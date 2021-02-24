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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/**
 * This is a refactoring visitor that will convert JUnit-style assertSame() to assertJ's assertThat().isSameAs().
 * <p>
 * This visitor only supports the migration of the following JUnit 5 assertSame() methods:
 *
 * <PRE>
 * assertSame(Object expected, Object actual) -> assertThat(actual).isSameAs(expected)
 * assertSame(Object expected, Object actual, String message) -> assertThat(actual).as(message).isSameAs(expected)
 * assertSame(Object expected, Object actual, Supplier<String> messageSupplier) -> assertThat(actual).withFailMessage(messageSupplier).isSameAs(expected);
 * </PRE>
 */
public class JUnitAssertSameToAssertThat extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertSameToAssertThatVisitor();
    }

    public static class AssertSameToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
        private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
        private static final String ASSERTJ_ASSERT_THAT_METHOD_NAME = "assertThat";

        /**
         * This matcher finds the junit methods that will be migrated by this visitor.
         */
        private static final MethodMatcher JUNIT_ASSERT_SAME_MATCHER = new MethodMatcher(
                JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertSame(..)"
        );

        private static final JavaParser ASSERTJ_JAVA_PARSER = JavaParser.fromJavaVersion().dependsOn(
                Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")
        ).build();

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
                        template("assertThat(#{}).isSameAs(#{});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        expected
                );
            } else {
                Expression message = args.get(2);
                // In assertJ the "as" method has a more informative error message, but doesn't accept String suppliers
                // so we're using "as" if the message is a string and "withFailMessage" if it is a supplier.
                String messageAs = TypeUtils.isString(message.getType()) ? "as" : "withFailMessage";

                method = method.withTemplate(
                        template("assertThat(#{}).#{}(#{}).isSameAs(#{});")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        messageAs,
                        message,
                        expected
                );
            }

            // Remove import for "org.junit.jupiter.api.Assertions" if no longer used.
            maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);

            // Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat".
            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_ASSERT_THAT_METHOD_NAME);

            return method;
        }
    }
}
