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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ANY_OF_MATCHER), new AnyOfToAssertJVisitor());
    }

    private static class AnyOfToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);
            List<Expression> arguments = mi.getArguments();
            if (!ASSERT_THAT_MATCHER.matches(mi) || !ANY_OF_MATCHER.matches(arguments.get(arguments.size() - 1))) {
                return mi;
            }

            Expression actual = arguments.get(arguments.size() - 2);

            List<Expression> parameters = new ArrayList<>();
            StringBuilder template = new StringBuilder();

            parameters.add(actual);

            if (arguments.size() == 3) {
                template.append("assertThat(#{any()})\n.as(#{any(java.lang.String)})\n.satisfiesAnyOf(");
                parameters.add(arguments.get(0));
            }else {
                template.append("assertThat(#{any()}).satisfiesAnyOf(");
            }

            J.MethodInvocation anyOf = ((J.MethodInvocation) arguments.get(arguments.size() - 1));
            for (Expression exp : anyOf.getArguments()) {
                template.append("\narg -> assertThat(arg, #{any()}),");
                parameters.add(exp);
            }
            template.deleteCharAt(template.length() - 1);
            template.append("\n);");

            JavaTemplate fullTemplate = JavaTemplate.builder(template.toString())
                    .contextSensitive()
                    .staticImports("org.assertj.core.api.Assertions.assertThat")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                            "assertj-core-3.24",
                            "hamcrest-2.2",
                            "junit-jupiter-api-5.9"))
                    .build();

            maybeRemoveImport("org.hamcrest.Matchers.anyOf");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            return fullTemplate.apply(getCursor(), mi.getCoordinates().replace(), parameters.toArray());
        }
    }
}
