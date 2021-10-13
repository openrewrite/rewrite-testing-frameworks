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

import java.util.List;
import java.util.function.Supplier;

public class JUnitAssertNotEqualsToAssertThat extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit `assertNotEquals` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertNotEquals()` to AssertJ's `assertThat().isNotEqualTo()`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.jupiter.api.Assertions");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertNotEqualsToAssertThatVisitor();
    }

    public static class AssertNotEqualsToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final Supplier<JavaParser> ASSERTJ_JAVA_PARSER = () -> JavaParser.fromJavaVersion()
                .dependsOn(Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")).build();
        private static final MethodMatcher JUNIT_ASSERT_EQUALS = new MethodMatcher("org.junit.jupiter.api.Assertions" + " assertNotEquals(..)");

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
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).isNotEqualTo(#{any()});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected
                );
            } else if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                Expression message = args.get(2);

                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).as(#{any(String)}).isNotEqualTo(#{any()});") :
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).withFailMessage(#{any(java.util.function.Supplier)}).isNotEqualTo(#{any()});");


                method = method.withTemplate(template
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        message,
                        expected
                );
            } else if (args.size() == 3) {
                method = method.withTemplate(
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).isNotCloseTo(#{any()}, within(#{any()}));")
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected,
                        args.get(2)
                );
                maybeAddImport("org.assertj.core.api.Assertions", "within");
            } else {
                Expression message = args.get(3);

                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).as(#{any(String)}).isNotCloseTo(#{any()}, within(#{any()}));") :
                        JavaTemplate.builder(this::getCursor, "assertThat(#{any()}).withFailMessage(#{any(java.util.function.Supplier)}).isNotCloseTo(#{any()}, within(#{any()}));");

                method = method.withTemplate(template
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        message,
                        expected,
                        args.get(2)
                );

                maybeAddImport("org.assertj.core.api.Assertions", "within");
            }

            //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            //And if there are no longer references to the JUnit assertions class, we can remove the import.
            maybeRemoveImport("org.junit.jupiter.api.Assertions");

            return method;
        }

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
