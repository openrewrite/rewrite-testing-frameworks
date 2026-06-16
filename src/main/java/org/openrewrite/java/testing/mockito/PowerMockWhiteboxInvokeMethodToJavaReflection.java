/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PowerMockWhiteboxInvokeMethodToJavaReflection extends Recipe {

    private static final MethodMatcher INVOKE_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Object, java.lang.String, ..)");
    private static final MethodMatcher INVOKE_METHOD_STATIC =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Class, java.lang.String, ..)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.invokeMethod()` with Java reflection";

    @Getter
    final String description = "Replace instance and static `Whitebox.invokeMethod(..)` calls with " +
            "`java.lang.reflect.Method` lookup and `invoke()`. Parameter types are taken from the unambiguously " +
            "resolved target method, falling back to each argument's compile-time class; calls passing an " +
            "explicit `Class[]` array are left unchanged for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new InvokeMethodVisitor().withPrecondition();
    }

    private static class InvokeMethodVisitor extends WhiteboxToReflectionVisitor {

        InvokeMethodVisitor() {
            super("java.lang.reflect.Method", INVOKE_METHOD, INVOKE_METHOD_STATIC);
        }

        @Override
        JavaType.@Nullable Method resolve(J.MethodInvocation mi) {
            if (INVOKE_METHOD.matches(mi)) {
                return resolveTargetMethod(mi.getArguments());
            }
            if (INVOKE_METHOD_STATIC.matches(mi)) {
                return resolveStaticTargetMethod(mi.getArguments());
            }
            return null;
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            if (hasArrayArg(args, 2)) {
                // Explicit `Class<?>[]` parameter-type overload (or an array passed as varargs) cannot be
                // mechanically expanded; leave for manual migration (flagged downstream).
                return null;
            }
            boolean isStatic = INVOKE_METHOD_STATIC.matches(mi);
            String varName = methodVarName(args.get(1), scope);
            // Static calls take a Class target; instance calls take an Object whose class we look up.
            String declaredMethodReceiver = isStatic ? "#{any(java.lang.Class)}" : "#{any(java.lang.Object)}.getClass()";
            String invokeTarget = isStatic ? "null" : "#{any(java.lang.Object)}";

            // getDeclaredMethod line
            StringBuilder sb = new StringBuilder();
            sb.append("Method ").append(varName).append(" = ").append(declaredMethodReceiver)
                    .append(".getDeclaredMethod(#{any(java.lang.String)}");
            for (int i = 2; i < args.size(); i++) {
                String classLiteral = getParamClassLiteral(args, i, resolvedMethod);
                if (classLiteral != null) {
                    sb.append(", ").append(classLiteral);
                } else {
                    sb.append(", #{any(java.lang.Object)}.getClass()");
                }
            }
            sb.append(");\n");

            // setAccessible line
            sb.append(varName).append(".setAccessible(true);\n");

            // invoke line
            if (sink.varName != null) {
                if (isNonObjectCast(sink.castType)) {
                    sb.append(sink.castType).append(" ").append(sink.varName).append(" = (").append(boxedCastType(sink.castType)).append(") ");
                } else {
                    sb.append("Object ").append(sink.varName).append(" = ");
                }
            }
            sb.append(varName).append(".invoke(").append(invokeTarget);
            for (int i = 2; i < args.size(); i++) {
                sb.append(", #{any(java.lang.Object)}");
            }
            sb.append(");");

            return sb.toString();
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            List<Object> result = new ArrayList<>();
            result.add(args.get(0)); // receiver for getDeclaredMethod (Class for static, Object for instance)
            result.add(args.get(1)); // methodName
            for (int i = 2; i < args.size(); i++) {
                if (getParamClassLiteral(args, i, resolvedMethod) == null) {
                    result.add(args.get(i)); // arg.getClass() fallback for getDeclaredMethod
                }
            }
            if (!INVOKE_METHOD_STATIC.matches(mi)) {
                result.add(args.get(0)); // target for invoke (static uses the null literal, no placeholder)
            }
            for (int i = 2; i < args.size(); i++) {
                result.add(args.get(i)); // arg for invoke
            }
            return result.toArray();
        }

        /**
         * Resolve the target method from the first argument's type and the method name.
         * Returns null if the method cannot be unambiguously resolved (not found, overloaded,
         * or missing type information).
         */
        private JavaType.@Nullable Method resolveTargetMethod(List<Expression> args) {
            if (args.size() <= 2) {
                return null;
            }
            JavaType targetType = args.get(0).getType();
            JavaType.FullyQualified fq = targetType instanceof JavaType.FullyQualified ? (JavaType.FullyQualified) targetType : null;
            return findUniqueMethod(fq, extractStringLiteral(args.get(1)), args.size() - 2);
        }

        /**
         * Resolve the static target method from the {@code Class} literal's element type and the method name.
         */
        private JavaType.@Nullable Method resolveStaticTargetMethod(List<Expression> args) {
            if (args.size() <= 2) {
                return null;
            }
            return findUniqueMethod(classLiteralElementType(args.get(0)), extractStringLiteral(args.get(1)), args.size() - 2);
        }

        private JavaType.@Nullable Method findUniqueMethod(JavaType.@Nullable FullyQualified targetType,
                                                           @Nullable String methodName, int expectedParamCount) {
            if (targetType == null || methodName == null) {
                return null;
            }
            JavaType.Method match = null;
            for (Iterator<JavaType.Method> it = targetType.getVisibleMethods(); it.hasNext(); ) {
                JavaType.Method method = it.next();
                if (method.getName().equals(methodName) &&
                        method.getParameterTypes().size() == expectedParamCount) {
                    if (match != null) {
                        return null; // ambiguous overload
                    }
                    match = method;
                }
            }
            return match;
        }
    }
}
