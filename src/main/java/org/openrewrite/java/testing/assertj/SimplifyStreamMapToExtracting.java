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

/**
 * Simplifies AssertJ assertions that use {@code stream().map()} to use the dedicated
 * {@code extracting()} method instead.
 * <p>
 * For example:
 * <pre>
 * assertThat(rows.stream().map(Row::getValue)).contains("value")
 * </pre>
 * becomes:
 * <pre>
 * assertThat(rows).extracting(Row::getValue).contains("value")
 * </pre>
 */
public class SimplifyStreamMapToExtracting extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher STREAM_MATCHER = new MethodMatcher("java.util.Collection stream()");
    private static final MethodMatcher MAP_MATCHER = new MethodMatcher("java.util.stream.Stream map(java.util.function.Function)");

    @Override
    public String getDisplayName() {
        return "Simplify `assertThat(collection.stream().map(...))` to `assertThat(collection).extracting(...)`";
    }

    @Override
    public String getDescription() {
        return "Simplifies AssertJ assertions that use `stream().map()` to extract values from a collection " +
               "by using the dedicated `extracting()` method instead. This makes the assertion more readable " +
               "and leverages AssertJ's built-in extraction capabilities.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(STREAM_MATCHER),
                        new UsesMethod<>(MAP_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Check if this is an assertThat call
                        if (!ASSERT_THAT_MATCHER.matches(mi) || mi.getArguments().size() != 1) {
                            return mi;
                        }

                        // Check if the argument is a method invocation (the map() call)
                        Expression arg = mi.getArguments().get(0);
                        if (!(arg instanceof J.MethodInvocation)) {
                            return mi;
                        }

                        J.MethodInvocation mapCall = (J.MethodInvocation) arg;
                        if (!MAP_MATCHER.matches(mapCall) || mapCall.getArguments().size() != 1) {
                            return mi;
                        }

                        // Check if map() is called on stream()
                        if (!(mapCall.getSelect() instanceof J.MethodInvocation)) {
                            return mi;
                        }

                        J.MethodInvocation streamCall = (J.MethodInvocation) mapCall.getSelect();
                        if (!STREAM_MATCHER.matches(streamCall)) {
                            return mi;
                        }

                        // Get the collection (select of stream() call)
                        Expression collection = streamCall.getSelect();
                        if (collection == null) {
                            return mi;
                        }

                        // Get the mapper function argument
                        Expression mapper = mapCall.getArguments().get(0);

                        // Apply the transformation
                        return JavaTemplate.builder("assertThat(#{any(java.lang.Iterable)}).extracting(#{any(java.util.function.Function)})")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), collection, mapper);
                    }
                }
        );
    }
}
