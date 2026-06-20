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
package org.openrewrite.java.testing.assertj;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class DecomposeConjunctionAssertion extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher IS_TRUE_MATCHER = new MethodMatcher("org.assertj.core.api.* isTrue()");

    @Getter
    final String displayName = "Decompose `assertThat` on conjunctions into separate assertions";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5838");

    @Getter
    final String description = "Split `assertThat(a && b).isTrue()` into separate `assertThat(a).isTrue()` and " +
            "`assertThat(b).isTrue()` statements, so each condition is asserted (and reported) on its own. This lets the " +
            "dedicated assertion recipes simplify each conjunct, and `CollapseConsecutiveAssertThatStatements` fuse them " +
            "back into a single chain when the actual is a plain expression. Only the direct `assertThat(...).isTrue()` " +
            "form is decomposed; `isFalse()` is left alone, as negating a conjunction is not equivalent to negating each " +
            "conjunct.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = super.visitBlock(block, ctx);
                return bl.withStatements(ListUtils.flatMap(bl.getStatements(), statement ->
                        isDecomposableConjunction(statement) ?
                                decompose((J.MethodInvocation) statement) : statement));
            }

            private boolean isDecomposableConjunction(Statement statement) {
                if (!(statement instanceof J.MethodInvocation)) {
                    return false;
                }
                J.MethodInvocation mi = (J.MethodInvocation) statement;
                // Only the direct `assertThat(...).isTrue()` form; skip intermediate `.as()`/`.describedAs()` chains
                if (!IS_TRUE_MATCHER.matches(mi) || !ASSERT_THAT_MATCHER.matches(mi.getSelect())) {
                    return false;
                }
                Expression argument = unwrapParentheses(((J.MethodInvocation) mi.getSelect()).getArguments().get(0));
                return argument instanceof J.Binary && ((J.Binary) argument).getOperator() == J.Binary.Type.And;
            }

            private List<Statement> decompose(J.MethodInvocation isTrue) {
                J.MethodInvocation assertThat = (J.MethodInvocation) isTrue.getSelect();
                List<Expression> conjuncts = new ArrayList<>();
                flattenConjuncts(assertThat.getArguments().get(0), conjuncts);

                // Subsequent statements reuse the original indentation, without the original leading comments
                Space subsequentPrefix = Space.build(isTrue.getPrefix().getLastWhitespace(), emptyList());

                List<Statement> result = new ArrayList<>();
                for (int i = 0; i < conjuncts.size(); i++) {
                    // The conjuncts are all boolean, so the `assertThat(boolean)` overload and `isTrue()` types are preserved
                    J.MethodInvocation newAssertThat = assertThat.withArguments(
                            singletonList(conjuncts.get(i).withPrefix(Space.EMPTY)));
                    J.MethodInvocation statement = isTrue.withSelect(newAssertThat);
                    result.add(statement.withPrefix(i == 0 ? isTrue.getPrefix() : subsequentPrefix));
                }
                return result;
            }

            private void flattenConjuncts(Expression expression, List<Expression> conjuncts) {
                Expression unwrapped = unwrapParentheses(expression);
                if (unwrapped instanceof J.Binary && ((J.Binary) unwrapped).getOperator() == J.Binary.Type.And) {
                    flattenConjuncts(((J.Binary) unwrapped).getLeft(), conjuncts);
                    flattenConjuncts(((J.Binary) unwrapped).getRight(), conjuncts);
                } else {
                    conjuncts.add(unwrapped);
                }
            }

            private Expression unwrapParentheses(Expression expression) {
                while (expression instanceof J.Parentheses) {
                    expression = (Expression) ((J.Parentheses<?>) expression).getTree();
                }
                return expression;
            }
        });
    }
}
