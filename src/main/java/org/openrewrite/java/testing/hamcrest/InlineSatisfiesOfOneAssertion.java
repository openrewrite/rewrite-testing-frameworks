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
package org.openrewrite.java.testing.hamcrest;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns {@code assertThat(x).satisfies(arg -> assertThat(arg).isEqualTo(1))} into {@code assertThat(x).isEqualTo(1)},
 * and likewise for {@code assumeThat(x)}, which the migration of Hamcrest assumptions and of matchers wrapping
 * another matcher produces on the way.
 */
public class InlineSatisfiesOfOneAssertion extends Recipe {

    private static final MethodMatcher SATISFIES = new MethodMatcher("org.assertj.core.api.AbstractAssert satisfies(..)", true);
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher ASSUME_THAT = new MethodMatcher("org.assertj.core.api.Assumptions assumeThat(..)");
    private static final MethodMatcher AS = new MethodMatcher("org.assertj.core.api.Descriptable as(..)", true);

    @Getter
    final String displayName = "Inline `satisfies(..)` of one assertion on its argument";

    @Getter
    final String description = "Replace `assertThat(x).satisfies(arg -> assertThat(arg).isEqualTo(1))` with " +
            "`assertThat(x).isEqualTo(1)`, and the same for `assumeThat(x)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SATISFIES), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!SATISFIES.matches(mi) || mi.getArguments().size() != 1 || !(mi.getArguments().get(0) instanceof J.Lambda) ||
                    !isAssertionRoot(mi.getSelect())) {
                    return mi;
                }
                J.Lambda lambda = (J.Lambda) mi.getArguments().get(0);
                if (lambda.getParameters().getParameters().size() != 1 ||
                    !(lambda.getParameters().getParameters().get(0) instanceof J.VariableDeclarations) ||
                    !(lambda.getBody() instanceof J.MethodInvocation)) {
                    return mi;
                }
                String parameter = ((J.VariableDeclarations) lambda.getParameters().getParameters().get(0))
                        .getVariables().get(0).getSimpleName();
                J.MethodInvocation body = (J.MethodInvocation) lambda.getBody();
                J.MethodInvocation innermost = innermost(body);
                if (innermost == body || !ASSERT_THAT.matches(innermost) || innermost.getArguments().size() != 1 ||
                    !(innermost.getArguments().get(0) instanceof J.Identifier) ||
                    !parameter.equals(((J.Identifier) innermost.getArguments().get(0)).getSimpleName()) ||
                    references(body, parameter) != 1) {
                    return mi;
                }
                Expression receiver = mi.getSelect();
                maybeRemoveImport("org.assertj.core.api.Assertions.assertThat");
                maybeRemoveImport("org.assertj.core.api.Assertions");
                //noinspection DataFlowIssue
                return new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                        if (m == innermost) {
                            return (J.MethodInvocation) receiver.withPrefix(m.getPrefix());
                        }
                        return super.visitMethodInvocation(m, p);
                    }
                }.visitMethodInvocation(body, 0).withPrefix(mi.getPrefix());
            }

            private boolean isAssertionRoot(@Nullable Expression select) {
                while (select instanceof J.MethodInvocation && AS.matches((J.MethodInvocation) select)) {
                    select = ((J.MethodInvocation) select).getSelect();
                }
                return select instanceof J.MethodInvocation &&
                       (ASSERT_THAT.matches((J.MethodInvocation) select) || ASSUME_THAT.matches((J.MethodInvocation) select));
            }

            private J.MethodInvocation innermost(J.MethodInvocation chain) {
                J.MethodInvocation current = chain;
                while (current.getSelect() instanceof J.MethodInvocation) {
                    current = (J.MethodInvocation) current.getSelect();
                }
                return current;
            }

            private int references(J tree, String name) {
                AtomicInteger count = new AtomicInteger();
                new JavaIsoVisitor<AtomicInteger>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, AtomicInteger c) {
                        // the method's own name is no reference to the parameter
                        visit(m.getSelect(), c);
                        for (Expression argument : m.getArguments()) {
                            visit(argument, c);
                        }
                        return m;
                    }

                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, AtomicInteger c) {
                        visit(fieldAccess.getTarget(), c);
                        return fieldAccess;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger c) {
                        if (identifier.getSimpleName().equals(name)) {
                            c.incrementAndGet();
                        }
                        return identifier;
                    }
                }.visit(tree, count);
                return count.get();
            }
        });
    }
}
