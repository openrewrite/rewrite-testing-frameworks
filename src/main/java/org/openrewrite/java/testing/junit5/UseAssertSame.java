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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;

public class UseAssertSame extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use JUnit5's `assertSame` or `assertNotSame` instead of `assertTrue(... == ...)`";
    }

    @Override
    public String getDescription() {
        return "Prefers the usage of `assertSame` or `assertNotSame` methods instead of using of vanilla `assertTrue` " +
               "or `assertFalse` with a boolean comparison.";
    }

    private static final MethodMatcher ASSERT_TRUE_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertTrue(..)");
    private static final MethodMatcher ASSERT_FALSE_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertFalse(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);
                if (!ASSERT_TRUE_MATCHER.matches(mi) && !ASSERT_FALSE_MATCHER.matches(mi)) {
                    return mi;
                }
                if (mi.getMethodType() == null) {
                    return mi;
                }

                Expression firstArgument = mi.getArguments().get(0);
                if (!(firstArgument instanceof J.Binary)) {
                    return mi;
                }
                J.Binary binary = (J.Binary) firstArgument;
                if (binary.getOperator() != J.Binary.Type.Equal && binary.getOperator() != J.Binary.Type.NotEqual) {
                    return mi;
                }
                List<Expression> newArguments = new ArrayList<>();
                newArguments.add(binary.getLeft());
                newArguments.add(binary.getRight());
                newArguments.addAll(mi.getArguments().subList(1, mi.getArguments().size()));

                String newMethodName = binary.getOperator() == J.Binary.Type.Equal == ASSERT_TRUE_MATCHER.matches(mi) ?
                        "assertSame" : "assertNotSame";

                maybeRemoveImport("org.junit.jupiter.api.Assertions");
                maybeAddImport("org.junit.jupiter.api.Assertions", newMethodName);

                JavaType.Method newType = assertSameMethodType(mi, newMethodName);
                return mi.withName(mi.getName().withSimpleName(newMethodName).withType(newType))
                        .withMethodType(newType)
                        .withArguments(newArguments);
            }

            private JavaType.Method assertSameMethodType(J.MethodInvocation mi, String newMethodName) {
                JavaType.Method assertTrue = mi.getMethodType();
                assert assertTrue != null;
                int parameterCount = assertTrue.getParameterTypes().size();
                JavaType.FullyQualified assertions = assertTrue.getDeclaringType();
                for (JavaType.Method method : assertions.getMethods()) {
                    if (method.getName().equals("assertSame") && method.getParameterNames().size() == parameterCount + 1 &&
                        assertTrue.getParameterTypes().get(parameterCount - 1).equals(method.getParameterTypes().get(parameterCount))) {
                        return method;
                    }
                }
                // fallback when type attribution was stubbed
                return assertTrue.withName(newMethodName);
            }
        };
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(ASSERT_TRUE_MATCHER),
                        new UsesMethod<>(ASSERT_FALSE_MATCHER)),
                visitor);
    }

}
