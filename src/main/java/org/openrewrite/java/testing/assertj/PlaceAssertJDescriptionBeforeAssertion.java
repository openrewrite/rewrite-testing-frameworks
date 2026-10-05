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

    @Getter
    final String displayName = "Place AssertJ descriptions and failure messages before the assertion";

    @Getter
    final String description = "AssertJ only applies `as(..)`, `describedAs(..)`, `withFailMessage(..)` and `overridingErrorMessage(..)` to assertions that run after them, so when they come last in a chain they are silently ignored. This moves them back past the assertions made on the same assert object, to directly after the call that created it, such as `assertThat(..)` or a navigation like `extracting(..)`. For `assertThatThrownBy(..)` the check that something was thrown has already run by then, so the moved message only applies to the chained checks such as `isInstanceOf(..)`. Note that a moved `withFailMessage(..)` or `overridingErrorMessage(..)` replaces AssertJ's entire failure message, including the expected and actual values.";

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

                        List<J.MethodInvocation> messages = new ArrayList<>();
                        boolean movesDescription = false;
                        boolean movesFailMessage = false;
                        Expression current = mi;
                        while (isDescription(current) || isFailMessage(current)) {
                            movesDescription |= isDescription(current);
                            movesFailMessage |= isFailMessage(current);
                            messages.add(0, (J.MethodInvocation) current);
                            current = ((J.MethodInvocation) current).getSelect();
                        }
                        if (messages.isEmpty()) {
                            return mi;
                        }
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        boolean resultDiscarded = parent instanceof J.Lambda ? returnsVoid(TypeUtils.asFullyQualified(((J.Lambda) parent).getType())) : parent instanceof J.Block || parent instanceof J.If || parent instanceof J.If.Else || parent instanceof Loop || parent instanceof J.Label || parent instanceof J.Case && ((J.Case) parent).getType() == J.Case.Type.Statement;
                        if (!resultDiscarded) {
                            return mi;
                        }

                        // A description or failure message set earlier in the chain would override the moved one, so moving it achieves nothing
                        for (Expression earlier = current; earlier instanceof J.MethodInvocation; earlier = ((J.MethodInvocation) earlier).getSelect()) {
                            if (movesDescription && isDescription(earlier) || movesFailMessage && isFailMessage(earlier)) {
                                return mi;
                            }
                        }

                        List<J.MethodInvocation> assertions = new ArrayList<>();
                        while (current instanceof J.MethodInvocation && keepsAssert((J.MethodInvocation) current)) {
                            assertions.add(0, (J.MethodInvocation) current);
                            current = ((J.MethodInvocation) current).getSelect();
                        }
                        if (current == null) {
                            return mi;
                        }
                        if (TypeUtils.isOfClassType(current.getType(), "org.assertj.core.api.ThrowableAssertAlternative")) {
                            // A failure message set here reaches none of the delegated `withMessage(..)` checks that follow
                            if (movesFailMessage) {
                                return mi;
                            }
                            // Describe the `isThrownBy(..)` check too, from the `ThrowableTypeAssert` it is called on
                            Expression typeAssert = current instanceof J.MethodInvocation ? ((J.MethodInvocation) current).getSelect() : null;
                            if (typeAssert != null && TypeUtils.isOfClassType(typeAssert.getType(), "org.assertj.core.api.ThrowableTypeAssert")) {
                                JavaType.FullyQualified typeAssertType = (JavaType.FullyQualified) typeAssert.getType();
                                List<J.MethodInvocation> retyped = new ArrayList<>();
                                for (J.MethodInvocation message : messages) {
                                    JavaType.Method methodType = message.getMethodType();
                                    if (methodType != null && TypeUtils.findDeclaredMethod(typeAssertType, methodType.getName(), methodType.getParameterTypes()).isPresent()) {
                                        retyped.add(message.withMethodType(methodType.withDeclaringType(typeAssertType).withReturnType(typeAssertType)));
                                    }
                                }
                                if (retyped.size() == messages.size()) {
                                    messages = retyped;
                                    assertions.add(0, (J.MethodInvocation) current);
                                    current = typeAssert;
                                }
                            }
                        }
                        if (assertions.isEmpty()) {
                            return mi;
                        }

                        Expression chain = current;
                        for (J.MethodInvocation message : messages) {
                            chain = message.withPrefix(Space.EMPTY).withSelect(chain);
                        }
                        Space beforeLastMessageDot = mi.getPadding().getSelect().getAfter();
                        JRightPadded<Expression> firstAssertionSelect = assertions.get(0).getPadding().getSelect();
                        if (beforeLastMessageDot.getWhitespace().contains("\n") && !firstAssertionSelect.getAfter().getWhitespace().contains("\n")) {
                            // Keep a chain that ended in a wrapped message wrapped, rather than appending the assertion to the moved message
                            assertions.set(0, assertions.get(0).getPadding().withSelect(firstAssertionSelect.withAfter(firstAssertionSelect.getAfter().withWhitespace(beforeLastMessageDot.getWhitespace()))));
                        }
                        for (J.MethodInvocation assertion : assertions) {
                            chain = assertion.withSelect(chain);
                        }
                        return ((J.MethodInvocation) chain).withPrefix(mi.getPrefix());
                    }
                }
        );
    }

    private static boolean isDescription(@Nullable Expression expression) {
        return AS.matches(expression) || DESCRIBED_AS.matches(expression);
    }

    private static boolean isFailMessage(@Nullable Expression expression) {
        return WITH_FAIL_MESSAGE.matches(expression) || OVERRIDING_ERROR_MESSAGE.matches(expression);
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

    // Whether the call asserts on its receiver and returns that same assert, rather than navigating to a new one
    private static boolean keepsAssert(J.MethodInvocation call) {
        if (call.getSelect() == null || call.getMethodType() == null) {
            return false;
        }
        // `satisfies(..)` and friends return the same assert, but their lambdas run their own assertions
        for (JavaType parameterType : call.getMethodType().getParameterTypes()) {
            JavaType elementType = parameterType instanceof JavaType.Array ? ((JavaType.Array) parameterType).getElemType() : parameterType;
            if (TypeUtils.isAssignableTo("java.util.function.Consumer", elementType) || TypeUtils.isAssignableTo("java.util.function.BiConsumer", elementType)) {
                return false;
            }
        }
        JavaType type = call.getType();
        JavaType receiver = call.getSelect().getType();
        if (type instanceof JavaType.GenericTypeVariable) {
            // A capture of `SELF`, returned on a receiver typed with a wildcard like `AbstractIntegerAssert<?>`
            List<JavaType> bounds = ((JavaType.GenericTypeVariable) type).getBounds();
            JavaType receiverClass = receiver instanceof JavaType.GenericTypeVariable && ((JavaType.GenericTypeVariable) receiver).getBounds().size() == 1 ? ((JavaType.GenericTypeVariable) receiver).getBounds().get(0) : receiver;
            JavaType.FullyQualified receiverFq = TypeUtils.asFullyQualified(receiverClass);
            return bounds.size() == 1 && receiverFq != null && TypeUtils.isOfClassType(bounds.get(0), receiverFq.getFullyQualifiedName()) && TypeUtils.isAssignableTo("org.assertj.core.api.Assert", receiverFq);
        }
        // On a receiver typed with a wildcard or capture, `SELF` comes back as a capture, so an exact match is a new assert, as from `assertThat(string).asString()`
        boolean wildcardReceiver = receiver instanceof JavaType.GenericTypeVariable || receiver instanceof JavaType.Parameterized && ((JavaType.Parameterized) receiver).getTypeParameters().stream().anyMatch(p -> p instanceof JavaType.GenericTypeVariable && "?".equals(((JavaType.GenericTypeVariable) p).getName()));
        return !wildcardReceiver && TypeUtils.isOfType(type, receiver) && TypeUtils.isAssignableTo("org.assertj.core.api.Assert", type);
    }
}
