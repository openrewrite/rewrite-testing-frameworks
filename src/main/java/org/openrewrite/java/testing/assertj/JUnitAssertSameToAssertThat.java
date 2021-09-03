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
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class JUnitAssertSameToAssertThat extends Recipe {
    private static final ThreadLocal<JavaParser> ASSERTJ_JAVA_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(
                    Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")
            ).build()
    );

    @Override
    public String getDisplayName() {
        return "JUnit `assertSame` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertSame()` to AssertJ's `assertThat().isSameAs()`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.jupiter.api.Assertions");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertSameToAssertThatVisitor();
    }

    public static class AssertSameToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher JUNIT_ASSERT_SAME_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions" + " assertSame(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_ASSERT_SAME_MATCHER.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();
            Expression expected = args.get(0);
            Expression actual = args.get(1);

            if (args.size() == 2) {
                method = method.withTemplate(
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).isSameAs(#{any()});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER::get)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected
                );
            } else {
                Expression message = args.get(2);

                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).as(#{any(String)}).isSameAs(#{any()});") :
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).withFailMessage(#{any(java.util.function.Supplier)}).isSameAs(#{any()});");

                method = method.withTemplate(template
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER::get)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        message,
                        expected
                );
            }

            maybeRemoveImport("org.junit.jupiter.api.Assertions");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");

            return method;
        }
    }
}
