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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class AssertEqualsBooleanToAssertBoolean extends Recipe {
    private static final MethodMatcher ASSERT_EQUALS = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertEquals(..)");

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertEquals(false, <boolean>)` to `assertFalse(<boolean>)` / `assertTrue(<boolean>)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertFalse` or `assertTrue` is simpler and more clear.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            JavaParser.Builder<?, ?> javaParser = null;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5");
                }
                return javaParser;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_EQUALS.matches(mi) && isBooleanLiteral(mi) &&
                        JavaType.Primitive.Boolean == mi.getArguments().get(1).getType()) {
                    StringBuilder sb = new StringBuilder();
                    String assertMethod = Boolean.parseBoolean(((J.Literal) mi.getArguments().get(0)).getValueSource()) ?
                            "assertTrue" : "assertFalse";
                    Expression assertion = mi.getArguments().get(1);
                    if (mi.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", assertMethod);
                    } else {
                        sb.append("Assertions.");
                    }
                    sb.append("#{}(#{any(java.lang.Boolean)}");
                    Object[] args;
                    if (mi.getArguments().size() == 3) {
                        args = new Object[]{assertMethod, assertion, mi.getArguments().get(2)};
                        sb.append(", #{any()}");
                    } else {
                        args = new Object[]{assertMethod, mi.getArguments().get(1)};
                    }
                    sb.append(")");
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(sb.toString())
                                .staticImports(String.format("org.junit.jupiter.api.Assertions.%s", assertMethod))
                                .javaParser(javaParser(ctx))
                                .build();
                    } else {
                        t = JavaTemplate.builder(sb.toString())
                                .imports("org.junit.jupiter.api.Assertions")
                                .javaParser(javaParser(ctx))
                                .build();
                    }
                    return t.apply(updateCursor(mi), mi.getCoordinates().replace(), args);
                }
                return mi;
            }

            private boolean isBooleanLiteral(J.MethodInvocation method) {
                if (!method.getArguments().isEmpty() && method.getArguments().get(0) instanceof J.Literal) {
                    J.Literal literal = (J.Literal) method.getArguments().get(0);
                    return JavaType.Primitive.Boolean == literal.getType();
                }

                return false;
            }
        };
    }
}
