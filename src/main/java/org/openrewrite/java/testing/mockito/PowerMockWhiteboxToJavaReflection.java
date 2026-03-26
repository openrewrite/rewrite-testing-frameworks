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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

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
                new WhiteboxVisitor()
        );
    }

    private static class WhiteboxVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String WHITEBOX_REPLACED = "whiteboxReplaced";
        private static final String NEEDS_FIELD_IMPORT = "needsFieldImport";
        private static final String NEEDS_METHOD_IMPORT = "needsMethodImport";
        private static final JavaParser.Builder<?, ?> JAVA_PARSER = JavaParser.fromJavaVersion();

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
                md = maybeAutoFormat(method, md, ctx);
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
                String template = buildReplacementTemplate(stmt, mi, blockCursor);
                if (template != null) {
                    Object[] templateArgs = buildTemplateArgs(mi);
                    b = JavaTemplate.builder(template)
                            .contextSensitive()
                            .javaParser(JAVA_PARSER)
                            .imports("java.lang.reflect.Field", "java.lang.reflect.Method")
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

        private @Nullable String buildReplacementTemplate(Statement statement, J.MethodInvocation mi, Cursor scope) {
            List<Expression> args = mi.getArguments();

            if (SET_INTERNAL_STATE.matches(mi) && args.size() == 3) {
                return buildSetInternalStateTemplate(args, scope);
            }
            if (GET_INTERNAL_STATE.matches(mi) && args.size() == 2) {
                return buildGetInternalStateTemplate(args, statement, scope);
            }
            if (INVOKE_METHOD.matches(mi) && args.size() >= 2) {
                return buildInvokeMethodTemplate(args, statement, scope);
            }
            return null;
        }

        private Object[] buildTemplateArgs(J.MethodInvocation mi) {
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
                return buildInvokeMethodArgs(args);
            }
            return new Object[0];
        }

        private @Nullable String buildSetInternalStateTemplate(List<Expression> args, Cursor scope) {
            String fieldName = extractStringLiteral(args.get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            return "Field " + varName + " = #{any()}.getClass().getDeclaredField(#{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);\n" +
                    varName + ".set(#{any()}, #{any()});";
        }

        private @Nullable String buildGetInternalStateTemplate(List<Expression> args, Statement statement, Cursor scope) {
            String fieldName = extractStringLiteral(args.get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            String prefix = "Field " + varName + " = #{any()}.getClass().getDeclaredField(#{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);\n";

            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                String assignToVar = varDecls.getVariables().get(0).getSimpleName();
                String castType = getCastType(varDecls.getType());
                if (castType != null && !"Object".equals(castType) && !"java.lang.Object".equals(castType)) {
                    return prefix + castType + " " + assignToVar + " = (" + castType + ") " + varName + ".get(#{any()});";
                }
                return prefix + "Object " + assignToVar + " = " + varName + ".get(#{any()});";
            }
            return prefix + varName + ".get(#{any()});";
        }

        private @Nullable String buildInvokeMethodTemplate(List<Expression> args, Statement statement, Cursor scope) {
            String methodName = extractStringLiteral(args.get(1));
            if (methodName == null) {
                return null;
            }
            String varName = generateVariableName(methodName + "Method", scope, INCREMENT_NUMBER);

            // getDeclaredMethod line
            StringBuilder sb = new StringBuilder();
            sb.append("Method ").append(varName).append(" = #{any()}.getClass().getDeclaredMethod(#{any(java.lang.String)}");
            for (int i = 2; i < args.size(); i++) {
                sb.append(", #{any()}.getClass()");
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
            sb.append(varName).append(".invoke(#{any()}");
            for (int i = 2; i < args.size(); i++) {
                sb.append(", #{any()}");
            }
            sb.append(");");

            return sb.toString();
        }

        private Object[] buildInvokeMethodArgs(List<Expression> args) {
            int extraArgs = args.size() - 2;
            Object[] result = new Object[2 + extraArgs + 1 + extraArgs];
            int idx = 0;
            result[idx++] = args.get(0); // target for getDeclaredMethod
            result[idx++] = args.get(1); // methodName
            for (int i = 2; i < args.size(); i++) {
                result[idx++] = args.get(i); // arg.getClass() for getDeclaredMethod
            }
            result[idx++] = args.get(0); // target for invoke
            for (int i = 2; i < args.size(); i++) {
                result[idx++] = args.get(i); // arg for invoke
            }
            return result;
        }

        private static J.@Nullable MethodInvocation extractWhiteboxInvocation(Statement statement) {
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

        private static @Nullable String extractStringLiteral(Expression expr) {
            if (expr instanceof J.Literal && ((J.Literal) expr).getValue() instanceof String) {
                return (String) ((J.Literal) expr).getValue();
            }
            return null;
        }

        private static @Nullable String getCastType(@Nullable JavaType type) {
            if (type instanceof JavaType.FullyQualified) {
                return ((JavaType.FullyQualified) type).getClassName();
            }
            if (type instanceof JavaType.Primitive) {
                return ((JavaType.Primitive) type).getKeyword();
            }
            return null;
        }

        private static J.MethodDeclaration addThrowsExceptionIfAbsent(J.MethodDeclaration md) {
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
    }
}
