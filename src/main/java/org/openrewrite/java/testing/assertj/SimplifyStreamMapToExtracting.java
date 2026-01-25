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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class SimplifyStreamMapToExtracting extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(*)");
    private static final MethodMatcher STREAM_MATCHER = new MethodMatcher("java.util.Collection stream()");
    private static final MethodMatcher MAP_MATCHER = new MethodMatcher("java.util.stream.Stream map(java.util.function.Function)");

    @Getter
    final String displayName = "Simplify `assertThat(collection.stream().map(...))` to `assertThat(collection).extracting(...)`";

    @Getter
    final String description = "Simplifies AssertJ assertions that use `stream().map()` to extract values from a collection " +
            "by using the dedicated `extracting()` method instead. This makes the assertion more readable " +
            "and leverages AssertJ's built-in extraction capabilities.";

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
                        if (!ASSERT_THAT_MATCHER.matches(mi)) {
                            return mi;
                        }

                        // Check if the argument is a method invocation (the map() call)
                        if (!MAP_MATCHER.matches(mi.getArguments().get(0))) {
                            return mi;
                        }

                        // Check if map() is called on stream()
                        J.MethodInvocation mapCall = (J.MethodInvocation) mi.getArguments().get(0);
                        if (!STREAM_MATCHER.matches(mapCall.getSelect())) {
                            return mi;
                        }

                        // Apply the transformation
                        return JavaTemplate.builder("assertThat(#{any(java.lang.Iterable)}).extracting(#{any(java.util.function.Function)})")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(),
                                        mi.getCoordinates().replace(),
                                        ((J.MethodInvocation) mapCall.getSelect()).getSelect(),
                                        mapCall.getArguments().get(0));
                    }
                }
        );
    }
}
