/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;

import java.util.function.Supplier;

public class AssertTrueNegationToAssertFalse extends Recipe {
    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertTrue(!<boolean>)` to `assertFalse(<boolean>)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertFalse` is simpler and more clear.";
    }


    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            final Supplier<JavaParser> javaParser = () -> JavaParser.fromJavaVersion()
                    .classpath("junit-jupiter-api")
                    .build();
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_TRUE.matches(mi) && isUnaryOperatorNot(mi)) {
                    StringBuilder sb = new StringBuilder();
                    J.Unary unary = (J.Unary) mi.getArguments().get(0);
                    if (mi.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertFalse");
                    } else {
                        sb.append("Assertions.");
                    }
                    sb.append("assertFalse(#{any(java.lang.Boolean)}");
                    Object[] args;
                    if (mi.getArguments().size() == 2) {
                        args = new Object[]{unary.getExpression(), mi.getArguments().get(1)};
                        sb.append(", #{any()}");
                    } else {
                        args = new Object[]{unary.getExpression()};
                    }
                    sb.append(")");
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .staticImports("org.junit.jupiter.api.Assertions.assertFalse").javaParser(javaParser).build();
                    } else {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .imports("org.junit.jupiter.api.Assertions").javaParser(javaParser).build();
                    }
                    return mi.withTemplate(t, mi.getCoordinates().replace(), args);
                }
                return mi;
            }

            private boolean isUnaryOperatorNot(J.MethodInvocation method) {
                if (!method.getArguments().isEmpty() && method.getArguments().get(0) instanceof J.Unary) {
                    J.Unary unary = (J.Unary) method.getArguments().get(0);
                    return unary.getOperator().equals(J.Unary.Type.Not);
                }

                return false;
            }
        };
    }
}
