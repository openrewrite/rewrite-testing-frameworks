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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class SimplifySequencedCollectionAssertions extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher GET_FIRST_MATCHER = new MethodMatcher("java.util.* getFirst()");
    private static final MethodMatcher GET_LAST_MATCHER = new MethodMatcher("java.util.* getLast()");

    @Override
    public String getDisplayName() {
        return "Simplify AssertJ assertions on SequencedCollection";
    }

    @Override
    public String getDescription() {
        return "Simplify AssertJ assertions on SequencedCollection by using dedicated assertion methods. " +
                "For example, `assertThat(sequencedCollection.getLast())` can be simplified to `assertThat(sequencedCollection).last()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(GET_FIRST_MATCHER),
                        new UsesMethod<>(GET_LAST_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Check if this is an assertThat call
                        if (!ASSERT_THAT_MATCHER.matches(mi) || mi.getArguments().size() != 1) {
                            return mi;
                        }

                        // Check if the argument is a method invocation
                        Expression arg = mi.getArguments().get(0);
                        if (arg instanceof J.MethodInvocation) {
                            // Check if the method is getFirst() or getLast() on a SequencedCollection
                            if (GET_FIRST_MATCHER.matches(arg)) {
                                return assertThat(mi, (J.MethodInvocation) arg, "first", ctx);
                            }
                            if (GET_LAST_MATCHER.matches(arg)) {
                                return assertThat(mi, (J.MethodInvocation) arg, "last", ctx);
                            }
                        }

                        return mi;
                    }

                    private J.MethodInvocation assertThat(J.MethodInvocation mi, J.MethodInvocation argMethod, String dedicatedAssertion, ExecutionContext ctx) {
                        return JavaTemplate.builder("assertThat(#{any(java.lang.Iterable)})." + dedicatedAssertion + "()")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), argMethod.getSelect());
                    }
                }
        );
    }
}
