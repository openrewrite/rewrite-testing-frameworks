/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodInvocation;

public class UseExplicitSize extends Recipe {
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher("org.assertj.core.api.* isEqualTo(..)");
    private static final MethodMatcher SIZE = new MethodMatcher("java.util.Collection size(..)", true);

    @Override
    public String getDisplayName() {
        return "Use AssertJ `hasSize()` on collections";
    }

    @Override
    public String getDescription() {
        return "Convert `assertThat(collection.size()).isEqualTo(Y)` to AssertJ's `assertThat(collection).hasSize()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.assertj.core.api.Assertions", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
                J.MethodInvocation method = super.visitMethodInvocation(m, ctx);
                if (!IS_EQUAL_TO.matches(method)) {
                    return method;
                }

                if (!(method.getSelect() instanceof J.MethodInvocation)) {
                    return method;
                }

                if (!ASSERT_THAT.matches((J.MethodInvocation) method.getSelect())) {
                    return method;
                }

                J.MethodInvocation assertThat = (MethodInvocation) method.getSelect();

                if (!(assertThat.getArguments().get(0) instanceof J.MethodInvocation)) {
                    return method;
                }

                J.MethodInvocation size = (J.MethodInvocation) assertThat.getArguments().get(0);

                if (!SIZE.matches(size)) {
                    return method;
                }

                Expression list = size.getSelect();
                Expression expectedSize = method.getArguments().get(0);

                return JavaTemplate.builder("assertThat(#{any(java.util.List)}).hasSize(#{any()});")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build()
                        .apply(
                                updateCursor(method),
                                method.getCoordinates().replace(),
                                list,
                                expectedSize
                        );
            }
        });
    }
}
