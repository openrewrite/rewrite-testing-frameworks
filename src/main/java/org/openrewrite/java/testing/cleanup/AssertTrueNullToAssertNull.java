/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class AssertTrueNullToAssertNull extends Recipe {
    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    @Getter
    final String displayName = "Replace JUnit `assertTrue(a == null)` to `assertNull(a)`";

    @Getter
    final String description = "Using `assertNull(a)` is simpler and more clear.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_TRUE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_TRUE.matches(mi) || !isEqualBinaryWithNull(mi)) {
                    return mi;
                }

                J.Binary binary = (J.Binary) mi.getArguments().get(0);
                Expression nonNullExpression = getNonNullExpression(binary);

                if (mi.getSelect() == null) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertTrue");
                    maybeAddImport("org.junit.jupiter.api.Assertions", "assertNull");
                }

                JavaType.Method newType = assertNullMethodType(mi.getMethodType(), mi.getArguments().size());
                return mi.withName(mi.getName().withSimpleName("assertNull").withType(newType))
                        .withMethodType(newType)
                        .withArguments(ListUtils.mapFirst(mi.getArguments(),
                                arg -> nonNullExpression.withPrefix(binary.getPrefix())));
            }

            private JavaType.@Nullable Method assertNullMethodType(JavaType.@Nullable Method assertTrue, int parameterCount) {
                if (assertTrue == null) {
                    return null;
                }
                JavaType messageType = parameterCount == 2 ?
                        assertTrue.getParameterTypes().get(assertTrue.getParameterTypes().size() - 1) : null;
                for (JavaType.Method method : assertTrue.getDeclaringType().getMethods()) {
                    if ("assertNull".equals(method.getName()) &&
                            method.getParameterTypes().size() == parameterCount &&
                            (messageType == null || messageType.equals(method.getParameterTypes().get(parameterCount - 1)))) {
                        return method;
                    }
                }
                // fallback when type attribution was stubbed
                return assertTrue.withName("assertNull");
            }

            private Expression getNonNullExpression(J.Binary binary) {
                if (binary.getRight() instanceof J.Literal) {
                    boolean isNull = ((J.Literal) binary.getRight()).getValue() == null;
                    if (isNull) {
                        return binary.getLeft();
                    }
                }
                return binary.getRight();
            }

            private boolean isEqualBinaryWithNull(J.MethodInvocation method) {
                if (method.getArguments().isEmpty()) {
                    return false;
                }

                final Expression firstArgument = method.getArguments().get(0);
                if (!(firstArgument instanceof J.Binary)) {
                    return false;
                }

                J.Binary binary = (J.Binary) firstArgument;
                if (binary.getOperator() != J.Binary.Type.Equal) {
                    return false;
                }
                return J.Literal.isLiteralValue(binary.getLeft(), null) ||
                        J.Literal.isLiteralValue(binary.getRight(), null);
            }
        });
    }
}
