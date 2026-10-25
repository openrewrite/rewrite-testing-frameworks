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
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

public class SimplifyRedundantAssertJChains extends Recipe {

    @Getter
    final String displayName = "Simplify redundant AssertJ assertion chains";

    @Getter
    final String description = "Removes redundant AssertJ assertions when chained methods already provide the same or stronger guarantees.";

    // Matcher for hasSize() - redundant when followed by assertions that verify exact contents
    private static final MethodMatcher hasSizeMatcher = new MethodMatcher("org.assertj.core.api..* hasSize(..)");

    // Matchers for assertions that already imply exact size
    private static final MethodMatcher exactSizeImplyingMatchers = new MethodMatcher("org.assertj.core.api..* containsExactly*(..)");

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
            // Not AbstractAssert#matches(Predicate), which hands a null actual to the predicate
            new MethodMatcher("org.assertj.core.api.AbstractCharSequenceAssert matches(..)", true),
            new MethodMatcher("org.assertj.core.api..* isEqualToIgnoringCase(..)"),

            // More assertions that imply non-null - using wildcards consistently
            exactSizeImplyingMatchers,
            new MethodMatcher("org.assertj.core.api..* containsOnly(..)"),
            new MethodMatcher("org.assertj.core.api..* containsAll(..)"),
            new MethodMatcher("org.assertj.core.api..* containsKey(..)"),
            new MethodMatcher("org.assertj.core.api..* containsKeys(..)"),
            new MethodMatcher("org.assertj.core.api..* containsValue(..)"),
            new MethodMatcher("org.assertj.core.api..* containsEntry(..)"),
            new MethodMatcher("org.assertj.core.api..* isPresent()"),
            new MethodMatcher("org.assertj.core.api..* isNotPresent()"),
            new MethodMatcher("org.assertj.core.api..* isTrue()"),
            new MethodMatcher("org.assertj.core.api..* isFalse()"),
            // Note: isNotEqualTo and isNotSameAs are NOT here because they pass when actual is null
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
    // These fail on an empty actual when given at least one value, which for a CharSequence must itself be non-empty
    private static final Set<String> nonEmptyImplyingContains = new HashSet<>(asList("contains", "containsAnyOf", "containsEntries", "containsEntry", "containsExactly", "containsExactlyInAnyOrder", "containsKey", "containsKeys", "containsOnly", "containsOnlyKeys", "containsOnlyOnce", "containsSequence", "containsSubsequence", "containsValue", "containsValues"));
    private static final MethodMatcher charSequenceContainsMatcher = new MethodMatcher("org.assertj.core.api.AbstractCharSequenceAssert contains*(..)", true);
    // Matcher for isPresent() preceded by another assertion
    private static final MethodMatcher isPresentMatcher = new MethodMatcher("org.assertj.core.api..* isPresent()");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(hasSizeMatcher),
                        new UsesMethod<>(isNotNullMatcher),
                        new UsesMethod<>(isNotEmptyMatcher),
                        new UsesMethod<>(isPresentMatcher)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
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
                if (isNotEmptyMatcher.matches(select) && impliesNotEmpty(mi)) {
                    // Remove the redundant isNotEmpty()
                    return mi.withSelect(select.getSelect());
                }

                // Check for isPresent() followed by contains() (for Optional)
                if (isPresentMatcher.matches(select) && containsMatcher.matches(mi)) {
                    // Remove the redundant isPresent()
                    return mi.withSelect(select.getSelect());
                }

                // Check for hasSize() followed by containsExactly() or similar
                if (hasSizeMatcher.matches(select) && exactSizeImplyingMatchers.matches(mi)) {
                    // Remove the redundant hasSize()
                    return mi.withSelect(select.getSelect());
                }

                return mi;
            }
        });
    }

    private static boolean impliesNotEmpty(J.MethodInvocation mi) {
        if (!containsMatcher.matches(mi) || !nonEmptyImplyingContains.contains(mi.getSimpleName()) || mi.getArguments().get(0) instanceof J.Empty) {
            return false;
        }
        boolean charSequence = charSequenceContainsMatcher.matches(mi);
        for (Expression arg : mi.getArguments()) {
            if (arg.getType() instanceof JavaType.Array || TypeUtils.isAssignableTo("java.lang.Iterable", arg.getType())) {
                return false;
            }
            Object literal = arg instanceof J.Literal ? ((J.Literal) arg).getValue() : null;
            if (charSequence && !(literal instanceof String && !((String) literal).isEmpty())) {
                return false;
            }
        }
        return true;
    }
}
