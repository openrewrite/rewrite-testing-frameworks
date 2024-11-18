/*
 * Copyright 2024 the original author or authors.
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
        return "Prefers the usage of `assertSame` or `assertNotSame` methods instead of using of vanilla assertTrue " +
               "or assertFalse with a boolean comparison.";
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

                JavaType.Method newType = ((JavaType.Method) mi.getName().getType()).withName(newMethodName);
                return mi.withName(mi.getName().withSimpleName(newMethodName).withType(newType))
                        .withMethodType(newType)
                        .withArguments(newArguments);
            }
        };
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(ASSERT_TRUE_MATCHER),
                        new UsesMethod<>(ASSERT_FALSE_MATCHER)),
                visitor);
    }

}
