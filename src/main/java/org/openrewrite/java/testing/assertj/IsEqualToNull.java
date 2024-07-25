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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType.Method;

import java.util.Collections;

/**
 * AssertJ has a more idiomatic way of asserting that an object is null. This
 * recipe will find instances of:
 * <p>
 * -`assertThat(Object).isEqualTo(null)` and replace them with `isNull()`.
 */
public class IsEqualToNull extends Recipe {

    private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher("org.assertj.core.api.AbstractAssert isEqualTo(..)");
    
    @Override
    public String getDisplayName() {
        return "Convert `assertThat(Object).isEqualTo(null)` to `isNull()`";
    }

    @Override
    public String getDescription() {
        return "Adopt idiomatic AssertJ assertion for null check.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_EQUAL_TO), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (IS_EQUAL_TO.matches(mi)) {
                    String methodName;
                    if (isNull(mi.getArguments().get(0))) {
                        methodName = "isNull";
                    } else {
                        return mi;
                    }
                    Method isBooleanMethod = mi.getMethodType().withName(methodName);
                    return mi.withName(mi.getName().withSimpleName(methodName).withType(isBooleanMethod))
                            .withMethodType(isBooleanMethod).withArguments(Collections.emptyList());
                }
                return mi;
            }

        });
    }
    

    private boolean isNull(Expression expression) {
        if(expression instanceof J.Literal literal) {
            return literal.getValue() == null;
        }
        return false;
    }
}
