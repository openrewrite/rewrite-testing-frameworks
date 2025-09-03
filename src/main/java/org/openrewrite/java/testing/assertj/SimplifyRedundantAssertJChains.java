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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class SimplifyRedundantAssertJChains extends Recipe {

    @Override
    public String getDisplayName() {
        return "Simplify redundant AssertJ assertion chains";
    }

    @Override
    public String getDescription() {
        return "Removes redundant AssertJ assertions when chained methods already provide the same or stronger guarantees.";
    }

    // Matcher for isNotNull() method - use wildcard to match any AbstractAssert subclass
    private static final MethodMatcher isNotNullMatcher = new MethodMatcher("org.assertj.core.api..* isNotNull()");

    // Matchers for assertions that already imply non-null - use wildcards for flexibility
    private static final MethodMatcher[] nonNullImplyingMatchers = {
            // String assertions
            new MethodMatcher("org.assertj.core.api..* isNotEmpty()"),
            new MethodMatcher("org.assertj.core.api..* isEmpty()"),
            new MethodMatcher("org.assertj.core.api..* isBlank()"),
            new MethodMatcher("org.assertj.core.api..* isNotBlank()"),
            new MethodMatcher("org.assertj.core.api..* hasSize(..)"),
            new MethodMatcher("org.assertj.core.api..* contains(..)"),
            new MethodMatcher("org.assertj.core.api..* startsWith(..)"),
            new MethodMatcher("org.assertj.core.api..* endsWith(..)"),
            new MethodMatcher("org.assertj.core.api..* matches(..)"),
            new MethodMatcher("org.assertj.core.api..* isEqualToIgnoringCase(..)"),

            // More assertions that imply non-null - using wildcards consistently
            new MethodMatcher("org.assertj.core.api..* containsOnly(..)"),
            new MethodMatcher("org.assertj.core.api..* containsExactly(..)"),
            new MethodMatcher("org.assertj.core.api..* containsAll(..)"),
            new MethodMatcher("org.assertj.core.api..* containsKey(..)"),
            new MethodMatcher("org.assertj.core.api..* containsKeys(..)"),
            new MethodMatcher("org.assertj.core.api..* containsValue(..)"),
            new MethodMatcher("org.assertj.core.api..* containsEntry(..)"),
            new MethodMatcher("org.assertj.core.api..* isPresent()"),
            new MethodMatcher("org.assertj.core.api..* isNotPresent()"),
            new MethodMatcher("org.assertj.core.api..* isTrue()"),
            new MethodMatcher("org.assertj.core.api..* isFalse()"),
            new MethodMatcher("org.assertj.core.api..* isNotEqualTo(..)"),
            new MethodMatcher("org.assertj.core.api..* isNotSameAs(..)"),
            new MethodMatcher("org.assertj.core.api..* isInstanceOf(..)"),
            new MethodMatcher("org.assertj.core.api..* hasSameClassAs(..)"),
            new MethodMatcher("org.assertj.core.api..* hasToString(..)"),
            new MethodMatcher("org.assertj.core.api..* isZero()"),
            new MethodMatcher("org.assertj.core.api..* isNotZero()"),
            new MethodMatcher("org.assertj.core.api..* isPositive()"),
            new MethodMatcher("org.assertj.core.api..* isNegative()"),
            new MethodMatcher("org.assertj.core.api..* exists()"),
            new MethodMatcher("org.assertj.core.api..* isFile()"),
            new MethodMatcher("org.assertj.core.api..* isDirectory()"),
            new MethodMatcher("org.assertj.core.api..* isRegularFile()"),
            new MethodMatcher("org.assertj.core.api..* canRead()"),
            new MethodMatcher("org.assertj.core.api..* canWrite()")
    };
    // Matcher for isNotEmpty() preceded by isNotEmpty()
    private static final MethodMatcher isNotEmptyMatcher = new MethodMatcher("org.assertj.core.api..* isNotEmpty()");
    private static final MethodMatcher containsMatcher = new MethodMatcher("org.assertj.core.api..* contains*(..)");
    // Matcher for isPresent() preceded by another assertion
    private static final MethodMatcher isPresentMatcher = new MethodMatcher("org.assertj.core.api..* isPresent()");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(isNotNullMatcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                // Check if the select is a method invocation
                if (!(mi.getSelect() instanceof J.MethodInvocation)) {
                    return mi;
                }

                J.MethodInvocation select = (J.MethodInvocation) mi.getSelect();

                // Check for isNotNull() followed by an assertion that implies non-null
                if (isNotNullMatcher.matches(select)) {
                    for (MethodMatcher matcher : nonNullImplyingMatchers) {
                        if (matcher.matches(mi)) {
                            // Remove the redundant isNotNull() by returning the method with the select's select
                            return mi.withSelect(select.getSelect());
                        }
                    }
                }

                // Check for isNotEmpty() followed by contains()
                if (isNotEmptyMatcher.matches(select) && containsMatcher.matches(mi)) {
                    // Remove the redundant isNotEmpty()
                    return mi.withSelect(select.getSelect());
                }

                // Check for isPresent() followed by contains() (for Optional)
                if (isPresentMatcher.matches(select) && containsMatcher.matches(mi)) {
                    // Remove the redundant isPresent()
                    return mi.withSelect(select.getSelect());
                }

                return mi;
            }
        });
    }
}
