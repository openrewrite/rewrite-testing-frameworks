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
package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class RemoveIsMatcher extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove Hamcrest `is(Matcher)`";
    }

    @Override
    public String getDescription() {
        return "Remove Hamcrest `is(Matcher)` ahead of migration.";
    }

    static final MethodMatcher IS_MATCHER = new MethodMatcher("org.hamcrest.*Matchers is(org.hamcrest.Matcher)");
    static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT_MATCHER), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                if (ASSERT_THAT_MATCHER.matches(mi)) {
                    getCursor().putMessage("ASSERT_THAT", mi);
                } else if (IS_MATCHER.matches(mi) && getCursor().pollNearestMessage("ASSERT_THAT") != null) {
                    maybeRemoveImport("org.hamcrest.Matchers.is");
                    maybeRemoveImport("org.hamcrest.CoreMatchers.is");
                    return mi.getArguments().get(0).withPrefix(mi.getPrefix());
                }
                return super.visitMethodInvocation(mi, ctx);
            }
        });
    }
}
