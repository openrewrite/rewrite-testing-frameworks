/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.HashSet;
import java.util.Set;

public class RemoveInitMocksIfRunnersSpecified extends Recipe {

    @Getter
    final String displayName = "Remove `MockitoAnnotations.initMocks(this)` and `openMocks(this)` if JUnit runners specified";

    @Getter
    final String description = "Remove `MockitoAnnotations.initMocks(this)` and `MockitoAnnotations.openMocks(this)` if class-level " +
            "JUnit runners `@RunWith(MockitoJUnitRunner.class)` or `@ExtendWith(MockitoExtension.class)` are specified. " +
            "These manual initialization calls are redundant when using Mockito's JUnit integration.";

    private static final String MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String MOCKITO_JUNIT_RUNNER = "org.mockito.junit.MockitoJUnitRunner";
    private static final AnnotationMatcher MOCKITO_EXTENSION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.extension.ExtendWith(" + MOCKITO_EXTENSION + ".class)");
    private static final AnnotationMatcher MOCKITO_JUNIT_MATCHER = new AnnotationMatcher("@org.junit.runner.RunWith(" + MOCKITO_JUNIT_RUNNER + ".class)");
    private static final MethodMatcher INIT_MOCKS_MATCHER = new MethodMatcher("org.mockito.MockitoAnnotations initMocks(..)", false);
    private static final MethodMatcher OPEN_MOCKS_MATCHER = new MethodMatcher("org.mockito.MockitoAnnotations openMocks(..)", false);
    private static final AnnotationMatcher AFTER_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
    private static final AnnotationMatcher AFTER_MATCHER = new AnnotationMatcher("@org.junit.After");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        Preconditions.or(
                                new UsesMethod<>(INIT_MOCKS_MATCHER),
                                new UsesMethod<>(OPEN_MOCKS_MATCHER)
                        ),
                        Preconditions.or(
                                new UsesType<>(MOCKITO_EXTENSION, false),
                                new UsesType<>(MOCKITO_JUNIT_RUNNER, false)
                        )
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    private final Set<String> fieldsToRemove = new HashSet<>();

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                        if (service(AnnotationService.class).matches(updateCursor(cd), MOCKITO_EXTENSION_MATCHER) ||
                                service(AnnotationService.class).matches(updateCursor(cd), MOCKITO_JUNIT_MATCHER)) {
                            fieldsToRemove.clear();
                            // First pass: find fields assigned from openMocks
                            for (Statement statement : cd.getBody().getStatements()) {
                                findFieldsAssignedFromMockitoInit(statement);
                            }
                            return super.visitClassDeclaration(cd, ctx);
                        }
                        return cd;
                    }

                    private void findFieldsAssignedFromMockitoInit(Statement statement) {
                        if (statement instanceof J.MethodDeclaration) {
                            J.MethodDeclaration md = (J.MethodDeclaration) statement;
                            if (md.getBody() != null) {
                                for (Statement s : md.getBody().getStatements()) {
                                    if (s instanceof J.Assignment) {
                                        J.Assignment assignment = (J.Assignment) s;
                                        if (isMockitoInitCall(assignment.getAssignment())) {
                                            Expression variable = assignment.getVariable();
                                            if (variable instanceof J.Identifier) {
                                                fieldsToRemove.add(((J.Identifier) variable).getSimpleName());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    private boolean isMockitoInitCall(Expression expr) {
                        if (expr instanceof J.MethodInvocation) {
                            J.MethodInvocation mi = (J.MethodInvocation) expr;
                            return INIT_MOCKS_MATCHER.matches(mi) || OPEN_MOCKS_MATCHER.matches(mi);
                        }
                        return false;
                    }

                    @Override
                    public J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        // Remove field declarations for fields that store openMocks result
                        for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                            if (fieldsToRemove.contains(variable.getSimpleName())) {
                                return null;
                            }
                        }
                        return vd;
                    }

                    @Override
                    public J.@Nullable Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                        J.Assignment a = super.visitAssignment(assignment, ctx);
                        // Remove assignments where RHS is initMocks/openMocks
                        if (isMockitoInitCall(assignment.getAssignment())) {
                            maybeRemoveImport("org.mockito.MockitoAnnotations");
                            return null;
                        }
                        return a;
                    }

                    @Override
                    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        // Remove direct initMocks/openMocks calls (not in assignment)
                        if (INIT_MOCKS_MATCHER.matches(mi) || OPEN_MOCKS_MATCHER.matches(mi)) {
                            maybeRemoveImport("org.mockito.MockitoAnnotations");
                            return null;
                        }
                        // Remove close() calls on fields that held openMocks result
                        if ("close".equals(mi.getSimpleName()) && mi.getSelect() instanceof J.Identifier) {
                            String selectName = ((J.Identifier) mi.getSelect()).getSimpleName();
                            if (fieldsToRemove.contains(selectName)) {
                                return null;
                            }
                        }
                        return mi;
                    }

                    @Override
                    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        if (md != method && md.getBody() != null && md.getBody().getStatements().isEmpty()) {
                            // Remove empty @BeforeEach/@Before methods
                            maybeRemoveImport("org.junit.jupiter.api.BeforeEach");
                            maybeRemoveImport("org.junit.Before");
                            // Remove empty @AfterEach/@After methods
                            if (service(AnnotationService.class).matches(getCursor(), AFTER_EACH_MATCHER)) {
                                maybeRemoveImport("org.junit.jupiter.api.AfterEach");
                                return null;
                            }
                            if (service(AnnotationService.class).matches(getCursor(), AFTER_MATCHER)) {
                                maybeRemoveImport("org.junit.After");
                                return null;
                            }
                            return null;
                        }
                        return md;
                    }
                }
        );
    }
}
