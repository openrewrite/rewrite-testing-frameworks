/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Replaces JUnit 4 MockitoJUnit rules with JUnit MockitoExtension.
 * <p>
 * Supported MockitoJUnit methods:
 * #rule()
 * #testRule()
 * <p>
 * Does not currently support @Incubating MockitoJUnit.collector().
 *
 * @implNote collector() is designed to aggregate multiple verifications into a single output.
 * Refactoring the method may be fairly complex and would likely benefit from being a separate recipe.
 * <p>
 * Must be ran in the JUnit 5 suite.
 */
public class MockitoJUnitToMockitoExtension extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit 4 `MockitoJUnit` to JUnit Jupiter `MockitoExtension`";
    }

    @Override
    public String getDescription() {
        return "Replaces `MockitoJUnit` rules with `MockitoExtension`.";
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                doAfterVisit(new UsesType<>("org.mockito.junit.MockitoTestRule", false));
                doAfterVisit(new UsesType<>("org.mockito.junit.MockitoRule", false));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MockitoRuleToMockitoExtensionVisitor();
    }

    public static class MockitoRuleToMockitoExtensionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String MOCKITO_RULE_INVOCATION_KEY = "mockitoRuleInvocation";
        private static final String MOCKITO_TEST_RULE_INVOCATION_KEY = "mockitoTestRuleInvocation";

        private static final String EXTEND_WITH_MOCKITO_EXTENSION = "@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)";
        private static final String RUN_WITH_MOCKITO_JUNIT_RUNNER = "@org.junit.runner.RunWith(org.mockito.runners.MockitoJUnitRunner.class)";

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<J.VariableDeclarations> mockitoFields = FindFieldsOfType.find(cd, "org.mockito.junit.MockitoRule");
            mockitoFields.addAll(FindFieldsOfType.find(cd, "org.mockito.junit.MockitoTestRule"));

            if (!mockitoFields.isEmpty()) {
                List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                statements.removeAll(mockitoFields);
                cd = cd.withBody(cd.getBody().withStatements(statements));

                maybeRemoveImport("org.mockito.junit.MockitoRule");
                maybeRemoveImport("org.mockito.junit.MockitoTestRule");

                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.mockito.junit.MockitoJUnit");
                maybeRemoveImport("org.mockito.quality.Strictness");

                //noinspection DataFlowIssue
                if (classDecl.getBody().getStatements().size() != cd.getBody().getStatements().size() &&
                        (FindAnnotations.find(classDecl.withBody(null), RUN_WITH_MOCKITO_JUNIT_RUNNER).isEmpty() &&
                                FindAnnotations.find(classDecl.withBody(null), EXTEND_WITH_MOCKITO_EXTENSION).isEmpty())) {

                    cd = cd.withTemplate(
                            JavaTemplate.builder(this::getCursor, "@ExtendWith(MockitoExtension.class)")
                                    .javaParser(JavaParser.fromJavaVersion()
                                                    .classpathFromResources(ctx, "junit-jupiter-api-5.9.2", "mockito-junit-jupiter-3.12.4"))
                                    .imports("org.junit.jupiter.api.extension.ExtendWith", "org.mockito.junit.jupiter.MockitoExtension")
                                    .build(),
                            cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                    );

                    maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                    maybeAddImport("org.mockito.junit.jupiter.MockitoExtension");
                }
            }

            return cd;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getMethodType() != null) {
                if (TypeUtils.isOfClassType(method.getMethodType().getDeclaringType(), "org.mockito.junit.MockitoRule")) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, MOCKITO_RULE_INVOCATION_KEY, method);
                } else if (TypeUtils.isOfClassType(method.getMethodType().getDeclaringType(), "org.mockito.junit.MockitoTestRule")) {
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
            if (m.getMethodType() == null) {
                return false;
            }

            return TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.mockito.junit.MockitoRule") ||
                    TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.mockito.junit.MockitoTestRule");
        }

    }
}
