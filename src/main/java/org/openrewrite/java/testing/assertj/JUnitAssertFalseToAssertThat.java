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
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/**
 * This is a refactoring visitor that will convert JUnit-style assertFalse() to assertJ's assertThat().isFalse().
 * <p>
 * This visitor only supports the migration of the following JUnit 5 assertFalse() methods:
 *
 * <PRE>
 * assertFalse(boolean condition) == assertThat(condition).isFalse()
 * assertFalse(boolean condition, String message) == assertThat(condition).as(message).isFalse();
 * assertFalse(boolean condition, Supplier<String> messageSupplier) == assertThat(condition).withFailMessage(messageSupplier).isFalse();
 * </PRE>
 * <p>
 * Note: There are three additional method signatures in JUnit that use a BooleanSupplier for the condition. Attempts
 * to map these signatures into assertJ's model obfuscates the original assertion. It would be possible to use a
 * shim method to support these method signatures, however, those shims would need to exist on each compilation
 * unit or in a shared testing utilities library.
 */
public class JUnitAssertFalseToAssertThat extends Recipe {
    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
    private static final ThreadLocal<JavaParser> ASSERTJ_JAVA_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(
                    Parser.Input.fromResource("/META-INF/rewrite/AssertJAssertions.java", "---")
            ).build()
    );

    @Override
    public String getDisplayName() {
        return "JUnit AssertFalse to AssertThat";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style assertFalse() to assertJ's assertThat().isFalse().";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertFalseToAssertThatVisitor();
    }

    public static class AssertFalseToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
        private static final String ASSERTJ_ASSERT_THAT_METHOD_NAME = "assertThat";

        /**
         * This matcher finds the junit methods that will be migrated by this visitor.
         */
        private static final MethodMatcher JUNIT_ASSERT_FALSE_MATCHER = new MethodMatcher(
                JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertFalse(boolean, ..)"
        );

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_ASSERT_FALSE_MATCHER.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();
            Expression actual = args.get(0);

            if (args.size() == 1) {
                method = method.withTemplate(
                        template("assertThat(#{}).isFalse();")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER.get())
                                .build(),
                        method.getCoordinates().replace(),
                        actual
                );
            } else {
                Expression message = args.get(1);
                String messageAs = TypeUtils.isString(message.getType()) ? "as" : "withFailMessage";

                method = method.withTemplate(
                        template("assertThat(#{}).#{}(#{}).isFalse();")
                                .staticImports("org.assertj.core.api.Assertions.assertThat")
                                .javaParser(ASSERTJ_JAVA_PARSER.get())
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
