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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            JavaParser.Builder<?, ?> javaParser = null;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9");
                }
                return javaParser;
            }

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
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .staticImports("org.junit.jupiter.api.Assertions.assertFalse")
                                .javaParser(javaParser(ctx))
                                .build();
                    } else {
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .imports("org.junit.jupiter.api.Assertions")
                                .javaParser(javaParser(ctx))
                                .build();
                    }
                    return  t.apply(updateCursor(mi), mi.getCoordinates().replace(), args);
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
