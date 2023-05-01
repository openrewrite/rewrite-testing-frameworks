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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class AssertEqualsNullToAssertNull extends Recipe {
    private static final MethodMatcher ASSERT_EQUALS = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertEquals(..)");

    @Override
    public String getDisplayName() {
        return "`assertEquals(a, null)` to `assertNull(a)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertNull(a)` is simpler and more clear.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_EQUALS), new JavaVisitor<ExecutionContext>() {

            JavaParser.Builder<?, ?> javaParser = null;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9.2");
                }
                return javaParser;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_EQUALS.matches(method) && hasNullLiteralArg(mi)) {
                    StringBuilder sb = new StringBuilder();
                    Object[] args;
                    if (mi.getSelect() != null) {
                        sb.append("Assertions.");
                    }
                    sb.append("assertNull(#{any(java.lang.Object)}");
                    if (mi.getArguments().size() == 3) {
                        sb.append(", #{any()}");
                        args = new Object[]{(isNullLiteral(mi.getArguments().get(0)) ? mi.getArguments().get(1) : mi.getArguments().get(0)), mi.getArguments().get(2)};
                    } else {
                        args = new Object[]{(isNullLiteral(mi.getArguments().get(0)) ? mi.getArguments().get(1) : mi.getArguments().get(0))};
                    }
                    sb.append(")");
                    JavaTemplate t;
                    if (method.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertNull");
                        t = JavaTemplate.builder(this::getCursor, sb.toString()).javaParser(javaParser(ctx))
                                .staticImports("org.junit.jupiter.api.Assertions.assertNull").build();
                    } else {
                        t = JavaTemplate.builder(this::getCursor, sb.toString()).javaParser(javaParser(ctx))
                                .imports("org.junit.jupiter.api.Assertions.assertNull").build();
                    }
                    return mi.withTemplate(t, mi.getCoordinates().replace(), args);
                }
                return mi;
            }

            private boolean hasNullLiteralArg(J.MethodInvocation method) {
                if (method.getArguments().size() > 1) {
                    return isNullLiteral(method.getArguments().get(0)) || isNullLiteral(method.getArguments().get(1));
                }
                return false;
            }

            private boolean isNullLiteral(Expression expr) {
                return expr.getType() == JavaType.Primitive.Null;
            }
        });
    }
}
