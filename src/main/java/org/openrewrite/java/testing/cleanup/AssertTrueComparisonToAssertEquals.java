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

public class AssertTrueComparisonToAssertEquals extends Recipe {
    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    @Override
    public String getDisplayName() {
        return "Junit `assertTrue(a == b)` to `assertEquals(a,b)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertEquals(a,b)` is simpler and more clear.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_TRUE), new JavaVisitor<ExecutionContext>() {

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
                if (ASSERT_TRUE.matches(mi) && isEqualBinary(mi)) {
                    J.Binary binary = (J.Binary) mi.getArguments().get(0);
                    StringBuilder sb = new StringBuilder();
                    Object[] args;
                    if (mi.getSelect() != null) {
                        sb.append("Assertions.");
                    }
                    sb.append("assertEquals(#{any(java.lang.Object)}, #{any(java.lang.Object)}");
                    if (mi.getArguments().size() == 2) {
                        sb.append(", #{any()}");
                        args = new Object[]{binary.getLeft(), binary.getRight(), mi.getArguments().get(1)};
                    } else {
                        args = new Object[]{binary.getLeft(), binary.getRight()};
                    }
                    sb.append(")");
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertEquals");
                        t = JavaTemplate.builder(this::getCursor, sb.toString()).javaParser(javaParser(ctx))
                                .staticImports("org.junit.jupiter.api.Assertions.assertEquals").build();
                    } else {
                        t = JavaTemplate.builder(this::getCursor, sb.toString()).javaParser(javaParser(ctx))
                                .imports("org.junit.jupiter.api.Assertions").build();

                    }
                    return mi.withTemplate(t, mi.getCoordinates().replace(), args);
                }
                return mi;
            }

            private boolean isEqualBinary(J.MethodInvocation method) {

                if (method.getArguments().isEmpty()) {
                    return false;
                }

                final Expression firstArgument = method.getArguments().get(0);
                if (!(firstArgument instanceof J.Binary)) {
                    return false;
                }

                J.Binary binary = (J.Binary) firstArgument;
                J.Binary.Type operator = binary.getOperator();

                if (!operator.equals(J.Binary.Type.Equal)) {
                    return false;
                }

                // Prevent breaking identity comparison.
                // Objects that are compared with == should not be compared with `.equals()` instead.
                // Out of the primitives == is not allowed when both are of type String
                return binary.getLeft().getType() instanceof JavaType.Primitive
                        && binary.getRight().getType() instanceof JavaType.Primitive
                        && !(binary.getLeft().getType() == JavaType.Primitive.String
                        && binary.getRight().getType() == JavaType.Primitive.String);
            }
        });
    }
}
