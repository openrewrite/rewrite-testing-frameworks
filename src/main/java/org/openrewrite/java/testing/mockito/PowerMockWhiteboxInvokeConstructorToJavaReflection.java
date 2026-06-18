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
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockWhiteboxInvokeConstructorToJavaReflection extends Recipe {

    private static final MethodMatcher INVOKE_CONSTRUCTOR_ARGS =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeConstructor(java.lang.Class, java.lang.Object[])");
    private static final MethodMatcher INVOKE_CONSTRUCTOR_EXPLICIT =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeConstructor(java.lang.Class, java.lang.Class[], java.lang.Object[])");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.invokeConstructor()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.invokeConstructor(..)` with `java.lang.reflect.Constructor` " +
            "lookup and `newInstance()` on the named class. Constructor parameter types are taken from the " +
            "unambiguously resolved constructor, falling back to each argument's compile-time class; arrays " +
            "passed to the `Object...` varargs overload are left unchanged for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new InvokeConstructorVisitor().withPrecondition();
    }

    private static class InvokeConstructorVisitor extends WhiteboxToReflectionVisitor {

        InvokeConstructorVisitor() {
            super("java.lang.reflect.Constructor", INVOKE_CONSTRUCTOR_ARGS, INVOKE_CONSTRUCTOR_EXPLICIT);
        }

        @Override
        JavaType.@Nullable Method resolve(J.MethodInvocation mi) {
            if (INVOKE_CONSTRUCTOR_ARGS.matches(mi)) {
                return resolveTargetConstructor(mi.getArguments());
            }
            return null;
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            boolean explicit = INVOKE_CONSTRUCTOR_EXPLICIT.matches(mi);
            if (!explicit && hasArrayArg(args, 1)) {
                // An array passed to the `Object...` varargs overload is ambiguous (spread vs single arg);
                // leave for manual migration (flagged downstream).
                return null;
            }
            JavaType.FullyQualified elem = classLiteralElementType(args.get(0));
            String genericType = elem != null ? elem.getClassName() : "?";
            String varName = constructorVarName(elem, scope);

            StringBuilder sb = new StringBuilder("Constructor<").append(genericType).append("> ").append(varName)
                    .append(" = #{any(java.lang.Class)}.getDeclaredConstructor(");

            int newInstanceArgCount;
            if (explicit) {
                List<Expression> paramTypeExprs = arrayElements(args.get(1));
                List<Expression> ctorArgExprs = arrayElements(args.get(2));
                if (paramTypeExprs == null || ctorArgExprs == null) {
                    // Cannot unwrap the explicit Class[]/Object[] arrays; leave for manual migration (flagged downstream)
                    return null;
                }
                for (int i = 0; i < paramTypeExprs.size(); i++) {
                    sb.append(i > 0 ? ", " : "").append("#{any(java.lang.Class)}");
                }
                newInstanceArgCount = ctorArgExprs.size();
            } else {
                for (int i = 1; i < args.size(); i++) {
                    sb.append(i > 1 ? ", " : "");
                    String classLiteral = getParamClassLiteral(args, i, resolvedMethod, 1);
                    sb.append(classLiteral != null ? classLiteral : "#{any(java.lang.Object)}.getClass()");
                }
                newInstanceArgCount = args.size() - 1;
            }
            sb.append(");\n");
            sb.append(varName).append(".setAccessible(true);\n");
            sb.append(constructorNewInstanceTail(varName, sink, elem != null, newInstanceArgCount));
            return sb.toString();
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            List<Object> result = new ArrayList<>();
            result.add(args.get(0)); // getDeclaredConstructor receiver (Class)
            if (INVOKE_CONSTRUCTOR_EXPLICIT.matches(mi)) {
                List<Expression> paramTypeExprs = arrayElements(args.get(1));
                List<Expression> ctorArgExprs = arrayElements(args.get(2));
                if (paramTypeExprs != null) {
                    result.addAll(paramTypeExprs); // one per #{any(java.lang.Class)}
                }
                if (ctorArgExprs != null) {
                    result.addAll(ctorArgExprs); // newInstance args
                }
            } else {
                for (int i = 1; i < args.size(); i++) {
                    if (getParamClassLiteral(args, i, resolvedMethod, 1) == null) {
                        result.add(args.get(i)); // arg.getClass() fallback receiver
                    }
                }
                for (int i = 1; i < args.size(); i++) {
                    result.add(args.get(i)); // newInstance args
                }
            }
            return result.toArray();
        }

        private String constructorNewInstanceTail(String varName, ResultSink sink, boolean elemKnown, int argCount) {
            String invokeArgs = repeatObjectPlaceholders(argCount);
            if (sink.varName != null) {
                String castType = sink.castType != null ? sink.castType : "Object";
                // When the Class element type is known, newInstance() returns it directly (no cast needed);
                // otherwise we have a raw Constructor<?> returning Object that must be cast.
                if (!elemKnown && isNonObjectCast(castType)) {
                    return castType + " " + sink.varName + " = (" + castType + ") " + varName + ".newInstance(" + invokeArgs + ");";
                }
                return castType + " " + sink.varName + " = " + varName + ".newInstance(" + invokeArgs + ");";
            }
            return varName + ".newInstance(" + invokeArgs + ");";
        }

        private String repeatObjectPlaceholders(int count) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append(i > 0 ? ", " : "").append("#{any(java.lang.Object)}");
            }
            return sb.toString();
        }

        private @Nullable List<Expression> arrayElements(Expression expr) {
            if (expr instanceof J.NewArray) {
                return ((J.NewArray) expr).getInitializer();
            }
            return null;
        }

        private String constructorVarName(JavaType.@Nullable FullyQualified elem, Cursor scope) {
            String base;
            if (elem != null) {
                String simple = elem.getClassName();
                int dot = simple.lastIndexOf('.');
                if (dot >= 0) {
                    simple = simple.substring(dot + 1);
                }
                base = Character.toLowerCase(simple.charAt(0)) + simple.substring(1) + "Constructor";
            } else {
                base = "reflectConstructor";
            }
            return generateVariableName(base, scope, INCREMENT_NUMBER);
        }

        /**
         * Resolve the target constructor from the {@code Class} literal's element type, by parameter count.
         * Returns null if not unambiguously resolvable (so parameter types fall back to {@code arg.getClass()}).
         */
        private JavaType.@Nullable Method resolveTargetConstructor(List<Expression> args) {
            if (args.size() <= 1) {
                return null;
            }
            JavaType.FullyQualified type = classLiteralElementType(args.get(0));
            if (type == null) {
                return null;
            }
            int expectedParamCount = args.size() - 1;
            JavaType.Method match = null;
            for (JavaType.Method method : type.getMethods()) {
                if (method.isConstructor() && method.getParameterTypes().size() == expectedParamCount) {
                    if (match != null) {
                        return null; // ambiguous overload
                    }
                    match = method;
                }
            }
            return match;
        }

        // Extract the element type X from a Class<X>-typed expression (e.g. MyService.class).
        private JavaType.@Nullable FullyQualified classLiteralElementType(Expression classExpr) {
            JavaType.Parameterized parameterized = TypeUtils.asParameterized(classExpr.getType());
            if (parameterized != null && !parameterized.getTypeParameters().isEmpty()) {
                return TypeUtils.asFullyQualified(parameterized.getTypeParameters().get(0));
            }
            return null;
        }

        // Class literal for the parameter at argIndex, where firstParamArgIndex is the argument index of the
        // first declared parameter (1 for invokeConstructor: class, params...). Prefers the resolved
        // constructor's declared parameter type, falls back to the argument's compile-time type.
        private @Nullable String getParamClassLiteral(List<Expression> args, int argIndex,
                                                      JavaType.@Nullable Method resolvedMethod, int firstParamArgIndex) {
            if (resolvedMethod != null) {
                int paramIdx = argIndex - firstParamArgIndex;
                List<JavaType> paramTypes = resolvedMethod.getParameterTypes();
                if (paramIdx >= 0 && paramIdx < paramTypes.size()) {
                    String literal = classLiteralFromType(paramTypes.get(paramIdx));
                    if (literal != null) {
                        return literal;
                    }
                }
            }
            return classLiteralFromType(args.get(argIndex).getType());
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
