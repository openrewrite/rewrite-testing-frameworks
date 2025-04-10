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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;

public class RemoveInitMocksIfRunnersSpecified extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `MockitoAnnotations.initMocks(this)` if specified JUnit runners";
    }

    @Override
    public String getDescription() {
        return "Remove `MockitoAnnotations.initMocks(this)` if specified class-level JUnit runners `@RunWith(MockitoJUnitRunner.class)` or `@ExtendWith(MockitoExtension.class)`.";
    }

    private static final String MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String MOCKITO_JUNIT_RUNNER = "org.mockito.junit.MockitoJUnitRunner";
    private static final AnnotationMatcher MOCKITO_EXTENSION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.extension.ExtendWith(" + MOCKITO_EXTENSION + ".class)");
    private static final AnnotationMatcher MOCKITO_JUNIT_MATCHER = new AnnotationMatcher("@org.junit.runner.RunWith(" + MOCKITO_JUNIT_RUNNER + ".class)");
    private static final MethodMatcher INIT_MOCKS_MATCHER = new MethodMatcher("org.mockito.MockitoAnnotations initMocks(..)", false);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(INIT_MOCKS_MATCHER),
                        Preconditions.or(
                                new UsesType<>(MOCKITO_EXTENSION, false),
                                new UsesType<>(MOCKITO_JUNIT_RUNNER, false)
                        )
                ),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (INIT_MOCKS_MATCHER.matches(mi)) {
                            maybeRemoveImport("org.mockito.MockitoAnnotations");
                            getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "initMocks", "removed");
                            return null;
                        }
                        return mi;
                    }

                    @Override
                    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        if (getCursor().pollMessage("initMocks") != null) {
                            if (md.getBody() != null && md.getBody().getStatements().isEmpty()) {
                                return null;
                            }
                        }
                        return md;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                        if (service(AnnotationService.class).matches(updateCursor(cd), MOCKITO_EXTENSION_MATCHER) ||
                                service(AnnotationService.class).matches(updateCursor(cd), MOCKITO_JUNIT_MATCHER)) {
                            return super.visitClassDeclaration(cd, ctx);
                        }
                        return cd;
                    }
                }
        );
    }
}
