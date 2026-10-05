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
    private static final MethodMatcher ASSUME_THAT = new MethodMatcher("org.assertj.core.api..* assumeThat*(..)");

    @Getter
    final String displayName = "Place AssertJ descriptions and failure messages before the assertion";

    @Getter
    final String description = "AssertJ only applies `as(..)`, `describedAs(..)`, `withFailMessage(..)` and `overridingErrorMessage(..)` to assertions that run after them, so when they come last in a chain they are silently ignored. This moves them to directly after the `assertThat(..)`, `then(..)` or `assumeThat(..)` entry point. For `assertThatThrownBy(..)` the check that something was thrown has already run by then, so the moved message only applies to the chained checks such as `isInstanceOf(..)`. Note that a moved `withFailMessage(..)` or `overridingErrorMessage(..)` replaces AssertJ's entire failure message, including the expected and actual values.";

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

                        List<J.MethodInvocation> assertions = new ArrayList<>();
                        while (current instanceof J.MethodInvocation && !ASSERT_THAT.matches(current) && !THEN.matches(current) && !ASSUME_THAT.matches(current)) {
                            // A description or failure message set earlier in the chain would override the moved one, so moving it achieves nothing
                            if (movesDescription && isDescription(current) || movesFailMessage && isFailMessage(current)) {
                                return mi;
                            }
                            assertions.add(0, (J.MethodInvocation) current);
                            current = ((J.MethodInvocation) current).getSelect();
                        }
                        if (assertions.isEmpty() || !(current instanceof J.MethodInvocation)) {
                            return mi;
                        }

                        J.MethodInvocation entry = (J.MethodInvocation) current;
                        if (TypeUtils.isOfClassType(entry.getType(), "org.assertj.core.api.ThrowableTypeAssert")) {
                            if (!keepsAssertType(assertions.subList(1, assertions.size()), assertions.get(0).getType())) {
                                return mi;
                            }
                            JavaType.FullyQualified typeAssert = (JavaType.FullyQualified) entry.getType();
                            for (int i = 0; i < messages.size(); i++) {
                                JavaType.Method methodType = messages.get(i).getMethodType();
                                if (methodType == null || !TypeUtils.findDeclaredMethod(typeAssert, methodType.getName(), methodType.getParameterTypes()).isPresent()) {
                                    return mi;
                                }
                                messages.set(i, messages.get(i).withMethodType(methodType.withDeclaringType(typeAssert).withReturnType(typeAssert)));
                            }
                        } else if (!keepsAssertType(assertions, entry.getType())) {
                            return mi;
                        }

                        J.MethodInvocation chain = entry;
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
                        return chain.withPrefix(mi.getPrefix());
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

    private static boolean keepsAssertType(List<J.MethodInvocation> calls, @Nullable JavaType assertType) {
        JavaType.FullyQualified assertClass = TypeUtils.asFullyQualified(assertType);
        if (assertClass == null) {
            return false;
        }
        // Calls returning `SELF` on a wildcard-typed entry like `AbstractStringAssert<?>` come back as a capture, so there an exact type match is a new assert, as from `assertThat(string).asString()`
        boolean wildcardEntry = assertType instanceof JavaType.Parameterized && ((JavaType.Parameterized) assertType).getTypeParameters().stream().anyMatch(p -> p instanceof JavaType.GenericTypeVariable && "?".equals(((JavaType.GenericTypeVariable) p).getName()));
        for (J.MethodInvocation call : calls) {
            JavaType type = call.getType();
            boolean sameAssert = type instanceof JavaType.GenericTypeVariable ? ((JavaType.GenericTypeVariable) type).getBounds().size() == 1 && TypeUtils.isOfClassType(((JavaType.GenericTypeVariable) type).getBounds().get(0), assertClass.getFullyQualifiedName()) : !wildcardEntry && TypeUtils.isOfType(type, assertType);
            if (!sameAssert || call.getMethodType() == null) {
                return false;
            }
            // `satisfies(..)` and friends return the same assert, but their lambdas run their own assertions
            for (JavaType parameterType : call.getMethodType().getParameterTypes()) {
                JavaType elementType = parameterType instanceof JavaType.Array ? ((JavaType.Array) parameterType).getElemType() : parameterType;
                if (TypeUtils.isAssignableTo("java.util.function.Consumer", elementType) || TypeUtils.isAssignableTo("java.util.function.BiConsumer", elementType)) {
                    return false;
                }
            }
        }
        return true;
    }
}
