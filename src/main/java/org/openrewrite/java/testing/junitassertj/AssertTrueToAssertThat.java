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

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.List;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.MethodTypeBuilder.newMethodType;

/**
 * This is a refactoring visitor that will convert JUnit-style assertTrue() to assertJ's assertThat().isTrue().
 *
 * This visitor only supports the migration of the following JUnit 5 assertTrue() methods:
 *
 * <PRE>
 *     assertTrue(boolean condition) -> assertThat(condition).isTrue()
 *     assertTrue(boolean condition, String message) -> assertThat(condition).withFailMessage(message).isTrue();
 *     assertTrue(boolean condition, Supplier<String> messageSupplier) -> assertThat(condition).withFailMessage(messageSupplier).isTrue();
 * </PRE>
 *
 * Note: There are three additional method signatures in JUnit that use a BooleanSupplier for the condition. Attempts
 *       to map these signatures into assertJ's model obfuscates the original assertion. It would be possible to use a
 *       shim method to support these method signatures, however, those shims would need to exist on each compilation
 *       unit or in a shared testing utilities library.
 */
@AutoConfigure
public class AssertTrueToAssertThat extends JavaIsoRefactorVisitor {

    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
    private static final String ASSERTJ_ASSERT_THAT_METHOD_NAME = "assertThat";

    /**
     * This matcher uses a pointcut expression to find the matching junit methods that will be migrated by this visitor
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
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu) {

        maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);
        return super.visitCompilationUnit(cu);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation original = super.visitMethodInvocation(method);
        if (!JUNIT_ASSERT_TRUE_MATCHER.matches(method)) {
            return original;
        }

        List<Expression> originalArgs = original.getArgs().getArgs();
        Expression condition = originalArgs.get(0);
        Expression message = originalArgs.size() == 2 ? originalArgs.get(1):null;

        //This create the `assertThat(<EXPRESSION>)` method invocation. The type information for this invocation
        //is used to link this reference to the AddImport visitor that adds the static import for "assertThat"
        J.MethodInvocation assertSelect = new J.MethodInvocation(
                randomId(),
                null,
                null,
                J.Ident.build(randomId(), ASSERTJ_ASSERT_THAT_METHOD_NAME, JavaType.Primitive.Void, EMPTY),
                new J.MethodInvocation.Arguments(
                        randomId(),
                        Collections.singletonList(condition),
                        EMPTY
                ),
                assertThatMethodType,
                EMPTY
        );

        if (message != null && TypeUtils.isString(message.getType())) {
            //If this is the three-argument variant and the third argument is a string, chain an ".as(message)"
            assertSelect = new J.MethodInvocation(
                    randomId(),
                    assertSelect, //assertThat is the select for this method.
                    null,
                    J.Ident.build(randomId(), "as", null, EMPTY),
                    new J.MethodInvocation.Arguments(
                            randomId(),
                            Collections.singletonList(message.withPrefix("")),
                            EMPTY
                    ),
                    null,
                    EMPTY
            );
        } else if (message != null) {
            //If this is the three-argument variant and the third argument is a string supplier, chain the
            //message ".withFailMessage(message)". "as" does not support passing a string supplier (at this time)
            assertSelect = new J.MethodInvocation(
                    randomId(),
                    assertSelect, //assertThat is the select for this method.
                    null,
                    J.Ident.build(randomId(), "withFailMessage", null, EMPTY),
                    new J.MethodInvocation.Arguments(
                            randomId(),
                            Collections.singletonList(message.withPrefix("")),
                            EMPTY
                    ),
                    null,
                    EMPTY
            );
        }

        //The method that will always be returned is the "isTrue()" method using the assertSelect as the select.
        //One argument form : assertThat(<CONDITION>).isTrue();
        //Two argument form : assertThat(<CONDITION>).withFailMessage(<MESSAGE>).isTrue();
        J.MethodInvocation replacement = new J.MethodInvocation(
                randomId(),
                assertSelect,
                null,
                J.Ident.build(randomId(), "isTrue", JavaType.Primitive.Boolean, EMPTY),
                new J.MethodInvocation.Arguments(
                        randomId(),
                        Collections.emptyList(),
                        EMPTY
                ),
                null,
                format("\n")
        );

        //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
        maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_ASSERT_THAT_METHOD_NAME);

        //Format the replacement method invocation in the context of where it is called.
        andThen(new AutoFormat(replacement));
        return replacement;
    }
}
