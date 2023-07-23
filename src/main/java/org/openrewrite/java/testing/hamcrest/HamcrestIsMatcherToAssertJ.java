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

import org.jetbrains.annotations.NotNull;
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
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class HamcrestIsMatcherToAssertJ extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Hamcrest `is(Object)` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate Hamcrest `is(Object)` to AssertJ `Assertions.assertThat(..)`.";
    }

    static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");

    static final MethodMatcher IS_OBJECT_MATCHER = new MethodMatcher("org.hamcrest.Matchers is(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);

                List<Expression> arguments = mi.getArguments();
                Expression isMatcher = arguments.get(arguments.size() - 1);
                if (!ASSERT_THAT_MATCHER.matches(mi) || !IS_OBJECT_MATCHER.matches(isMatcher)) {
                    return mi;
                }
                Expression reason = arguments.size() == 3 ? arguments.get(0) : null;
                Expression actual = arguments.get(arguments.size() - 2);

                // Handle `is(Matcher)` in src/main/resources/META-INF/rewrite/hamcrest.yml
                Expression isMatcherArgument = ((J.MethodInvocation) isMatcher).getArguments().get(0);
                if (TypeUtils.isOfClassType(isMatcherArgument.getType(), "org.hamcrest.Matcher")) {
                    return mi;
                }

                if (TypeUtils.asArray(actual.getType()) != null) {
                    return replace(ctx, mi, reason, actual, isMatcherArgument, "containsExactly");
                }

                // Replace with assertThat
                return replace(ctx, mi, reason, actual, isMatcherArgument, "isEqualTo");
            }

            @NotNull
            private J.MethodInvocation replace(ExecutionContext ctx, J.MethodInvocation mi, Expression reason, Expression actual, Expression isMatcherArgument, String replacement) {
                List<Expression> parameters = new ArrayList<>();
                parameters.add(actual);
                final String template;
                if (reason == null) {
                    template = "assertThat(#{any(java.lang.Object)})." +
                               replacement + "(#{any()});";
                } else {
                    template = "assertThat(#{any(java.lang.String)})" +
                               ".as(#{any(String)})." +
                               replacement + "(#{any()});";
                    parameters.add(reason);
                }
                parameters.add(isMatcherArgument);

                maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                maybeRemoveImport("org.hamcrest.MatcherAssert");
                maybeRemoveImport("org.hamcrest.Matchers.is");
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat");

                return JavaTemplate.builder(template)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), parameters.toArray());
            }
        });
    }
}
