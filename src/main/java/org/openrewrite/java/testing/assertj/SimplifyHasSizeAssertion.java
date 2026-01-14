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
package org.openrewrite.java.testing.assertj;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static java.util.Collections.singletonList;

public class SimplifyHasSizeAssertion extends Recipe {

    private static final MethodMatcher HAS_SIZE_MATCHER = new MethodMatcher("org.assertj.core.api.* hasSize(int)");

    private static final MethodMatcher CHAR_SEQUENCE_LENGTH_MATCHER = new MethodMatcher("java.lang.CharSequence length()", true);
    private static final MethodMatcher ITERABLE_SIZE_MATCHER = new MethodMatcher("java.lang.Iterable size()", true);
    private static final MethodMatcher MAP_SIZE_MATCHER = new MethodMatcher("java.util.Map size()", true);

    private static final String HAS_SAME_SIZE_AS = "hasSameSizeAs";

    @Getter
    final String displayName = "Simplify AssertJ assertions with `hasSize` argument";

    @Getter
    final String description = "Simplify AssertJ assertions by replacing `hasSize` with `hasSameSizeAs` dedicated assertions.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(HAS_SIZE_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!HAS_SIZE_MATCHER.matches(mi)) {
                            return mi;
                        }

                        Expression expression = mi.getArguments().get(0);
                        if (expression instanceof J.MethodInvocation) {
                            if (CHAR_SEQUENCE_LENGTH_MATCHER.matches(expression) ||
                                    ITERABLE_SIZE_MATCHER.matches(expression) ||
                                    MAP_SIZE_MATCHER.matches(expression)) {
                                return updateMethodInvocation(mi, ((J.MethodInvocation) expression).getSelect());
                            }
                        } else if (expression instanceof J.FieldAccess) {
                            Expression target = ((J.FieldAccess) expression).getTarget();
                            if (target.getType() instanceof JavaType.Array) {
                                return updateMethodInvocation(mi, target);
                            }
                        }
                        return mi;
                    }

                    private J.MethodInvocation updateMethodInvocation(J.MethodInvocation mi, Expression argument) {
                        return mi.withMethodType(mi.getMethodType().withName(HAS_SAME_SIZE_AS))
                                .withName(mi.getName().withSimpleName(HAS_SAME_SIZE_AS))
                                .withArguments(singletonList(argument));
                    }
                });
    }
}
