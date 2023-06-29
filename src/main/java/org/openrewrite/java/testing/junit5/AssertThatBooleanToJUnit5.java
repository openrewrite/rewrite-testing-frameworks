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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class AssertThatBooleanToJUnit5 extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Hamcrest `assertThat(boolean, Matcher)` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Replace Hamcrest `assertThat(String, boolean)` with AssertJ `assertThat(boolean).as(String).isTrue()`.";
    }

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(String, boolean)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (ASSERT_THAT_MATCHER.matches(mi)) {
                    Expression reasonArgument = mi.getArguments().get(0);
                    Expression booleanArgument = mi.getArguments().get(1);
                    maybeAddImport("org.junit.jupiter.api.Assertions", "assertTrue");
                    maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                    return JavaTemplate.builder("assertTrue(#{any(boolean)}, #{any(String)})")
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9"))
                      .staticImports("org.junit.jupiter.api.Assertions.assertTrue")
                      .build()
                      .apply(getCursor(), mi.getCoordinates().replace(), booleanArgument, reasonArgument);
                }
                return mi;
            }
        };
    }
}