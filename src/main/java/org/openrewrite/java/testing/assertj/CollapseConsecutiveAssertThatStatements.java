/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class CollapseConsecutiveAssertThatStatements extends Recipe {
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");

    @Getter
    final String displayName = "Collapse consecutive `assertThat` statements";

    @Getter
    final String description = "Collapse consecutive `assertThat` statements into single `assertThat` chained statement. This recipe ignores `assertThat` statements that have method invocation as parameter.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = super.visitBlock(block, ctx);

                List<Statement> statementsCollapsed = new ArrayList<>();
                for (List<Statement> group : getGroupedStatements(bl)) {
                    if (group.size() <= 1) {
                        statementsCollapsed.addAll(group);
                    } else {
                        statementsCollapsed.add(getCollapsedAssertThat(group));
                    }
                }

                return bl.withStatements(statementsCollapsed);
            }

            private List<List<Statement>> getGroupedStatements(J.Block bl) {
                List<Statement> originalStatements = bl.getStatements();
                List<List<Statement>> groupedStatements = new ArrayList<>();
                Expression currentActual = null; // The actual argument of the current group of assertThat statements
                List<Statement> currentGroup = new ArrayList<>();
                for (Statement statement : originalStatements) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation assertion = (J.MethodInvocation) statement;
                        if (isGroupableAssertion(assertion)) {
                            J.MethodInvocation assertThat = (J.MethodInvocation) assertion.getSelect();
                            assert assertThat != null;
                            Expression actual = assertThat.getArguments().get(0);
                            if (currentActual == null || !SemanticallyEqual.areEqual(currentActual, actual)) {
                                // Conclude the previous group
                                groupedStatements.add(currentGroup);
                                currentGroup = new ArrayList<>();
                                currentActual = actual;
                            }
                            currentGroup.add(statement);
                            continue;
                        }
                    }

                    // Conclude the previous group, and start a new group
                    groupedStatements.add(currentGroup);
                    currentGroup = new ArrayList<>();
                    currentActual = null;
                    // The current statement should not be grouped with any other statement
                    groupedStatements.add(singletonList(statement));
                }
                if (!currentGroup.isEmpty()) {
                    // Conclude the last group
                    groupedStatements.add(currentGroup);
                }
                return groupedStatements;
            }

            private boolean isGroupableAssertion(J.MethodInvocation assertion) {
                // Only match method invocations where the select is an assertThat, containing a non-method call argument
                if (ASSERT_THAT.matches(assertion.getSelect())) {
                    J.MethodInvocation assertThat = (J.MethodInvocation) assertion.getSelect();
                    if (assertThat != null) {
                        Expression assertThatArgument = assertThat.getArguments().get(0);
                        if (!(assertThatArgument instanceof MethodCall)) {
                            JavaType assertThatType = assertThat.getType();
                            JavaType assertionType = assertion.getType();

                            // Check if both types are the same or if the assertion type is assignable to assertThat type
                            // This handles cases where assertion methods return the same type (for fluent chaining)
                            // but avoids collapsing when methods like extracting() change the assertion type
                            if (assertThatType != null && assertionType != null) {
                                // First check for exact type match
                                if (TypeUtils.isOfType(assertThatType, assertionType)) {
                                    return true;
                                }

                                // For generic types like AbstractIntegerAssert<?>, we need to check the raw types
                                JavaType.Parameterized assertThatFq = TypeUtils.asParameterized(assertThatType);
                                JavaType.Parameterized assertionFq = TypeUtils.asParameterized(assertionType);

                                // If assertionType is a generic wildcard, try to get its bound
                                if (assertionFq == null && assertionType instanceof JavaType.GenericTypeVariable) {
                                    JavaType.GenericTypeVariable genericType = (JavaType.GenericTypeVariable) assertionType;
                                    if (!genericType.getBounds().isEmpty()) {
                                        assertionFq = TypeUtils.asParameterized(genericType.getBounds().get(0));
                                    }
                                }

                                if (assertThatFq != null && assertionFq != null) {
                                    return TypeUtils.isOfType(assertThatFq.getType(), assertionFq.getType());
                                }
                            }
                            return false;
                        }
                    }
                }
                return false;
            }

            private J.MethodInvocation getCollapsedAssertThat(List<Statement> consecutiveAssertThatStatement) {
                assert !consecutiveAssertThatStatement.isEmpty();
                Space originalPrefix = consecutiveAssertThatStatement.get(0).getPrefix();
                String originalIndent = originalPrefix.getLastWhitespace().replaceAll("^\\s+\n", "\n");
                String chainedIndent = originalIndent + (originalIndent.contains("\t") ? "\t\t" : "        ");
                J.MethodInvocation collapsed = null;
                for (Statement st : consecutiveAssertThatStatement) {
                    J.MethodInvocation assertion = (J.MethodInvocation) st;
                    J.MethodInvocation assertThat = (J.MethodInvocation) assertion.getSelect();
                    assert assertThat != null;
                    J.MethodInvocation newSelect = collapsed == null ? assertThat : collapsed;

                    collapsed = assertion.getPadding().withSelect(JRightPadded
                            .build((Expression) newSelect.withPrefix(Space.EMPTY))
                            .withAfter(Space.build(chainedIndent, ListUtils.map(st.getPrefix().getComments(), c -> c.withSuffix(chainedIndent)))));
                }
                return requireNonNull(collapsed).withPrefix(originalPrefix.withComments(emptyList()));
            }
        });
    }
}
