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
import java.util.stream.Collectors;

@SuppressWarnings("NullableProblems")
public class HamcrestAnyOfToAssertJ extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate `anyOf` Hamcrest Matcher to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate the `anyOf` Hamcrest Matcher to AssertJ's `satisfiesAnyOf` assertion.";
    }

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher ANY_OF_MATCHER = new MethodMatcher("org.hamcrest.Matchers anyOf(..)");
    private static final MethodMatcher ALL_OF_MATCHER = new MethodMatcher("org.hamcrest.Matchers allOf(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesMethod<>(ANY_OF_MATCHER),
                new UsesMethod<>(ALL_OF_MATCHER)
        ), new AnyOfToAssertJVisitor());
    }

    private static class AnyOfToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);
            List<Expression> arguments = mi.getArguments();
            Expression anyOfExpression = arguments.get(arguments.size() - 1);
            String methodName = ALL_OF_MATCHER.matches(anyOfExpression) ? "satisfies" : "satisfiesAnyOf";
            if (!ASSERT_THAT_MATCHER.matches(mi) && (!ANY_OF_MATCHER.matches(anyOfExpression) || !ALL_OF_MATCHER.matches(anyOfExpression))) {
                return mi;
            }

            // Skip anyOf(Iterable)
            List<Expression> anyOfArguments = ((J.MethodInvocation) anyOfExpression).getArguments();
            if (TypeUtils.isAssignableTo("java.lang.Iterable", anyOfArguments.get(0).getType())) {
                return mi;
            }

            StringBuilder template = new StringBuilder();
            List<Expression> parameters = new ArrayList<>();

            // assertThat(actual)
            template.append("assertThat(#{any()})\n");
            parameters.add(arguments.get(arguments.size() - 2));

            // .as("...")
            if (arguments.size() == 3) {
                template.append(".as(#{any(java.lang.String)})\n");
                parameters.add(arguments.get(0));
            }

            // .satisfiesAnyOf(...) or .satisfies(...)
            String methodCall = String.format(".%s(\n", methodName);
            template.append(methodCall);
            template.append(anyOfArguments.stream()
                    .map(arg -> "arg -> assertThat(arg, #{any()})")
                    .collect(Collectors.joining(",\n")));
            parameters.addAll(anyOfArguments);
            template.append("\n);");

            maybeRemoveImport("org.hamcrest.Matchers.anyOf");
            maybeRemoveImport("org.hamcrest.Matchers.allOf");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            return JavaTemplate.builder(template.toString())
                    .contextSensitive()
                    .staticImports("org.assertj.core.api.Assertions.assertThat")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                            "assertj-core-3.24",
                            "hamcrest-2.2",
                            "junit-jupiter-api-5.9"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), parameters.toArray());
        }
    }
}
