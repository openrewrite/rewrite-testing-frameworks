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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;

import static org.openrewrite.Tree.randomId;

/**
 * This is a refactoring visitor that will convert JUnit-style assertNull() to assertJ's assertThat().isNull().
 * <p>
 * This visitor only supports the migration of the following JUnit 5 assertNull() methods:
 *
 * <PRE>
 * assertNull(Object actual) -> assertThat(condition).isNull()
 * assertNull(Object actual, String message) -> assertThat(condition).as(message).isNull();
 * assertNull(Object actual, Supplier<String> messageSupplier) -> assertThat(condition).withFailMessage(messageSupplier).isNull();
 * </PRE>
 */
public class AssertNullToAssertThat extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertNullToAssertThatVisitor();
    }

    public static class AssertNullToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
        private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
        private static final String ASSERTJ_ASSERT_THAT_METHOD_NAME = "assertThat";

        /**
         * This matcher finds the junit methods that will be migrated by this visitor.
         */
        private static final MethodMatcher JUNIT_ASSERT_NULL_MATCHER = new MethodMatcher(
                JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertNull(..)"
        );

        private static final JavaType.Method assertThatMethodType = newMethodType()
                .declaringClass(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME)
                .flags(Flag.Public, Flag.Static)
                .returnType("org.assertj.core.api.AbstractBooleanAssert")
                .name(ASSERTJ_ASSERT_THAT_METHOD_NAME)
                .parameter(JavaType.Primitive.Boolean, "arg1")
                .build();

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation original = super.visitMethodInvocation(method, ctx);
            if (!JUNIT_ASSERT_NULL_MATCHER.matches(method)) {
                return original;
            }

            List<Expression> originalArgs = original.getArgs();
            Expression objToCheck = originalArgs.get(0);
            Expression message = originalArgs.size() == 2 ? originalArgs.get(1) : null;

            // This creates the `assertThat(<EXPRESSION>)` method invocation. Without the type information for this invocation,
            // the AddImport visitor won't add a static import for "assertThat" (assuming it doesn't already exist)
            // because it won't be able to find a reference to the type.
            J.MethodInvocation assertSelect = new J.MethodInvocation(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    null,
                    null,
                    J.Ident.build(randomId(), ASSERTJ_ASSERT_THAT_METHOD_NAME, JavaType.Primitive.Void),
                    JContainer.build(
                            Collections.singletonList(JRightPadded.build(objToCheck))
                    ),
                    assertThatMethodType
            );

            // If the assertNull is the two-argument variant, we need to maintain the message via a chained method
            // call to "as"/"withFailMessage". The message may be a String or Supplier<String>.
            if (message != null) {
                // In assertJ the "as" method has a more informative error message, but doesn't accept String suppliers
                // so we're using "as" if the message is a string and "withFailMessage" if it is a supplier.
                String messageAs = TypeUtils.isString(message.getType()) ? "as" : "withFailMessage";

                assertSelect = new J.MethodInvocation(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        JRightPadded.build(assertSelect), // assertThat is the select for this method.
                        null,
                        J.Ident.build(randomId(), messageAs, null),
                        JContainer.build(
                                Collections.singletonList(JRightPadded.build(message))
                        ),
                        null
                );
            }

            // This will always return the "isNull()" method using assertSelect as the select.
            J.MethodInvocation replacement = new J.MethodInvocation(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    JRightPadded.build(assertSelect),
                    null,
                    J.Ident.build(randomId(), "isNull", JavaType.Primitive.Boolean),
                    JContainer.build(
                            Collections.emptyList()
                    ),
                    null
            );
            // Remove import for "org.junit.jupiter.api.Assertions" if no longer used.
            maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);

            // Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat".
            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_ASSERT_THAT_METHOD_NAME);

            // Format the replacement method invocation in the context of where it is called.
            return (J.MethodInvocation) new AutoFormatVisitor<ExecutionContext>().visit(replacement, ctx, getCursor());
        }
    }
}
