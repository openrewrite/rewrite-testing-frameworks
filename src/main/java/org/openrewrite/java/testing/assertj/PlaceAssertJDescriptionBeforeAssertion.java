/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Loop;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class PlaceAssertJDescriptionBeforeAssertion extends Recipe {

    private static final MethodMatcher AS = new MethodMatcher("org.assertj.core.api.Descriptable as(..)", true);
    private static final MethodMatcher DESCRIBED_AS = new MethodMatcher("org.assertj.core.api.Descriptable describedAs(..)", true);
    private static final MethodMatcher WITH_FAIL_MESSAGE = new MethodMatcher("org.assertj.core.api.AbstractAssert withFailMessage(..)", true);
    private static final MethodMatcher OVERRIDING_ERROR_MESSAGE = new MethodMatcher("org.assertj.core.api.AbstractAssert overridingErrorMessage(..)", true);

    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api..* assertThat*(..)");
    private static final MethodMatcher THEN = new MethodMatcher("org.assertj.core.api..* then*(..)");

    @Getter
    final String displayName = "Place AssertJ descriptions and failure messages before the assertion";

    @Getter
    final String description = "AssertJ only applies `as(..)`, `describedAs(..)`, `withFailMessage(..)` and `overridingErrorMessage(..)` to assertions that run after them, so when they come last in a chain they are silently ignored. This moves them to directly after the `assertThat(..)` or `then(..)` entry point. For `assertThatThrownBy(..)` the check that something was thrown has already run by then, so the moved message only applies to the chained checks such as `isInstanceOf(..)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(AS),
                        new UsesMethod<>(DESCRIBED_AS),
                        new UsesMethod<>(WITH_FAIL_MESSAGE),
                        new UsesMethod<>(OVERRIDING_ERROR_MESSAGE)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!isMessage(mi) || !discardsResult(getCursor().getParentTreeCursor().getValue())) {
                            return mi;
                        }

                        List<J.MethodInvocation> messages = new ArrayList<>();
                        Expression current = mi;
                        while (isMessage(current)) {
                            J.MethodInvocation message = (J.MethodInvocation) current;
                            messages.add(0, message);
                            current = message.getSelect();
                        }
                        List<J.MethodInvocation> assertions = new ArrayList<>();
                        while (current instanceof J.MethodInvocation && !isEntry(current)) {
                            J.MethodInvocation assertion = (J.MethodInvocation) current;
                            assertions.add(0, assertion);
                            current = assertion.getSelect();
                        }
                        if (assertions.isEmpty() || !isEntry(current) || alreadySetInChain(messages, assertions)) {
                            return mi;
                        }

                        J.MethodInvocation entry = (J.MethodInvocation) current;
                        if (TypeUtils.isOfClassType(entry.getType(), "org.assertj.core.api.ThrowableTypeAssert")) {
                            if (!keepsAssertType(assertions.subList(1, assertions.size()), assertions.get(0).getType())) {
                                return mi;
                            }
                            for (int i = 0; i < messages.size(); i++) {
                                J.MethodInvocation onTypeAssert = resolveOn(messages.get(i), entry.getType());
                                if (onTypeAssert == null) {
                                    return mi;
                                }
                                messages.set(i, onTypeAssert);
                            }
                        } else if (!keepsAssertType(assertions, entry.getType())) {
                            return mi;
                        }

                        return moveMessagesAfterEntry(mi, entry, messages, assertions);
                    }
                }
        );
    }

    private static boolean discardsResult(Object parent) {
        if (parent instanceof J.Lambda) {
            return returnsVoid(TypeUtils.asFullyQualified(((J.Lambda) parent).getType()));
        }
        return parent instanceof J.Block || parent instanceof J.If || parent instanceof J.If.Else || parent instanceof Loop || parent instanceof J.Label || parent instanceof J.Case && ((J.Case) parent).getType() == J.Case.Type.Statement;
    }

    private static boolean returnsVoid(JavaType.@Nullable FullyQualified functionalInterface) {
        if (functionalInterface == null) {
            return false;
        }
        for (JavaType.Method method : functionalInterface.getMethods()) {
            if (method.hasFlags(Flag.Abstract) && !method.hasFlags(Flag.Default)) {
                return method.getReturnType() == JavaType.Primitive.Void;
            }
        }
        for (JavaType.FullyQualified superInterface : functionalInterface.getInterfaces()) {
            if (returnsVoid(superInterface)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMessage(@Nullable Expression expression) {
        return isDescription(expression) || isFailMessage(expression);
    }

    private static boolean isDescription(@Nullable Expression expression) {
        return AS.matches(expression) || DESCRIBED_AS.matches(expression);
    }

    private static boolean isFailMessage(@Nullable Expression expression) {
        return WITH_FAIL_MESSAGE.matches(expression) || OVERRIDING_ERROR_MESSAGE.matches(expression);
    }

    // A description or failure message set earlier in the chain would override the moved one, so moving it achieves nothing
    private static boolean alreadySetInChain(List<J.MethodInvocation> messages, List<J.MethodInvocation> assertions) {
        boolean describedInChain = false;
        boolean failMessageInChain = false;
        for (J.MethodInvocation assertion : assertions) {
            describedInChain |= isDescription(assertion);
            failMessageInChain |= isFailMessage(assertion);
        }
        for (J.MethodInvocation message : messages) {
            if (describedInChain && isDescription(message) || failMessageInChain && isFailMessage(message)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEntry(@Nullable Expression expression) {
        return ASSERT_THAT.matches(expression) || THEN.matches(expression);
    }

    private static boolean keepsAssertType(List<J.MethodInvocation> calls, @Nullable JavaType assertType) {
        for (J.MethodInvocation call : calls) {
            if (call.getMethodType() == null || !isSameAssertType(call.getType(), assertType) || takesConsumer(call.getMethodType())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSameAssertType(@Nullable JavaType type, @Nullable JavaType assertType) {
        if (type == null || assertType == null) {
            return false;
        }
        if (type instanceof JavaType.GenericTypeVariable) {
            // A capture of `SELF`, as returned when the entry point is declared to return a wildcard type like `AbstractIntegerAssert<?>`
            List<JavaType> bounds = ((JavaType.GenericTypeVariable) type).getBounds();
            JavaType.FullyQualified assertClass = TypeUtils.asFullyQualified(assertType);
            return bounds.size() == 1 && assertClass != null && TypeUtils.isOfClassType(bounds.get(0), assertClass.getFullyQualifiedName());
        }
        return TypeUtils.isOfType(type, assertType);
    }

    // `satisfies(..)` and friends return the same assert, but their lambdas run their own assertions
    private static boolean takesConsumer(JavaType.Method methodType) {
        for (JavaType parameterType : methodType.getParameterTypes()) {
            JavaType type = parameterType instanceof JavaType.Array ? ((JavaType.Array) parameterType).getElemType() : parameterType;
            if (TypeUtils.isAssignableTo("java.util.function.Consumer", type) || TypeUtils.isAssignableTo("java.util.function.BiConsumer", type)) {
                return true;
            }
        }
        return false;
    }

    private static J.@Nullable MethodInvocation resolveOn(J.MethodInvocation message, @Nullable JavaType assertType) {
        JavaType.FullyQualified assertClass = TypeUtils.asFullyQualified(assertType);
        JavaType.Method methodType = message.getMethodType();
        if (assertClass == null || methodType == null || !TypeUtils.findDeclaredMethod(assertClass, methodType.getName(), methodType.getParameterTypes()).isPresent()) {
            return null;
        }
        return message.withMethodType(methodType.withDeclaringType(assertClass).withReturnType(assertType));
    }

    private static J.MethodInvocation moveMessagesAfterEntry(J.MethodInvocation statement, J.MethodInvocation entry, List<J.MethodInvocation> messages, List<J.MethodInvocation> assertions) {
        J.MethodInvocation chain = entry;
        for (J.MethodInvocation message : messages) {
            chain = message.withPrefix(Space.EMPTY).withSelect(chain);
        }
        Space beforeLastMessageDot = messages.get(messages.size() - 1).getPadding().getSelect().getAfter();
        J.MethodInvocation firstAssertion = assertions.get(0);
        JRightPadded<Expression> firstAssertionSelect = firstAssertion.getPadding().getSelect();
        if (beforeLastMessageDot.getWhitespace().contains("\n") && !firstAssertionSelect.getAfter().getWhitespace().contains("\n")) {
            // Keep a chain that ended in a wrapped message wrapped, rather than appending the assertion to the moved message
            assertions.set(0, firstAssertion.getPadding().withSelect(firstAssertionSelect.withAfter(firstAssertionSelect.getAfter().withWhitespace(beforeLastMessageDot.getWhitespace()))));
        }
        for (J.MethodInvocation assertion : assertions) {
            chain = assertion.withSelect(chain);
        }
        return chain.withPrefix(statement.getPrefix());
    }
}
