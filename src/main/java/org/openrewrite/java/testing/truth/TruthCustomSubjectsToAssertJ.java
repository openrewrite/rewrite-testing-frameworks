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
package org.openrewrite.java.testing.truth;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

public class TruthCustomSubjectsToAssertJ extends Recipe {

    private static final MethodMatcher ASSERT_ABOUT = new MethodMatcher("com.google.common.truth.Truth assertAbout(..)");

    @Override
    public String getDisplayName() {
        return "Migrate Truth custom subjects to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Marks Google Truth's `assertAbout()` usage for manual review as AssertJ handles custom assertions differently.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_ABOUT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                if (ASSERT_ABOUT.matches(mi)) {
                    // Truth's assertAbout() is used for custom subjects
                    // AssertJ uses a different pattern with custom assertion classes
                    // This requires manual migration to create custom AssertJ assertion classes
                    return SearchResult.found(mi,
                            "Truth's assertAbout() with custom subjects requires manual migration to AssertJ custom assertions");
                }

                return mi;
            }
        });
    }
}
