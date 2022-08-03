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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public class JUnitAssertArrayEqualsToAssertThat extends Recipe {
    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";

    @Override
    public String getDisplayName() {
        return "JUnit `assertArrayEquals` To AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertArrayEquals()` to assertJ's `assertThat().contains()` equivalents.";
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertArrayEqualsToAssertThatVisitor();
    }

    public static class AssertArrayEqualsToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher JUNIT_ASSERT_EQUALS = new MethodMatcher(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertArrayEquals(..)");
        private static final Supplier<JavaParser> ASSERTIONS_PARSER = () -> JavaParser.fromJavaVersion()
                .dependsOn(Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")).build();

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_ASSERT_EQUALS.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();

            Expression expected = args.get(0);
            Expression actual = args.get(1);

            if (args.size() == 2) {
                method = method.withTemplate(
                        JavaTemplate.builder(this::getCursor, "assertThat(#{anyArray()}).containsExactly(#{anyArray()});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTIONS_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected
                );
            } else if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                Expression message = args.get(2);
                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        JavaTemplate.builder(this::getCursor, "assertThat(#{anyArray()}).as(#{any(String)}).containsExactly(#{anyArray()});") :
                        JavaTemplate.builder(this::getCursor, "assertThat(#{anyArray()}).as(#{any(java.util.function.Supplier)}).containsExactly(#{anyArray()});");

                method = method.withTemplate(template
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTIONS_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        message,
                        expected
                );
            } else if (args.size() == 3) {
                // assert is using floating points with a delta and no message.
                method = method.withTemplate(
                        JavaTemplate.builder(this::getCursor, "assertThat(#{anyArray()}).containsExactly(#{anyArray()}, within(#{any()}));")
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTIONS_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected,
                        args.get(2)
                );
                maybeAddImport("org.assertj.core.api.Assertions", "within");
            } else {
                // The assertEquals is using a floating point with a delta argument and a message.
                Expression message = args.get(3);
                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        JavaTemplate.builder(this::getCursor, "assertThat(#{anyArray()}).as(#{any(String)}).containsExactly(#{anyArray()}, within(#{any()}));") :
                        JavaTemplate.builder(this::getCursor, "assertThat(#{anyArray()}).as(#{any(java.util.function.Supplier)}).containsExactly(#{anyArray()}, within(#{}));");

                method = method.withTemplate(template
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTIONS_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        message,
                        expected,
                        args.get(2)
                );
                maybeAddImport("org.assertj.core.api.Assertions", "within");
            }

            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);

            return method;
        }

        /**
         * Returns true if the expression's type is either a primitive float/double or their object forms Float/Double
         *
         * @param expression The expression parsed from the original AST.
         * @return true if the type is a floating point number.
         */
        private static boolean isFloatingPointType(Expression expression) {

            JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(expression.getType());
            if (fullyQualified != null) {
                String typeName = fullyQualified.getFullyQualifiedName();
                return ("java.lang.Double".equals(typeName) || "java.lang.Float".equals(typeName));
            }

            JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
            return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
        }
    }
}
