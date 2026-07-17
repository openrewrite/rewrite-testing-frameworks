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
import org.openrewrite.java.tree.Space;

import java.util.List;

public class AssertEqualsNullToAssertNull extends Recipe {
    private static final MethodMatcher ASSERT_EQUALS = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertEquals(..)");

    @Getter
    final String displayName = "`assertEquals(a, null)` to `assertNull(a)`";

    @Getter
    final String description = "Using `assertNull(a)` is simpler and more clear.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_EQUALS), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_EQUALS.matches(mi) || mi.getMethodType() == null) {
                    return mi;
                }
                List<Expression> arguments = mi.getArguments();
                if (!hasNullLiteralArg(arguments)) {
                    return mi;
                }

                List<Expression> newArguments = ListUtils.mapFirst(
                        ListUtils.filter(arguments, arg -> !isNullLiteral(arg)),
                        arg -> arg.withPrefix(Space.EMPTY));

                if (mi.getSelect() == null) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertEquals");
                    maybeAddImport("org.junit.jupiter.api.Assertions", "assertNull");
                }

                JavaType.Method newType = assertNullMethodType(mi.getMethodType(), newArguments.size());
                return mi.withName(mi.getName().withSimpleName("assertNull").withType(newType))
                        .withMethodType(newType)
                        .withArguments(newArguments);
            }

            private JavaType.Method assertNullMethodType(JavaType.Method assertEquals, int parameterCount) {
                JavaType messageType = parameterCount == 2 ?
                        assertEquals.getParameterTypes().get(assertEquals.getParameterTypes().size() - 1) : null;
                for (JavaType.Method method : assertEquals.getDeclaringType().getMethods()) {
                    if ("assertNull".equals(method.getName()) &&
                            method.getParameterTypes().size() == parameterCount &&
                            (messageType == null || messageType.equals(method.getParameterTypes().get(parameterCount - 1)))) {
                        return method;
                    }
                }
                // fallback when type attribution was stubbed
                return assertEquals.withName("assertNull");
            }

            private boolean hasNullLiteralArg(List<Expression> arguments) {
                return arguments.size() > 1 &&
                        (isNullLiteral(arguments.get(0)) || isNullLiteral(arguments.get(1)));
            }

            private boolean isNullLiteral(Expression expr) {
                return expr.getType() == JavaType.Primitive.Null || J.Literal.isLiteralValue(expr, null);
            }
        });
    }
}
