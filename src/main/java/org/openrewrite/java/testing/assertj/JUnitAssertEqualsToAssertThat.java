/*
 * Copyright 2020 the original author or authors.
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

public class JUnitAssertEqualsToAssertThat extends Recipe {
    private static final ThreadLocal<JavaParser> ASSERTJ_JAVA_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(
                    Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")
            ).build()
    );

    @Override
    public String getDisplayName() {
        return "JUnit `assertEquals` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertEquals()` to AssertJ's `assertThat().isEqualTo()`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.jupiter.api.Assertions");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertEqualsToAssertThatVisitor();
    }

    public static class AssertEqualsToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher JUNIT_ASSERT_EQUALS = new MethodMatcher("org.junit.jupiter.api.Assertions" + " assertEquals(..)");

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
                        template("assertThat(#{any()}).isEqualTo(#{any()});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER::get)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected
                );
            } else if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                Expression message = args.get(2);
                // In assertJ the "as" method has a more informative error message, but doesn't accept String suppliers
                // so we're using "as" if the message is a string and "withFailMessage" if it is a supplier.
                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        template("assertThat(#{any()}).as(#{any(String)}).isEqualTo(#{any()});") :
                        template("assertThat(#{any()}).withFailMessage(#{any(java.util.function.Supplier)}).isEqualTo(#{any()});");

                method = method.withTemplate(template
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER::get)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        message,
                        expected
                );
            } else if (args.size() == 3) {
                method = method.withTemplate(
                        template("assertThat(#{any()}).isCloseTo(#{any()}, within(#{any()}));")
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTJ_JAVA_PARSER::get)
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

                // If the message is a string use "as", if it is a supplier use "withFailMessage"
                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        template("assertThat(#{any()}).as(#{any(String)}).isCloseTo(#{any()}, within(#{any()}));") :
                        template("assertThat(#{any()}).withFailMessage(#{any(java.util.function.Supplier)}).isCloseTo(#{any()}, within(#{any()}));");

                method = method.withTemplate(template
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTJ_JAVA_PARSER::get)
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
            maybeRemoveImport("org.junit.jupiter.api.Assertions");

            return method;
        }

        private static boolean isFloatingPointType(Expression expression) {

            JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(expression.getType());
            if (fullyQualified != null) {
                String typeName = fullyQualified.getFullyQualifiedName();
                return (typeName.equals("java.lang.Double") || typeName.equals("java.lang.Float"));
            }

            JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
            return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
        }
    }
}
