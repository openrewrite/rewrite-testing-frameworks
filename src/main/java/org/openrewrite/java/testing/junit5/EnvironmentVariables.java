/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import static java.util.Comparator.comparing;
import static org.openrewrite.java.testing.junit5.Junit4Utils.*;

/**
 * A recipe to replace JUnit 4's EnvironmentVariables rule from contrib with the JUnit 5-compatible
 * `SystemStubsExtension` and `EnvironmentVariables` from the System Stubs library.
 */
public class EnvironmentVariables extends Recipe {
    public static final String ENVIRONMENT_VARIABLES =
            "org.junit.contrib.java.lang.system.EnvironmentVariables";
    public static final String ENVIRONMENT_VARIABLES_STUB =
            "uk.org.webcompere.systemstubs.environment.EnvironmentVariables";
    public static final String SYSTEM_STUBS_EXTENSION =
            "uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension";
    public static final String SYSTEM_STUB = "uk.org.webcompere.systemstubs.jupiter.SystemStub";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";

    @Override
    public @NlsRewrite.DisplayName @NonNull String getDisplayName() {
        return "Migrate JUnit 4 environmentVariables rule to JUnit 5 system stubs extension";
    }

    @Override
    public @NlsRewrite.Description @NonNull String getDescription() {
        return "Replaces usage of the JUnit 4 `@Rule EnvironmentVariables` with the JUnit 5-compatible "
                + "`SystemStubsExtension` and `@SystemStub EnvironmentVariables` from the System Stubs "
                + "library.";
    }

    public @NonNull TreeVisitor<?, ExecutionContext> getVisitor() {
        return new EnvironmentVariablesVisitor();
    }

    private static class EnvironmentVariablesVisitor extends JavaVisitor<ExecutionContext> {

        private static final String HAS_ENV_VAR_RULE = "hasEnvVarRule";
        private static final String REPLACE_TEST_RULE_ANNOTATION = "replaceTestRuleAnnotation";
        private static final MethodMatcher ENV_VAR_CLEAR =
                new MethodMatcher(ENVIRONMENT_VARIABLES + " clear(String[])");

        @Override
        public @NonNull J visitCompilationUnit(
                J.@NonNull CompilationUnit cu, @NonNull ExecutionContext ctx) {
            maybeRemoveImport(RULE);
            maybeRemoveImport(CLASS_RULE);
            maybeRemoveImport(ENVIRONMENT_VARIABLES);
            maybeAddImport(SYSTEM_STUBS_EXTENSION);
            maybeAddImport(SYSTEM_STUB);
            maybeAddImport(EXTEND_WITH);
            maybeAddImport(ENVIRONMENT_VARIABLES_STUB);
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public @NonNull J visitClassDeclaration(
                J.@NonNull ClassDeclaration classDecl, @NonNull ExecutionContext ctx) {
            J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
            Boolean hasEnvVarRule = getCursor().getMessage(HAS_ENV_VAR_RULE);

            if (!Boolean.TRUE.equals(hasEnvVarRule)) {
                return cd;
            }
            // Add @ExtendWith(SystemStubsExtension.class) annotation to class.
            return systemStubExtensionTemplate(ctx).apply(
                    updateCursor(cd),
                    cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
        }

        @Override
        public @NonNull J visitVariableDeclarations(
                J.@NonNull VariableDeclarations variableDecls, @NonNull ExecutionContext ctx) {
            // missing type attribution, possibly parsing error.
            if (variableDecls.getType() == null) {
                return variableDecls;
            }
            boolean isEnvVarRule = isEnvVarRule(variableDecls);

            if (isEnvVarRule) {
                // put message to first enclosing ClassDeclaration, to inform that we have an env var rule.
                getCursor()
                        .dropParentUntil(c -> c instanceof J.ClassDeclaration)
                        .putMessage(HAS_ENV_VAR_RULE, true);
                // put message to variable, to inform that we need to replace the test rule annotation.
                getCursor().putMessage(REPLACE_TEST_RULE_ANNOTATION, true);
            }

            return super.visitVariableDeclarations(variableDecls, ctx);
        }

        @Override
        public @NonNull J visitAnnotation(
                J.@NonNull Annotation annotation, @NonNull ExecutionContext ctx) {
            if (!shouldReplace(annotation)) {
                return super.visitAnnotation(annotation, ctx);
            }
            return systemStubsTemplate(ctx).apply(getCursor(), annotation.getCoordinates().replace());
        }

        @SuppressWarnings("NullAway") // may need to delete a method
        @Override
        public @Nullable J visitMethodInvocation(
                J.@NonNull MethodInvocation method, @NonNull ExecutionContext ctx) {

            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            // Replace EnvironmentVariables.clear() with EnvironmentVariables.remove()
            if (ENV_VAR_CLEAR.matches(method) && m.getSelect() != null /* NullAway */) {
                int argCount = argCount(m);
                J j =
                        getEnvVarClearTemplate(ctx, argCount)
                                .apply(updateCursor(m), m.getCoordinates().replace(), getArgs(m, argCount));

                if (getCursor().getParentTreeCursor().getValue() instanceof J.Block
                        && !(j instanceof Statement)) {
                    return null;
                }
                return j;
            }
            return m;
        }

        @Override
        public @Nullable JavaType visitType(@Nullable JavaType type, @NonNull ExecutionContext ctx) {
            if (type instanceof JavaType.FullyQualified) {
                String fullyQualifiedName = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                if (fullyQualifiedName.equals(ENVIRONMENT_VARIABLES)) {
                    return JavaType.buildType(ENVIRONMENT_VARIABLES_STUB);
                }
            }
            return super.visitType(type, ctx);
        }

        private boolean shouldReplace(J.Annotation annotation) {
            if (annotation.getType() == null) {
                return false;
            }
            String type = ((JavaType.FullyQualified) annotation.getType()).getFullyQualifiedName();
            if (!type.equals(RULE) && !type.equals(CLASS_RULE)) { // not a test rule annotation
                return false;
            }
            if (getCursor().getParentTreeCursor().getValue() instanceof J.VariableDeclarations) {
                return Boolean.TRUE.equals(
                        getCursor().getParentTreeCursor().getMessage(REPLACE_TEST_RULE_ANNOTATION));
            }
            return false;
        }

        private static JavaTemplate systemStubExtensionTemplate(ExecutionContext ctx) {
            return JavaTemplate.builder("@ExtendWith(SystemStubsExtension.class)")
                    .imports(EXTEND_WITH, SYSTEM_STUBS_EXTENSION)
                    .javaParser(
                            JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-jupiter", "junit-jupiter-api"))
                    .build();
        }

        private static JavaTemplate systemStubsTemplate(ExecutionContext ctx) {
            return JavaTemplate.builder("@SystemStub")
                    .imports(SYSTEM_STUB)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-jupiter"))
                    .build();
        }

        private static boolean isEnvVarRule(J.VariableDeclarations variableDeclarations) {
            if (!isRule(variableDeclarations)) {
                return false;
            }
            JavaType.FullyQualified type = (JavaType.FullyQualified) variableDeclarations.getType();
            return type != null && type.getFullyQualifiedName().equals(ENVIRONMENT_VARIABLES);
        }

        private static JavaTemplate getEnvVarClearTemplate(ExecutionContext ctx, int argsSize) {
            StringBuilder template = new StringBuilder("#{any(").append(ENVIRONMENT_VARIABLES_STUB).append(")}");
            for (int i = 0; i < argsSize; i++) {
                template.append(".remove(#{any(java.lang.String)})");
            }
            return JavaTemplate.builder(template.toString())
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-jupiter", "system-stubs-core"))
                    .build();
        }

        private static int argCount(J.MethodInvocation methodInvocation) {
            if (methodInvocation.getArguments().size() == 1) {
                // method call with empty args contains an element of type J.Empty in LST.
                return methodInvocation.getArguments().get(0) instanceof J.Empty ? 0 : 1;
            }
            return methodInvocation.getArguments().size();
        }

        private static Expression[] getArgs(J.MethodInvocation methodInvocation, int argCount) {
            Expression[] args = new Expression[argCount + 1];
            args[0] = methodInvocation.getSelect();
            for (int i = 0; i < argCount; i++) {
                args[i + 1] = methodInvocation.getArguments().get(i);
            }
            return args;
        }
    }
}
