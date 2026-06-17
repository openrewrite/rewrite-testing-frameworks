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
import java.util.List;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockWhiteboxInvokeMethodToJavaReflection extends Recipe {

    private static final MethodMatcher INVOKE_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Object, java.lang.String, ..)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.invokeMethod()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.invokeMethod(Object, String, ..)` with `java.lang.reflect.Method` " +
            "lookup and `invoke()`. Parameter types are taken from the unambiguously resolved target method, " +
            "falling back to each argument's compile-time class.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new InvokeMethodVisitor().withPrecondition();
    }

    private static class InvokeMethodVisitor extends WhiteboxToReflectionVisitor {

        InvokeMethodVisitor() {
            super("java.lang.reflect.Method", INVOKE_METHOD);
        }

        @Override
        JavaType.@Nullable Method resolve(J.MethodInvocation mi) {
            return resolveTargetMethod(mi.getArguments());
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            String methodName = extractStringLiteral(args.get(1));
            if (methodName == null) {
                return null;
            }
            String varName = generateVariableName(methodName + "Method", scope, INCREMENT_NUMBER);

            // getDeclaredMethod line
            StringBuilder sb = new StringBuilder();
            sb.append("Method ").append(varName).append(" = #{any(java.lang.Object)}.getClass().getDeclaredMethod(#{any(java.lang.String)}");
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
            sb.append(varName).append(".invoke(#{any(java.lang.Object)}");
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
            result.add(args.get(0)); // target for getDeclaredMethod
            result.add(args.get(1)); // methodName
            for (int i = 2; i < args.size(); i++) {
                if (getParamClassLiteral(args, i, resolvedMethod) == null) {
                    result.add(args.get(i)); // arg.getClass() fallback for getDeclaredMethod
                }
            }
            result.add(args.get(0)); // target for invoke
            for (int i = 2; i < args.size(); i++) {
                result.add(args.get(i)); // arg for invoke
            }
            return result.toArray();
        }

        /**
         * Get the class literal for a parameter at the given argument index. Prefers the resolved
         * method's declared parameter type, falls back to the argument's compile-time type, and
         * returns null if neither is available.
         */
        private @Nullable String getParamClassLiteral(List<Expression> args, int argIndex,
                                                      JavaType.@Nullable Method resolvedMethod) {
            if (resolvedMethod != null) {
                String literal = classLiteralFromType(resolvedMethod.getParameterTypes().get(argIndex - 2));
                if (literal != null) {
                    return literal;
                }
            }
            return classLiteralFromType(args.get(argIndex).getType());
        }

        /**
         * Resolve the target method from the first argument's type and the method name, walking the
         * supertype chain. Returns null if the method cannot be unambiguously resolved (not found,
         * overloaded, or missing type information).
         */
        private JavaType.@Nullable Method resolveTargetMethod(List<Expression> args) {
            if (args.size() <= 2) {
                return null;
            }
            String methodName = extractStringLiteral(args.get(1));
            if (methodName == null) {
                return null;
            }
            JavaType targetType = args.get(0).getType();
            if (!(targetType instanceof JavaType.FullyQualified)) {
                return null;
            }
            int expectedParamCount = args.size() - 2;
            JavaType.Method match = null;
            for (JavaType.FullyQualified current = (JavaType.FullyQualified) targetType;
                 current != null; current = current.getSupertype()) {
                for (JavaType.Method method : current.getMethods()) {
                    if (method.getName().equals(methodName) &&
                            method.getParameterTypes().size() == expectedParamCount) {
                        if (match != null) {
                            return null; // ambiguous overload
                        }
                        match = method;
                    }
                }
            }
            return match;
        }

        private @Nullable String classLiteralFromType(@Nullable JavaType type) {
            if (type instanceof JavaType.Primitive) {
                return ((JavaType.Primitive) type).getKeyword() + ".class";
            }
            if (type instanceof JavaType.FullyQualified) {
                return ((JavaType.FullyQualified) type).getClassName() + ".class";
            }
            return null;
        }
    }
}
