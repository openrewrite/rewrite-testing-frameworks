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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.trait.Traits;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType.Method;
import org.openrewrite.java.tree.MethodCall;

import java.util.Collections;

/**
 * AssertJ has a more idiomatic way of asserting that an object is null. This
 * recipe will find instances of:
 * <p>
 * -`assertThat(Object).isEqualTo(null)` and replace them with `isNull()`.
 */
public class IsEqualToNull extends Recipe {

    private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher("org.assertj.core.api.AbstractAssert isEqualTo(..)", true);

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
        return Traits.methodAccess(IS_EQUAL_TO).asVisitor(methodAccess -> {
                    MethodCall methodCall = methodAccess.getTree();
                    if (methodCall instanceof J.MethodInvocation && isNull(methodCall.getArguments().get(0))) {
                        @SuppressWarnings("DataFlowIssue")
                        Method isBooleanMethod = methodCall.getMethodType().withName("isNull");
                        return ((J.MethodInvocation) methodCall)
                                .withName(((J.MethodInvocation) methodCall).getName().withSimpleName("isNull").withType(isBooleanMethod))
                                .withMethodType(isBooleanMethod).withArguments(Collections.emptyList());
                    }
                    return methodCall;
                }
        );
    }

    private boolean isNull(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == null;
    }
}
