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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.List;

public class PowerMockRunnerDelegateToRunWith extends Recipe {

    private static final String POWER_MOCK_RUNNER_DELEGATE = "org.powermock.modules.junit4.PowerMockRunnerDelegate";
    private static final String POWER_MOCK_RUNNER = "org.powermock.modules.junit4.PowerMockRunner";
    private static final AnnotationMatcher DELEGATE_MATCHER =
            new AnnotationMatcher("@" + POWER_MOCK_RUNNER_DELEGATE);
    private static final AnnotationMatcher RUN_WITH_POWER_MOCK_RUNNER_MATCHER =
            new AnnotationMatcher("@org.junit.runner.RunWith(" + POWER_MOCK_RUNNER + ".class)");

    @Getter
    final String displayName = "Replace PowerMock runner with JUnit `@RunWith`";

    @Getter
    final String description = "Replaces `@RunWith(PowerMockRunner.class)`. If `@PowerMockRunnerDelegate(X.class)` " +
            "is present, promotes the delegate runner to `@RunWith(X.class)`. Otherwise, removes the " +
            "`@RunWith(PowerMockRunner.class)` annotation entirely.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(POWER_MOCK_RUNNER_DELEGATE, false),
                        new UsesType<>(POWER_MOCK_RUNNER, false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        if (!service(AnnotationService.class).matches(getCursor(), RUN_WITH_POWER_MOCK_RUNNER_MATCHER)) {
                            return cd;
                        }

                        maybeRemoveImport(POWER_MOCK_RUNNER);

                        Expression delegateRunnerArg = findDelegateRunnerArg(cd);
                        if (delegateRunnerArg != null) {
                            // Replace @RunWith(PowerMockRunner.class) argument with the delegate runner,
                            // and remove @PowerMockRunnerDelegate
                            maybeRemoveImport(POWER_MOCK_RUNNER_DELEGATE);
                            return cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
                                if (RUN_WITH_POWER_MOCK_RUNNER_MATCHER.matches(annotation)) {
                                    return annotation.withArguments(Collections.singletonList(delegateRunnerArg));
                                }
                                if (DELEGATE_MATCHER.matches(annotation)) {
                                    return null;
                                }
                                return annotation;
                            }));
                        }

                        // No delegate — just remove @RunWith(PowerMockRunner.class)
                        maybeRemoveImport("org.junit.runner.RunWith");
                        return cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
                            if (RUN_WITH_POWER_MOCK_RUNNER_MATCHER.matches(annotation)) {
                                return null;
                            }
                            return annotation;
                        }));
                    }

                    private @Nullable Expression findDelegateRunnerArg(J.ClassDeclaration cd) {
                        for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                            if (DELEGATE_MATCHER.matches(annotation)) {
                                List<Expression> args = annotation.getArguments();
                                if (args != null && !args.isEmpty()) {
                                    Expression arg = args.get(0);
                                    if (arg instanceof J.Assignment) {
                                        return ((J.Assignment) arg).getAssignment();
                                    }
                                    return arg;
                                }
                                return null;
                            }
                        }
                        return null;
                    }
                }
        );
    }
}
