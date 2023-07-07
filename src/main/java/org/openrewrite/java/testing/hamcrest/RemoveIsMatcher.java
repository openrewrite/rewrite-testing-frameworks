/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.*;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.tree.J;

@SuppressWarnings("NullableProblems")
public class RemoveIsMatcher extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove Hamcrest `is(Matcher)`";
    }

    @Override
    public String getDescription() {
        return "Remove Hamcrest `is(Matcher)` ahead of migration.";
    }

    static final MethodMatcher IS_MATCHER = new MethodMatcher("org.hamcrest.Matchers is(org.hamcrest.Matcher)");
    static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                Cursor c = getCursor().dropParentWhile(cr -> cr instanceof J.Block ||
                                                             cr instanceof J.Identifier ||
                                                             !(cr instanceof Tree));

                if (IS_MATCHER.matches(mi) && c.getMessage("CHANGES_KEY") != null) {
                    maybeRemoveImport("org.hamcrest.Matchers.is");
                    return mi.getArguments().get(0).withPrefix(mi.getPrefix());
                }else if (ASSERT_THAT_MATCHER.matches(mi)) {
                    getCursor().putMessage("CHANGES_KEY", mi);
                }
                return super.visitMethodInvocation(mi, ctx);
            }
        };
    }
}
