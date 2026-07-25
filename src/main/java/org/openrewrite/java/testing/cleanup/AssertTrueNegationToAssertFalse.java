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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class AssertTrueNegationToAssertFalse extends Recipe {
    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    @Getter
    final String displayName = "Replace JUnit `assertTrue(!<boolean>)` to `assertFalse(<boolean>)`";

    @Getter
    final String description = "Using `assertFalse` is simpler and more clear.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_TRUE), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_TRUE.matches(mi) || !isUnaryOperatorNot(mi)) {
                    return mi;
                }

                if (mi.getSelect() == null) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertTrue");
                    maybeAddImport("org.junit.jupiter.api.Assertions", "assertFalse");
                }

                JavaType.Method newType = assertFalseMethodType(mi.getMethodType());
                return mi.withName(mi.getName().withSimpleName("assertFalse").withType(newType))
                        .withMethodType(newType)
                        .withArguments(ListUtils.mapFirst(mi.getArguments(),
                                arg -> ((J.Unary) arg).getExpression().withPrefix(arg.getPrefix())));
            }

            private JavaType.Method assertFalseMethodType(JavaType.Method assertTrue) {
                for (JavaType.Method method : assertTrue.getDeclaringType().getMethods()) {
                    if ("assertFalse".equals(method.getName()) &&
                            method.getParameterTypes().equals(assertTrue.getParameterTypes())) {
                        return method;
                    }
                }
                // fallback when type attribution was stubbed
                return assertTrue.withName("assertFalse");
            }

            private boolean isUnaryOperatorNot(J.MethodInvocation method) {
                if (!method.getArguments().isEmpty() && method.getArguments().get(0) instanceof J.Unary) {
                    J.Unary unary = (J.Unary) method.getArguments().get(0);
                    return unary.getOperator() == J.Unary.Type.Not;
                }
                return false;
            }
        });
    }
}
