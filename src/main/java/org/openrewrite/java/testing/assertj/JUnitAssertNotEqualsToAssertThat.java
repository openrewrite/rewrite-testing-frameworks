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
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/**
 * This is a refactoring visitor that will convert JUnit-style assertNotEquals() to assertJ's assertThat().isNotEqualTo().
 * <p>
 * This visitor has to convert a surprisingly large number (93 methods) of JUnit's assertEquals to assertThat().
 *
 * <PRE>
 * Two parameter variants:
 * <p>
 * assertNotEquals(expected,actual) == assertThat(actual).isNotEqualTo(expected)
 * <p>
 * Three parameter variant where the third argument is a String:
 * <p>
 * assertNotEquals(expected, actual, "message") == assertThat(actual).as("message").isNotEqualTo(expected)
 * <p>
 * Three parameter variant where the third argument is a String Supplier (there is no overloaded "as" method that takes a supplier):
 * <p>
 * assertNotEquals(expected, actual, "message") == assertThat(actual).withFailMessage("message").isNotEqualTo(expected)
 * <p>
 * Three parameter variant where args are all floating point numbers.
 * <p>
 * assertEquals(expected, actual, delta) == assertThat(actual).isCloseTo(expected, within(delta));
 * <p>
 * Four parameter variant when comparing floating point numbers with a delta and a message:
 * <p>
 * assertEquals(expected, actual, delta, "message") == assertThat(actual).withFailureMessage("message").isCloseTo(expected, within(delta));
 *
 * </PRE>
 */
public class JUnitAssertNotEqualsToAssertThat extends Recipe {

    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
    private static final ThreadLocal<JavaParser> ASSERTJ_JAVA_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(
                    Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")
            ).build()
    );

    @Override
    public String getDisplayName() {
        return "JUnit AssertNotEquals to AssertThat";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style assertNotEquals() to assertJ's assertThat().isNotEqualTo().";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertNotEqualsToAssertThatVisitor();
    }

    public static class AssertNotEqualsToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
        private static final String ASSERTJ_ASSERT_THAT_METHOD_NAME = "assertThat";
        private static final String ASSERTJ_WITHIN_METHOD_NAME = "within";

        /**
         * This matcher finds the junit methods that will be migrated by this visitor.
         */
        private static final MethodMatcher JUNIT_ASSERT_EQUALS_MATCHER = new MethodMatcher(
                JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertNotEquals(..)"
        );

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {

            if (!JUNIT_ASSERT_EQUALS_MATCHER.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();

            Expression expected = args.get(0);
            Expression actual = args.get(1);

            if (args.size() == 2) {
                method = method.withTemplate(
                        template("assertThat(#{}).isNotEqualTo(#{});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER.get())
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected
                );
            } else if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                Expression message = args.get(2);
                // In assertJ the "as" method has a more informative error message, but doesn't accept String suppliers
                // so we're using "as" if the message is a string and "withFailMessage" if it is a supplier.
                String messageAs = TypeUtils.isString(message.getType()) ? "as" : "withFailMessage";
                method = method.withTemplate(
                        template("assertThat(#{}).#{}(#{}).isNotEqualTo(#{});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER.get())
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        messageAs,
                        message,
                        expected
                );
            } else if (args.size() == 3) {
                method = method.withTemplate(
                        template("assertThat(#{}).isNotCloseTo(#{}, within(#{}));")
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTJ_JAVA_PARSER.get())
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected,
                        args.get(2)
                );
                maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_WITHIN_METHOD_NAME);
            } else {
                Expression message = args.get(3);
                //If the message is a string use "as", if it is a supplier use "withFailMessage"
                String messageAs = TypeUtils.isString(message.getType()) ? "as" : "withFailMessage";

                method = method.withTemplate(
                        template("assertThat(#{}).#{}(#{}).isNotCloseTo(#{}, within(#{}));")
                                .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                                .javaParser(ASSERTJ_JAVA_PARSER.get())
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        messageAs,
                        message,
                        expected,
                        args.get(2)
                );

                maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_WITHIN_METHOD_NAME);
            }

            //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_ASSERT_THAT_METHOD_NAME);
            //And if there are no longer references to the JUnit assertions class, we can remove the import.
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
                return (typeName.equals("java.lang.Double") || typeName.equals("java.lang.Float"));
            }

            JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
            return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
        }

    }
}
