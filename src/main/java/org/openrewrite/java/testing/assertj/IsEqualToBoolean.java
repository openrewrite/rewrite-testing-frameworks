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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.JavaType.Method;

import java.util.Collections;

/**
 * AssertJ has a more idiomatic way of asserting that a boolean is true. This
 * recipe will find instances of:
 * 
 * -`assertThat(boolean).isEqualTo(true)` and replace them with `isTrue()`.
 * -`assertThat(boolean).isEqualTo(false)` and replace them with `isFalse()`.
 */
public class IsEqualToBoolean extends Recipe {

    private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher(
            "org.assertj.core.api.AbstractBooleanAssert isEqualTo(boolean)");

    @Override
    public String getDisplayName() {
        return "Convert `assertThat(String).isEqualTo(true)` to `isTrue()` and `isEqualTo(false)` to `isFalse()`";
    }

    @Override
    public String getDescription() {
        return "Adopt idiomatic AssertJ assertion for true booleans.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_EQUAL_TO), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (IS_EQUAL_TO.matches(mi)) {
                    String methodName;
                    if (J.Literal.isLiteralValue(mi.getArguments().get(0), true)) {
                        methodName = "isTrue";
                    } else {
                        methodName = "isFalse";
                    }
                    Method isBooleanMethod = mi.getMethodType().withName(methodName);
                    return mi.withName(mi.getName().withSimpleName(methodName).withType(isBooleanMethod))
                            .withMethodType(isBooleanMethod).withArguments(Collections.emptyList());
                }
                return mi;
            }
        });
    }
}
