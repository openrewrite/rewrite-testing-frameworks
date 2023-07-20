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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;

import java.util.ArrayList;
import java.util.List;

public class FlattenAllOf extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert Hamcrest `allOf(Matcher...)` to individual `assertThat` statements";
    }

    @Override
    public String getDescription() {
        return "Convert Hamcrest `allOf(Matcher...)` to individual `assertThat` statements for easier migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>("org.hamcrest.Matchers allOf(..)"),
                new FlattenAllOfVisitor());
    }
}

@SuppressWarnings("NullableProblems")
class FlattenAllOfVisitor extends JavaVisitor<ExecutionContext> {
    private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private final MethodMatcher ALL_OF_MATCHER = new MethodMatcher("org.hamcrest.Matchers allOf(..)");

    @Override
    public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(invocation, ctx);

        List<Expression> arguments = mi.getArguments();
        Expression allOf = arguments.get(arguments.size() - 1);
        if (!ASSERT_THAT_MATCHER.matches(mi) && !ALL_OF_MATCHER.matches(allOf)) {
            return mi;
        }

        Expression reason = arguments.size() == 3 ? arguments.get(0) : null;
        Expression actual = arguments.get(arguments.size() - 2);

        // Wrap statements in a block, as JavaTemplate can only return one element
        StringBuilder blockTemplate = new StringBuilder();
        blockTemplate.append("{");
        List<Expression> parameters = new ArrayList<>();
        for (Expression matcher : ((J.MethodInvocation) allOf).getArguments()) {
            if (reason == null) {
                blockTemplate.append("assertThat(#{any(java.lang.Object)}, #{any(org.hamcrest.Matcher)});");
            } else {
                blockTemplate.append("assertThat(#{any(java.lang.String)}, #{any(java.lang.Object)}, #{any(org.hamcrest.Matcher)});");
                parameters.add(reason);
            }
            parameters.add(actual);
            parameters.add(matcher);
        }
        blockTemplate.append("}");

        JavaTemplate template = JavaTemplate.builder(blockTemplate.toString())
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hamcrest-2.2"))
                .staticImports("org.hamcrest.MatcherAssert.assertThat")
                .build();

        // Remove wrapping block and allOf import after template is applied
        doAfterVisit(new RemoveUnneededBlock().getVisitor());
        maybeRemoveImport("org.hamcrest.Matchers.allOf");
        return template.apply(getCursor(), mi.getCoordinates().replace(), parameters.toArray());
    }
}
