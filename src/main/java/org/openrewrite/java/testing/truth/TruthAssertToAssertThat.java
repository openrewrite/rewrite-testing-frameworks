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
package org.openrewrite.java.testing.truth;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

public class TruthAssertToAssertThat extends Recipe {

    private static final MethodMatcher ASSERT_MATCHER = new MethodMatcher("com.google.common.truth.Truth assert_()");

    @Getter
    final String displayName = "Convert Truth `assert_()` to AssertJ";

    @Getter
    final String description = "Converts Google Truth's `assert_()` method to AssertJ's standard assertion pattern.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                if (ASSERT_MATCHER.matches(mi)) {
                    // Truth's assert_() returns a StandardSubjectBuilder which is used differently
                    // For now, we'll mark this as needing manual review
                    return SearchResult.found(mi, "Truth's assert_() requires manual review for migration to AssertJ");
                }

                return mi;
            }
        });
    }
}
