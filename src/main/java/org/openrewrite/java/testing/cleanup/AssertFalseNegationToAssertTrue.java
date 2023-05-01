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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class AssertFalseNegationToAssertTrue extends Recipe {
    private static final MethodMatcher ASSERT_FALSE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertFalse(..)");

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertFalse(!<boolean>)` to `assertTrue(<boolean>)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertTrue` is simpler and more clear.";
    }


    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            JavaParser.Builder<?, ?> javaParser = null;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9.2");
                }
                return javaParser;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_FALSE.matches(method) && isUnaryOperatorNot(method)) {
                    StringBuilder sb = new StringBuilder();
                    if (mi.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertTrue");
                    } else {
                        sb.append("Assertions.");
                    }
                    sb.append("assertTrue(#{any(java.lang.Boolean)}");
                    J.Unary unary = (J.Unary) method.getArguments().get(0);

                    Object[] args;
                    if (method.getArguments().size() == 2) {
                        args = new Object[]{unary.getExpression(), mi.getArguments().get(1)};
                        sb.append(", #{any()}");
                    } else {
                        args = new Object[]{unary.getExpression()};
                    }
                    sb.append(")");
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .staticImports("org.junit.jupiter.api.Assertions.assertTrue")
                                .javaParser(javaParser(ctx))
                                .build();
                    } else {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .imports("org.junit.jupiter.api.Assertions")
                                .javaParser(javaParser(ctx))
                                .build();
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
