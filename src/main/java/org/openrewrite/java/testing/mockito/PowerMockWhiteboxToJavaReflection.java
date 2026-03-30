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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockWhiteboxToJavaReflection extends Recipe {

    private static final String WHITEBOX_FQN = "org.powermock.reflect.Whitebox";
    private static final MethodMatcher SET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object)");
    private static final MethodMatcher GET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox getInternalState(java.lang.Object, java.lang.String)");
    private static final MethodMatcher INVOKE_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Object, java.lang.String, ..)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox` with Java reflection";

    @Getter
    final String description = "Replace `org.powermock.reflect.Whitebox` calls " +
            "(`setInternalState`, `getInternalState`, `invokeMethod`) with plain Java reflection using " +
            "`java.lang.reflect.Field` and `java.lang.reflect.Method`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(WHITEBOX_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {

        private static final String WHITEBOX_REPLACED = "whiteboxReplaced";
        private static final String NEEDS_FIELD_IMPORT = "needsFieldImport";
        private static final String NEEDS_METHOD_IMPORT = "needsMethodImport";

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (getCursor().getMessage(WHITEBOX_REPLACED, false)) {
                md = addThrowsExceptionIfAbsent(md);
                maybeRemoveImport(WHITEBOX_FQN);
                if (getCursor().getMessage(NEEDS_FIELD_IMPORT, false)) {
                    maybeAddImport("java.lang.reflect.Field", false);
                }
                if (getCursor().getMessage(NEEDS_METHOD_IMPORT, false)) {
                    maybeAddImport("java.lang.reflect.Method", false);
                }
                return maybeAutoFormat(method, md, ctx);
            }
            return md;
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);

            List<Statement> statements = b.getStatements();
            // Process in reverse so that coordinate positions remain valid after each replacement
            for (int i = statements.size() - 1; i >= 0; i--) {
                Statement stmt = statements.get(i);
                J.MethodInvocation mi = extractWhiteboxInvocation(stmt);
                if (mi == null) {
                    continue;
                }
                Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                JavaType.Method resolvedMethod = INVOKE_METHOD.matches(mi) ?
                        resolveTargetMethod(mi.getArguments()) : null;
                String template = buildReplacementTemplate(stmt, mi, blockCursor, resolvedMethod);
                if (template != null) {
                    Object[] templateArgs = buildTemplateArgs(mi, resolvedMethod);

                    List<String> templateImports = new ArrayList<>();
                    templateImports.add("java.lang.reflect.Field");
                    templateImports.add("java.lang.reflect.Method");
                    if (resolvedMethod != null) {
                        for (JavaType paramType : resolvedMethod.getParameterTypes()) {
                            if (paramType instanceof JavaType.FullyQualified) {
                                JavaType.FullyQualified fq = (JavaType.FullyQualified) paramType;
                                if (!"java.lang".equals(fq.getPackageName())) {
                                    templateImports.add(fq.getFullyQualifiedName());
                                    maybeAddImport(fq.getFullyQualifiedName());
                                }
                            }
                        }
                    }

                    b = JavaTemplate.builder(template)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion())
                            .imports(templateImports.toArray(new String[0]))
                            .build()
                            .apply(
                                    new Cursor(getCursor().getParentOrThrow(), b),
                                    stmt.getCoordinates().replace(),
                                    templateArgs
                            );
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, WHITEBOX_REPLACED, true);
                    if (SET_INTERNAL_STATE.matches(mi) || GET_INTERNAL_STATE.matches(mi)) {
                        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_FIELD_IMPORT, true);
                    }
                    if (INVOKE_METHOD.matches(mi)) {
                        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_METHOD_IMPORT, true);
                    }
                    // Re-read statements list since the block has been rebuilt
                    statements = b.getStatements();
                }
            }

            return b;
        }

        private @Nullable String buildReplacementTemplate(Statement statement, J.MethodInvocation mi,
                                                          Cursor scope, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();

            if (SET_INTERNAL_STATE.matches(mi) && args.size() == 3) {
                return buildSetInternalStateTemplate(args, scope);
            }
            if (GET_INTERNAL_STATE.matches(mi) && args.size() == 2) {
                return buildGetInternalStateTemplate(args, statement, scope);
            }
            if (INVOKE_METHOD.matches(mi) && args.size() >= 2) {
                return buildInvokeMethodTemplate(args, statement, scope, resolvedMethod);
            }
            return null;
        }

        private Object[] buildTemplateArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();

            if (SET_INTERNAL_STATE.matches(mi) && args.size() == 3) {
                // target, fieldName, target, value
                return new Object[]{args.get(0), args.get(1), args.get(0), args.get(2)};
            }
            if (GET_INTERNAL_STATE.matches(mi) && args.size() == 2) {
                // target, fieldName, target
                return new Object[]{args.get(0), args.get(1), args.get(0)};
            }
            if (INVOKE_METHOD.matches(mi) && args.size() >= 2) {
                return buildInvokeMethodArgs(args, resolvedMethod);
            }
            return new Object[0];
        }

        private @Nullable String buildSetInternalStateTemplate(List<Expression> args, Cursor scope) {
            String fieldName = extractStringLiteral(args.get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            return "Field " + varName + " = #{any(java.lang.Object)}.getClass().getDeclaredField(#{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);\n" +
                    varName + ".set(#{any(java.lang.Object)}, #{any(java.lang.Object)});";
        }

        private @Nullable String buildGetInternalStateTemplate(List<Expression> args, Statement statement, Cursor scope) {
            String fieldName = extractStringLiteral(args.get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            String prefix = "Field " + varName + " = #{any(java.lang.Object)}.getClass().getDeclaredField(#{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);\n";

            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                String assignToVar = varDecls.getVariables().get(0).getSimpleName();
                String castType = getCastType(varDecls.getType());
                if (castType != null && !"Object".equals(castType) && !"java.lang.Object".equals(castType)) {
                    return prefix + castType + " " + assignToVar + " = (" + castType + ") " + varName + ".get(#{any(java.lang.Object)});";
                }
                return prefix + "Object " + assignToVar + " = " + varName + ".get(#{any(java.lang.Object)});";
            }
            return prefix + varName + ".get(#{any(java.lang.Object)});";
        }

        private @Nullable String buildInvokeMethodTemplate(List<Expression> args, Statement statement,
                                                           Cursor scope, JavaType.@Nullable Method resolvedMethod) {
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
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                String assignToVar = varDecls.getVariables().get(0).getSimpleName();
                String castType = getCastType(varDecls.getType());
                if (castType != null && !"Object".equals(castType) && !"java.lang.Object".equals(castType)) {
                    sb.append(castType).append(" ").append(assignToVar).append(" = (").append(castType).append(") ");
                } else {
                    sb.append("Object ").append(assignToVar).append(" = ");
                }
            }
            sb.append(varName).append(".invoke(#{any(java.lang.Object)}");
            for (int i = 2; i < args.size(); i++) {
                sb.append(", #{any(java.lang.Object)}");
            }
            sb.append(");");

            return sb.toString();
        }

        private Object[] buildInvokeMethodArgs(List<Expression> args, JavaType.@Nullable Method resolvedMethod) {
            int extraArgs = args.size() - 2;
            int unresolvedCount = 0;
            for (int i = 2; i < args.size(); i++) {
                if (getParamClassLiteral(args, i, resolvedMethod) == null) {
                    unresolvedCount++;
                }
            }
            Object[] result = new Object[2 + unresolvedCount + 1 + extraArgs];
            int idx = 0;
            result[idx++] = args.get(0); // target for getDeclaredMethod
            result[idx++] = args.get(1); // methodName
            for (int i = 2; i < args.size(); i++) {
                if (getParamClassLiteral(args, i, resolvedMethod) == null) {
                    result[idx++] = args.get(i); // arg.getClass() fallback for getDeclaredMethod
                }
            }
            result[idx++] = args.get(0); // target for invoke
            for (int i = 2; i < args.size(); i++) {
                result[idx++] = args.get(i); // arg for invoke
            }
            return result;
        }

        /**
         * Get the class literal for a parameter at the given argument index.
         * Prefers the resolved method's declared parameter type, falls back to the argument's
         * compile-time type, and returns null if neither is available.
         */
        private @Nullable String getParamClassLiteral(List<Expression> args, int argIndex,
                                                      JavaType.@Nullable Method resolvedMethod) {
            if (resolvedMethod != null) {
                String literal = classLiteralFromType(resolvedMethod.getParameterTypes().get(argIndex - 2));
                if (literal != null) {
                    return literal;
                }
            }
            return getClassLiteral(args.get(argIndex));
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

        private J.@Nullable MethodInvocation extractWhiteboxInvocation(Statement statement) {
            if (statement instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) statement;
                if (SET_INTERNAL_STATE.matches(mi) || GET_INTERNAL_STATE.matches(mi) || INVOKE_METHOD.matches(mi)) {
                    return mi;
                }
            }
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                if (varDecls.getVariables().size() == 1) {
                    Expression init = varDecls.getVariables().get(0).getInitializer();
                    if (init instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) init;
                        if (GET_INTERNAL_STATE.matches(mi) || INVOKE_METHOD.matches(mi)) {
                            return mi;
                        }
                    }
                }
            }
            return null;
        }

        private @Nullable String extractStringLiteral(Expression expr) {
            if (expr instanceof J.Literal && ((J.Literal) expr).getValue() instanceof String) {
                return (String) ((J.Literal) expr).getValue();
            }
            return null;
        }

        private @Nullable String getClassLiteral(Expression expr) {
            return classLiteralFromType(expr.getType());
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

        private @Nullable String getCastType(@Nullable JavaType type) {
            if (type instanceof JavaType.FullyQualified) {
                return ((JavaType.FullyQualified) type).getClassName();
            }
            if (type instanceof JavaType.Primitive) {
                return ((JavaType.Primitive) type).getKeyword();
            }
            return null;
        }

        private J.MethodDeclaration addThrowsExceptionIfAbsent(J.MethodDeclaration md) {
            if (md.getThrows() != null && md.getThrows().stream()
                    .anyMatch(j -> TypeUtils.isOfClassType(j.getType(), "java.lang.Exception") ||
                            TypeUtils.isOfClassType(j.getType(), "java.lang.Throwable"))) {
                return md;
            }
            JavaType.Class exceptionType = JavaType.ShallowClass.build("java.lang.Exception");
            return md.withThrows(ListUtils.concat(md.getThrows(),
                    new J.Identifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, emptyList(),
                            exceptionType.getClassName(), exceptionType, null)));
        }
        });
    }
}
