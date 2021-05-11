/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.FindFields;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Replaces JUnit 4 MockitoJUnit rules with JUnit MockitoExtension.
 *
 * Supported MockitoJUnit methods:
 *  #rule()
 *  #testRule()
 *
 * Does not currently support @Incubating MockitoJUnit.collector().
 *
 * @implNote collector() is designed to aggregate multiple verifications into a single output.
 * Refactoring the method may be fairly complex and would likely benefit from being a separate recipe.
 *
 * Must be ran in the JUnit5 suite.
 */
public class MockitoJUnitToMockitoExtension extends Recipe {

    private static final String MOCKITO_TEST_RULE_FQN = "org.mockito.junit.MockitoTestRule";
    private static final String MOCKITO_RULE_FQN = "org.mockito.junit.MockitoRule";

    private static final ThreadLocal<JavaParser> JAVA_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion()
                    .dependsOn(Arrays.asList(
                            Parser.Input.fromString("package org.junit.jupiter.api.extension;\n" +
                                    "public @interface ExtendWith {\n" +
                                    "Class[] value();\n" +
                                    "}"),
                            Parser.Input.fromString("package org.mockito.junit.jupiter;\n" +
                                    "public class MockitoExtension {\n" +
                                    "}")
                    ))
                    .build()
    );

    @Override
    public String getDisplayName() {
        return "MockitoJUnit to MockitoExtension";
    }

    @Override
    public String getDescription() {
        return "Replaces MockitoJUnit rules with MockitoExtension.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>(MOCKITO_TEST_RULE_FQN));
                doAfterVisit(new UsesType<>(MOCKITO_RULE_FQN));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MockitoRuleToMockitoExtensionVisitor();
    }

    public static class MockitoRuleToMockitoExtensionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String EXTEND_WITH_FQN = "org.junit.jupiter.api.extension.ExtendWith";
        private static final String MOCKITO_EXTENSION_FQN = "org.mockito.junit.jupiter.MockitoExtension";

        private static final String MOCKITO_RULE_INVOCATION_KEY = "mockitoRuleInvocation";
        private static final String MOCKITO_TEST_RULE_INVOCATION_KEY = "mockitoTestRuleInvocation";

        private static final String EXTEND_WITH_MOCKITO_EXTENSION =
                "@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)";
        private static final String RUN_WITH_MOCKITO_JUNIT_RUNNER =
                "@org.junit.runner.RunWith(org.mockito.runners.MockitoJUnitRunner.class)";


        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<J.VariableDeclarations> mockitoFields = FindFields.find(cd, MOCKITO_RULE_FQN);
            mockitoFields.addAll(FindFields.find(cd, MOCKITO_TEST_RULE_FQN));

            if (!mockitoFields.isEmpty()) {
                List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                statements.removeAll(mockitoFields);
                cd = cd.withBody(cd.getBody().withStatements(statements));

                maybeRemoveImport(MOCKITO_RULE_FQN);
                maybeRemoveImport(MOCKITO_TEST_RULE_FQN);

                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.mockito.junit.MockitoJUnit");
                maybeRemoveImport("org.mockito.quality.Strictness");

                if (classDecl.getBody().getStatements().size() != cd.getBody().getStatements().size() &&
                        (FindAnnotations.find(classDecl.withBody(null), RUN_WITH_MOCKITO_JUNIT_RUNNER).isEmpty() &&
                                FindAnnotations.find(classDecl.withBody(null), EXTEND_WITH_MOCKITO_EXTENSION).isEmpty())) {


                    cd = cd.withTemplate(
                            template("@ExtendWith(MockitoExtension.class)")
                                    .javaParser(JAVA_PARSER.get())
                                    .imports(EXTEND_WITH_FQN, MOCKITO_EXTENSION_FQN)
                                    .build(),
                            cd.getCoordinates()
                                    .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                    );

                    maybeAddImport(EXTEND_WITH_FQN);
                    maybeAddImport(MOCKITO_EXTENSION_FQN);
                }
            }

            return cd;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getType() != null) {
                if (TypeUtils.isOfClassType(method.getType().getDeclaringType(), MOCKITO_RULE_FQN)) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, MOCKITO_RULE_INVOCATION_KEY, method);
                } else if (TypeUtils.isOfClassType(method.getType().getDeclaringType(), MOCKITO_TEST_RULE_FQN)) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, MOCKITO_TEST_RULE_INVOCATION_KEY, method);
                }
            }

            return method;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);

            final J.MethodInvocation mockitoRuleInvocation = getCursor().pollMessage(MOCKITO_RULE_INVOCATION_KEY);
            final J.MethodInvocation mockitoTestRuleInvocation = getCursor().pollMessage(MOCKITO_TEST_RULE_INVOCATION_KEY);

            if ((mockitoRuleInvocation != null || mockitoTestRuleInvocation != null) && m.getBody() != null) {
                final List<Statement> filteredStatements = m.getBody().getStatements().stream()
                        .filter(it -> !isTargetMethodInvocation(it))
                        .collect(Collectors.toList());

                m = m.withBody((J.Block) new AutoFormatVisitor<ExecutionContext>()
                        .visit(m.getBody().withStatements(filteredStatements), ctx, getCursor()));
            }

            return m;
        }

        private static boolean isTargetMethodInvocation(Statement statement) {
            if (!(statement instanceof J.MethodInvocation)) {
                return false;
            }
            final J.MethodInvocation m = (J.MethodInvocation) statement;
            if (m.getType() == null) {
                return false;
            }

            return TypeUtils.isOfClassType(m.getType().getDeclaringType(), MOCKITO_RULE_FQN) ||
                    TypeUtils.isOfClassType(m.getType().getDeclaringType(), MOCKITO_TEST_RULE_FQN);
        }

    }
}
