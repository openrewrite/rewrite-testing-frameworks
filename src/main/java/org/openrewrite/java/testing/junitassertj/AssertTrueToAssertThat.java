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
package org.openrewrite.java.testing.junitassertj;


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
 * This is a refactoring visitor that will convert JUnit-style assertTrue() to assertJ's assertThat().isTrue().
 * <p>
 * This visitor only supports the migration of the following JUnit 5 assertTrue() methods:
 *
 * <PRE>
 * assertTrue(boolean condition) -> assertThat(condition).isTrue()
 * assertTrue(boolean condition, String message) -> assertThat(condition).withFailMessage(message).isTrue();
 * assertTrue(boolean condition, Supplier<String> messageSupplier) -> assertThat(condition).withFailMessage(messageSupplier).isTrue();
 * </PRE>
 * <p>
 * Note: There are three additional method signatures in JUnit that use a BooleanSupplier for the condition. Attempts
 * to map these signatures into assertJ's model obfuscates the original assertion. It would be possible to use a
 * shim method to support these method signatures, however, those shims would need to exist on each compilation
 * unit or in a shared testing utilities library.
 */
public class AssertTrueToAssertThat extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertTrueToAssertThatVisitor();
    }

    public static class AssertTrueToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
        private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
        private static final String ASSERTJ_ASSERT_THAT_METHOD_NAME = "assertThat";

        /**
         * This matcher finds the junit methods that will be migrated by this visitor.
         */
        private static final MethodMatcher JUNIT_ASSERT_TRUE_MATCHER = new MethodMatcher(
                JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertTrue(boolean, ..)"
        );

        private static final JavaParser ASSERTJ_JAVA_PARSER = JavaParser.fromJavaVersion().dependsOn(
                Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")
        ).build();

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_ASSERT_TRUE_MATCHER.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();
            Expression actual = args.get(0);

            if (args.size() == 1) {
                method = method.withTemplate(
                        template("assertThat(#{}).isTrue();")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual
                );
            } else {
                Expression message = args.get(1);

                // In assertJ the "as" method has a more informative error message, but doesn't accept String suppliers
                // so we're using "as" if the message is a string and "withFailMessage" if it is a supplier.
                String messageAs = TypeUtils.isString(message.getType()) ? "as" : "withFailMessage";

                method = method.withTemplate(
                        template("assertThat(#{}).#{}(#{}).isTrue();")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER)
                                .build(),
                        method.getCoordinates().replace(),
                        actual,
                        messageAs,
                        message
                );
            }

            //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_ASSERT_THAT_METHOD_NAME);

            //And if there are no longer references to the JUnit assertions class, we can remove the import.
            maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);

            return method;
        }
    }
}
