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

public class AssertTrueEqualsToAssertEquals extends Recipe {
    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertTrue(a.equals(b))` to `assertEquals(a,b)`";
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
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9");
                }
                return javaParser;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_TRUE.matches(mi) && isEquals(mi.getArguments().get(0))) {
                    StringBuilder sb = new StringBuilder();
                    if (mi.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertEquals");
                    } else {
                        sb.append("Assertions.");
                    }
                    J.MethodInvocation s = (J.MethodInvocation) mi.getArguments().get(0);
                    sb.append("assertEquals(#{any(java.lang.Object)},#{any(java.lang.Object)}");
                    Object[] args;
                    if (mi.getArguments().size() == 2) {
                        args = new Object[]{s.getSelect(), s.getArguments().get(0), mi.getArguments().get(1)};
                        sb.append(", #{any()}");
                    } else {
                        args = new Object[]{s.getSelect(), s.getArguments().get(0)};
                    }
                    sb.append(")");
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .staticImports("org.junit.jupiter.api.Assertions.assertEquals")
                                .javaParser(javaParser(ctx))
                                .build();
                    } else {
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .imports("org.junit.jupiter.api.Assertions.assertEquals")
                                .javaParser(javaParser(ctx))
                                .build();
                    }
                    return  t.apply(updateCursor(mi), mi.getCoordinates().replace(), args);
                }
                return mi;
            }

            private boolean isEquals(Expression expr) {
                if (!(expr instanceof J.MethodInvocation)) {
                    return false;
                }

                J.MethodInvocation methodInvocation = (J.MethodInvocation) expr;

                return "equals".equals(methodInvocation.getName().getSimpleName()) &&
                        methodInvocation.getArguments().size() == 1;
            }
        });
    }
}
