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
import static org.openrewrite.java.tree.MethodTypeBuilder.newMethodType;

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
            if (!JUNIT_ASSERT_TRUE_MATCHER.matches(method)) {
                return original;
            }

            List<Expression> originalArgs = original.getArguments();
            Expression condition = originalArgs.get(0);
            Expression message = originalArgs.size() == 2 ? originalArgs.get(1) : null;

            //This create the `assertThat(<EXPRESSION>)` method invocation. The type information for this invocation
            //is used to link this reference to the AddImport visitor that adds the static import for "assertThat"
            J.MethodInvocation assertSelect = new J.MethodInvocation(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    null,
                    null,
                    J.Identifier.build(randomId(), ASSERTJ_ASSERT_THAT_METHOD_NAME, JavaType.Primitive.Void),
                    JContainer.build(
                            Collections.singletonList(JRightPadded.build(condition))
                    ),
                    assertThatMethodType
            );

            // In assertJ the "as" method has a more informative error message, but doesn't accept String suppliers
            // so we're using "as" if the message is a string and "withFailMessage" if it is a supplier.
            if (message != null && TypeUtils.isString(message.getType())) {
                //If this is the three-argument variant and the third argument is a string
                assertSelect = new J.MethodInvocation(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        JRightPadded.build(assertSelect), //assertThat is the select for this method.
                        null,
                        J.Identifier.build(randomId(), "as", null),
                        JContainer.build(
                                Collections.singletonList(JRightPadded.build(message))
                        ),
                        null
                );
            } else if (message != null) {
                //If this is the three-argument variant and the third argument is a string supplier
                assertSelect = new J.MethodInvocation(
                        randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        JRightPadded.build(assertSelect), //assertThat is the select for this method.
                        null,
                        J.Identifier.build(randomId(), "withFailMessage", null),
                        JContainer.build(
                                Collections.singletonList(JRightPadded.build(message))
                        ),
                        null
                );
            }

            //The method that will always be returned is the "isTrue()" method using the assertSelect as the select.
            //One argument form : assertThat(<CONDITION>).isTrue();
            //Two argument form : assertThat(<CONDITION>).withFailMessage(<MESSAGE>).isTrue();
            J.MethodInvocation replacement = new J.MethodInvocation(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    JRightPadded.build(assertSelect),
                    null,
                    J.Identifier.build(randomId(), "isTrue", JavaType.Primitive.Boolean),
                    JContainer.build(
                            Collections.emptyList()
                    ),
                    null
            );

            //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_ASSERT_THAT_METHOD_NAME);

            //And if there are no longer references to the JUnit assertions class, we can remove the import.
            maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);

            //Format the replacement method invocation in the context of where it is called.
            return (J.MethodInvocation) new AutoFormatVisitor<ExecutionContext>().visit(replacement, ctx, getCursor());
        }
    }
}
