/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.java.tree.JavaType;


public class AssertLiteralBooleanToFailRecipe extends Recipe {

    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    private static final MethodMatcher ASSERT_FALSE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertFalse(..)");

    @Override
    public String getDisplayName() {
        //language=markdown
        return "Replace JUnit `assertTrue(false, \"reason\")` and `assertFalse(true, \"reason\")` with `fail(\"reason\")`";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Using fail is more direct and clear.";
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

                boolean convertToFail = false;

                if (ASSERT_TRUE.matches(mi) && isBooleanLiteral(mi)) {
                    if (!Boolean.parseBoolean(((J.Literal) mi.getArguments().get(0)).getValueSource())) {
                        convertToFail = true;
                    }
                } else if (ASSERT_FALSE.matches(mi) && isBooleanLiteral(mi)) {
                    if (Boolean.parseBoolean(((J.Literal) mi.getArguments().get(0)).getValueSource())) {
                        convertToFail = true;
                    }
                }

                if (convertToFail) {
                    StringBuilder sb = new StringBuilder();
                    maybeRemoveImport("org.junit.jupiter.api.Assertions");
                    maybeAddImport("org.junit.jupiter.api.Assertions", "fail");

                    sb.append("fail(");

                    Object[] args = new Object[0];

                    if (mi.getArguments().size() == 2) {
                        sb.append("#{any()}");
                        args = new Object[]{mi.getArguments().get(1)};
                    }
                    sb.append(")");

                    JavaTemplate t = JavaTemplate.builder(sb.toString())
                            .staticImports("org.junit.jupiter.api.Assertions.fail")
                            .javaParser(javaParser(ctx))
                            .build();

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
