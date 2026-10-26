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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class HamcrestIsMatcherToAssertJ extends Recipe {
    @Getter
    final String displayName = "Migrate Hamcrest `is(Object)` to AssertJ";

    @Getter
    final String description = "Migrate Hamcrest `is(Object)` to AssertJ `Assertions.assertThat(..)`.";

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher IS_MATCHER = new MethodMatcher("org.hamcrest.*Matchers is(..)");
    private static final MethodMatcher IS_WITH_NESTED_MATCHER = new MethodMatcher("org.hamcrest.*Matchers is(org.hamcrest.Matcher)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_THAT_MATCHER.matches(mi)) {
                    return mi;
                }

                List<Expression> args = mi.getArguments();
                Expression reasonArgument = args.size() == 3 ? args.get(0) : null;
                Expression actualArgument = args.get(args.size() - 2);
                Expression matcherArgument = args.get(args.size() - 1);

                // Only handle is() matcher, but not is(Matcher) which nests another matcher
                if (!IS_MATCHER.matches(matcherArgument) || IS_WITH_NESTED_MATCHER.matches(matcherArgument)) {
                    return mi;
                }

                // Choose assertion based on whether actual argument is an array
                boolean isArray = TypeUtils.asArray(actualArgument.getType()) != null;

                J.MethodInvocation isInvocation = (J.MethodInvocation) matcherArgument;
                Expression expectedArgument = isInvocation.getArguments().get(0);

                maybeRemoveImport("org.hamcrest.Matchers.is");
                maybeRemoveImport("org.hamcrest.CoreMatchers.is");
                maybeRemoveImport("org.hamcrest.MatcherAssert");
                maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat");

                String reason = reasonArgument != null ? ".as(#{any(String)})" : "";
                // Using `#{any(Object)}` and `#{any(Object[])}` here as the actual types could be from the source path
                String template = isArray ?
                        "assertThat(#{any(Object[])})" + reason + ".containsExactly(#{any(Object[])})" :
                        "assertThat(#{any(Object)})" + reason + ".isEqualTo(#{any(Object)})";

                Object[] templateArgs = reasonArgument != null ?
                        new Object[]{actualArgument, reasonArgument, expectedArgument} :
                        new Object[]{actualArgument, expectedArgument};

                return JavaTemplate.builder(template)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), templateArgs);
            }
        });
    }
}
