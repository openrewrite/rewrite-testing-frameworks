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
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.List;

import static org.openrewrite.java.tree.MethodTypeBuilder.newMethodType;

/**
 * This is a refactoring visitor that will convert JUnit-style assertNotEquals() to assertJ's assertThat().isNotEqualTo().
 *
 * This visitor has to convert a surprisingly large number (93 methods) of JUnit's assertEquals to assertThat().
 *
 * <PRE>
 *  Two parameter variants:
 *
 *  assertNotEquals(expected,actual) -> assertThat(actual).isNotEqualTo(expected)
 *
 *  Three parameter variant where the third argument is a String:
 *
 *  assertNotEquals(expected, actual, "message") -> assertThat(actual).as("message").isNotEqualTo(expected)
 *
 *  Three parameter variant where the third argument is a String Supplier (there is no overloaded "as" method that takes a supplier):
 *
 *  assertNotEquals(expected, actual, "message") -> assertThat(actual).withFailMessage("message").isNotEqualTo(expected)
 *
 *  Three parameter variant where args are all floating point numbers.
 *
 *  assertEquals(expected, actual, delta) -> assertThat(actual).isCloseTo(expected, within(delta));
 *
 *  Four parameter variant when comparing floating point numbers with a delta and a message:
 *
 *  assertEquals(expected, actual, delta, "message") -> assertThat(actual).withFailureMessage("message").isCloseTo(expected, within(delta));
 *
 * </PRE>
 */
@AutoConfigure
public class AssertNotEqualsToAssertThat extends JavaIsoRefactorVisitor {

    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";

    private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";
    private static final String ASSERTJ_ASSERT_THAT_METHOD_NAME = "assertThat";
    private static final String ASSERTJ_WITHIN_METHOD_NAME = "within";

    /**
     * This matcher uses a pointcut expression to find the matching junit methods that will be migrated by this visitor
     */
    private static final MethodMatcher JUNIT_ASSERT_EQUALS_MATCHER = new MethodMatcher(
            JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertNotEquals(..)"
    );

    private static final JavaType ASSERTJ_ASSERTIONS_WILDCARD_STATIC_IMPORT = newMethodType()
            .declaringClass("org.assertj.core.api.Assertions")
            .flags(Flag.Public, Flag.Static)
            .name("*")
            .build();

    public AssertNotEqualsToAssertThat() {
        setCursoringOn();
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method) {

        J.MethodInvocation original = super.visitMethodInvocation(method);
        if (!JUNIT_ASSERT_EQUALS_MATCHER.matches(method)) {
            return original;
        }

        List<Expression> originalArgs = original.getArgs().getArgs();

        Expression expected = originalArgs.get(0);
        Expression actual = originalArgs.get(1);

        J.MethodInvocation replacement;
        if (originalArgs.size() == 2) {
            //assertThat(actual).isNotEqualTo(expected)
            replacement = assertSimple(actual, expected);
        } else if (originalArgs.size() == 3 && !isFloatingPointType(originalArgs.get(2))) {
            //assertThat(actual).as(message).isNotEqualTo(expected)
            replacement = assertWithMessage(actual, expected, originalArgs.get(2));
        } else if (originalArgs.size() == 3) {
            //assertThat(actual).isNotCloseTo(expected, within(delta))
            replacement = assertFloatingPointDelta(actual, expected, originalArgs.get(2));
            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_WITHIN_METHOD_NAME);

        } else {
            //assertThat(actual).as(message).isNotCloseTo(expected, within(delta))
            replacement = assertFloatingPointDeltaWithMessage(actual, expected, originalArgs.get(2), originalArgs.get(3));
            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_WITHIN_METHOD_NAME);
        }

        //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
        maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, ASSERTJ_ASSERT_THAT_METHOD_NAME);
        //And if there are no longer references to the assertJ class, we can remove the import.
        maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);

        //Format the replacement method invocation in the context of where it is called.
        andThen(new AutoFormat(replacement));
        return replacement;
    }

    private J.MethodInvocation assertSimple(Expression actual, Expression expected) {

        List<J.MethodInvocation> statements = treeBuilder.buildSnippet(getCursor(),
                String.format("assertThat(%s).isNotEqualTo(%s);", actual.printTrimmed(), expected.printTrimmed()),
                ASSERTJ_ASSERTIONS_WILDCARD_STATIC_IMPORT
        );
        return statements.get(0);
    }

    private J.MethodInvocation assertWithMessage(Expression actual, Expression expected, Expression message) {

        //If the message is a string use "as", if it is a supplier use "withFailMessage"
        String messageAs = TypeUtils.isString(message.getType())?"as":"withFailMessage";

        List<J.MethodInvocation> statements = treeBuilder.buildSnippet(getCursor(),
                String.format("assertThat(%s).%s(%s).isNotEqualTo(%s);",
                        actual.printTrimmed(), messageAs, message.printTrimmed(), expected.printTrimmed()),
                ASSERTJ_ASSERTIONS_WILDCARD_STATIC_IMPORT
        );
        return statements.get(0);
    }

    private J.MethodInvocation assertFloatingPointDelta(Expression actual, Expression expected, Expression delta) {
        List<J.MethodInvocation> statements = treeBuilder.buildSnippet(getCursor(),
                String.format("assertThat(%s).isNotCloseTo(%s, within(%s));",
                        actual.printTrimmed(), expected.printTrimmed(), delta.printTrimmed()),
                ASSERTJ_ASSERTIONS_WILDCARD_STATIC_IMPORT
        );
        return statements.get(0);
    }

    private J.MethodInvocation assertFloatingPointDeltaWithMessage(Expression actual, Expression expected,
            Expression delta, Expression message) {

        //If the message is a string use "as", if it is a supplier use "withFailMessage"
        String messageAs = TypeUtils.isString(message.getType())?"as":"withFailMessage";

        List<J.MethodInvocation> statements = treeBuilder.buildSnippet(getCursor(),
                String.format("assertThat(%s).%s(%s).isNotCloseTo(%s, within(%s));",
                        actual.printTrimmed(), messageAs, message.printTrimmed(), expected.printTrimmed(), delta.printTrimmed()),
                ASSERTJ_ASSERTIONS_WILDCARD_STATIC_IMPORT
        );
        return statements.get(0);
    }

    /**
     * Returns true if the expression's type is either a primitive float/double or their object forms Float/Double
     *
     * @param expression The expression parsed from the original AST.
     * @return true if the type is a floating point number.
     */
    private boolean isFloatingPointType(Expression expression) {

        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(expression.getType());
        if (fullyQualified != null) {
            String typeName = fullyQualified.getFullyQualifiedName();
            return (typeName.equals("java.lang.Double") || typeName.equals("java.lang.Float"));
        }

        JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
        return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
    }

}
