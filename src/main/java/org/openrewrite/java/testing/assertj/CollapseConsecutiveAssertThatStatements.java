/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollapseConsecutiveAssertThatStatements extends Recipe {
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");

    @Override
    public String getDisplayName() {
        return "Collapse consecutive `assertThat` statements";
    }

    @Override
    public String getDescription() {
        return "Collapse consecutive `assertThat` statements into single `assertThat` chained statement. This recipe ignores `assertThat` statements that have method invocation as parameter.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = super.visitBlock(block, ctx);

                List<Statement> modifiedStatements = new ArrayList<>();
                for (List<Statement> group : getGroupedStatements(bl)) {
                    if (group.size() <= 1) {
                        modifiedStatements.addAll(group);
                    } else {
                        modifiedStatements.add(getCollapsedAssertThat(group));
                    }
                }

                return maybeAutoFormat(block, bl.withStatements(modifiedStatements), ctx);
            }

            private List<List<Statement>> getGroupedStatements(J.Block bl) {
                List<Statement> originalStatements = bl.getStatements();
                List<List<Statement>> groupedStatements = new ArrayList<>();
                Expression currentActual = null; // The actual argument of the current group of assertThat statements
                List<Statement> currentGroup = new ArrayList<>();
                for (Statement statement : originalStatements) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation assertion = (J.MethodInvocation) statement;
                        if (ASSERT_THAT.matches(assertion.getSelect())) {
                            J.MethodInvocation assertThat = (J.MethodInvocation) assertion.getSelect();
                            Expression actual = assertThat.getArguments().get(0);
                            if (currentActual == null) {
                                currentActual = actual;
                            }
                            if (SemanticallyEqual.areEqual(currentActual, actual) && !(actual instanceof MethodCall)) {
                                currentGroup.add(statement);
                            } else {
                                // Conclude the previous group
                                groupedStatements.add(currentGroup);
                                currentGroup = new ArrayList<>();
                                currentActual = actual;
                                // Add current statement to the new group
                                currentGroup.add(statement);
                            }
                            continue;
                        }
                    }

                    // Conclude the previous group, and start a new group
                    groupedStatements.add(currentGroup);
                    currentGroup = new ArrayList<>();
                    currentActual = null;
                    // The current statement should not be grouped with any other statement
                    groupedStatements.add(Collections.singletonList(statement));
                }
                if (!currentGroup.isEmpty()) {
                    // Conclude the last group
                    groupedStatements.add(currentGroup);
                }
                return groupedStatements;
            }

            private J.MethodInvocation getCollapsedAssertThat(List<Statement> consecutiveAssertThatStatement) {
                J.MethodInvocation collapsed = null;
                for (Statement st : consecutiveAssertThatStatement) {
                    J.MethodInvocation mi = (J.MethodInvocation) st;
                    collapsed = collapsed == null ?
                            mi.getPadding().withSelect(JRightPadded.build(mi.getSelect()).withAfter(mi.getPrefix())) :
                            mi.getPadding().withSelect(JRightPadded.build((Expression) collapsed.withPrefix(Space.EMPTY)).withAfter(collapsed.getPrefix()));
                }
                return collapsed;
            }
        });
    }
}
