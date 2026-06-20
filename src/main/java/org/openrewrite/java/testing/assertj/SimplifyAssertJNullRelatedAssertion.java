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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class SimplifyAssertJNullRelatedAssertion extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher IS_TRUE_MATCHER = new MethodMatcher("org.assertj.core.api.* isTrue()");
    private static final MethodMatcher IS_FALSE_MATCHER = new MethodMatcher("org.assertj.core.api.* isFalse()");
    private static final MethodMatcher IS_EQUAL_TO_MATCHER = new MethodMatcher("org.assertj.core.api.* isEqualTo(..)");

    @Getter
    final String displayName = "Simplify AssertJ assertions on `null` reference comparisons";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5838");

    @Getter
    final String description = "Replace `assertThat(x == null).isTrue()` and its variants with the dedicated " +
            "`assertThat(x).isNull()` / `assertThat(x).isNotNull()`. Beyond being more expressive, this avoids the " +
            "compilation error that results when the `null` literal ends up as the `assertThat` argument " +
            "(e.g. `assertThat(null == x).isTrue()` becoming `assertThat(null).isSameAs(x)`).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                // Match isTrue()/isFalse() and isEqualTo(true|false), so we fire before the picnic boolean and
                // object rules in the same cycle, rather than after they have already produced an `isSameAs`.
                Boolean assertsTrue = booleanAssertion(mi);
                if (assertsTrue == null || !ASSERT_THAT_MATCHER.matches(mi.getSelect())) {
                    return mi;
                }

                // Unwrap parentheses and logical negations around the assertThat argument
                Expression argument = ((J.MethodInvocation) mi.getSelect()).getArguments().get(0);
                boolean negated = false;
                while (true) {
                    if (argument instanceof J.Parentheses) {
                        argument = (Expression) ((J.Parentheses<?>) argument).getTree();
                    } else if (argument instanceof J.Unary && ((J.Unary) argument).getOperator() == J.Unary.Type.Not) {
                        negated = !negated;
                        argument = ((J.Unary) argument).getExpression();
                    } else {
                        break;
                    }
                }
                if (!(argument instanceof J.Binary)) {
                    return mi;
                }
                J.Binary binary = (J.Binary) argument;
                J.Binary.Type operator = binary.getOperator();
                if (operator != J.Binary.Type.Equal && operator != J.Binary.Type.NotEqual) {
                    return mi;
                }

                // Only handle comparisons against the `null` literal; leave `x == y` to AssertJObjectRules (isSameAs)
                Expression actual;
                if (J.Literal.isLiteralValue(binary.getLeft(), null)) {
                    actual = binary.getRight();
                } else if (J.Literal.isLiteralValue(binary.getRight(), null)) {
                    actual = binary.getLeft();
                } else {
                    return mi;
                }

                boolean effectiveTrue = assertsTrue != negated;
                boolean expectNull = (operator == J.Binary.Type.Equal) == effectiveTrue;
                String dedicatedAssertion = expectNull ? "isNull" : "isNotNull";

                // Preserve the original `assertThat` select (static import or qualified), only swapping its argument
                J.MethodInvocation newAssertThat = ((J.MethodInvocation) mi.getSelect())
                        .withArguments(singletonList(actual.withPrefix(Space.EMPTY)));
                return JavaTemplate.builder("#{any()}." + dedicatedAssertion + "()")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), newAssertThat);
            }

            private @Nullable Boolean booleanAssertion(J.MethodInvocation mi) {
                if (IS_TRUE_MATCHER.matches(mi)) {
                    return true;
                }
                if (IS_FALSE_MATCHER.matches(mi)) {
                    return false;
                }
                if (IS_EQUAL_TO_MATCHER.matches(mi) && mi.getArguments().size() == 1 &&
                        mi.getArguments().get(0) instanceof J.Literal) {
                    Object value = ((J.Literal) mi.getArguments().get(0)).getValue();
                    if (Boolean.TRUE.equals(value)) {
                        return true;
                    }
                    if (Boolean.FALSE.equals(value)) {
                        return false;
                    }
                }
                return null;
            }
        });
    }
}
