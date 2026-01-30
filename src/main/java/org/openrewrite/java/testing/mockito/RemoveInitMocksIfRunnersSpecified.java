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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
    private static final MethodMatcher CLOSEABLE_MATCHER = new MethodMatcher("java.lang.AutoCloseable close()", false);
    private static List<AnnotationMatcher> BEFORE_AND_AFTER_MATCHERS = Arrays.asList(
            new AnnotationMatcher("@org.junit.jupiter.api.BeforeAll"),
            new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach"),
            new AnnotationMatcher("@org.junit.BeforeClass"),
            new AnnotationMatcher("@org.junit.Before"),
            new AnnotationMatcher("@org.junit.jupiter.api.AfterAll"),
            new AnnotationMatcher("@org.junit.jupiter.api.AfterEach"),
            new AnnotationMatcher("@org.junit.AfterClass"),
            new AnnotationMatcher("@org.junit.After")
    );

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
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        Set<Expression> closeables = new JavaIsoVisitor<Set<Expression>>() {
                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, Set<Expression> exprSet) {
                                J.Assignment as = super.visitAssignment(assignment, exprSet);

                                if (isMockitoOpenMocksCall(assignment.getAssignment())) {
                                    exprSet.add(assignment.getVariable());
                                }
                                return as;
                            }
                        }.reduce(cd, new HashSet<>());

                        J.ClassDeclaration modifiedCd = (J.ClassDeclaration) new JavaIsoVisitor<ExecutionContext>() {

                            @Override
                            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                                if (service(AnnotationService.class).matches(updateCursor(classDecl), MOCKITO_EXTENSION_MATCHER) ||
                                        service(AnnotationService.class).matches(updateCursor(classDecl), MOCKITO_JUNIT_MATCHER)) {
                                    return super.visitClassDeclaration(classDecl, ctx);
                                }
                                return classDecl;
                            }

                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                                J.Assignment a = super.visitAssignment(assignment, ctx);
                                // Remove assignments where RHS is initMocks/openMocks
                                if (isMockitoInitMocksCall(assignment.getAssignment()) || isMockitoOpenMocksCall(assignment.getAssignment())) {
                                    maybeRemoveImport("org.mockito.MockitoAnnotations");
                                    return null;
                                }
                                return a;
                            }

                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                                if (OPEN_MOCKS_MATCHER.matches(mi) || INIT_MOCKS_MATCHER.matches(mi)) {
                                    return null;
                                }
                                if (CLOSEABLE_MATCHER.matches(mi) && mi.getSelect() != null && closeables.stream().anyMatch(it -> SemanticallyEqual.areEqual(it, mi.getSelect()))) {
                                    return null;
                                }
                                return mi;
                            }

                            @Override
                            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                                // Remove field declarations for fields that store openMocks result
                                for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                                    if (closeables.stream().anyMatch(it -> SemanticallyEqual.areEqual(it, variable.getDeclarator()))) {
                                        return null;
                                    }
                                }
                                return vd;
                            }

                            @Override
                            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                                if (md != method && md.getBody() != null && md.getBody().getStatements().isEmpty()) {
                                    // Only remove empty Before and After methods
                                    if (BEFORE_AND_AFTER_MATCHERS.stream().anyMatch(matcher ->
                                            service(AnnotationService.class).matches(getCursor(), matcher))) {
                                        return null;
                                    }
                                }
                                return md;
                            }

                        }.visitNonNull(cd, ctx, getCursor().getParentOrThrow());

                        maybeRemoveImport("org.mockito.MockitoAnnotations");
                        maybeRemoveImport("org.junit.jupiter.api.BeforeAll");
                        maybeRemoveImport("org.junit.jupiter.api.BeforeEach");
                        maybeRemoveImport("org.junit.jupiter.api.AfterAll");
                        maybeRemoveImport("org.junit.jupiter.api.AfterEach");
                        maybeRemoveImport("org.junit.BeforeClass");
                        maybeRemoveImport("org.junit.Before");
                        maybeRemoveImport("org.junit.AfterClass");
                        maybeRemoveImport("org.junit.After");

                        return modifiedCd;
                    }

                    private boolean isMockitoOpenMocksCall(Expression expr) {
                        return expr instanceof J.MethodInvocation && OPEN_MOCKS_MATCHER.matches((J.MethodInvocation)expr);
                    }

                    private boolean isMockitoInitMocksCall(Expression expr) {
                        return expr instanceof J.MethodInvocation && INIT_MOCKS_MATCHER.matches((J.MethodInvocation)expr);
                    }
                }
        );
    }
}
