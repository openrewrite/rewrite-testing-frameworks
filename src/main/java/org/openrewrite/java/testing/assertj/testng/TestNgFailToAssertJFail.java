/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.assertj.testng;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class TestNgFailToAssertJFail
        extends Recipe {
    @Override
    public String getDisplayName() {
        return "JUnit fail to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `fail()` to AssertJ's `fail()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.api.Assertions", false), new JUnitFailToAssertJFailVisitor());
    }

    public static class JUnitFailToAssertJFailVisitor extends JavaIsoVisitor<ExecutionContext> {
        private JavaParser.Builder<?, ?> assertionsParser;

        private JavaParser.Builder<?, ?> assertionsParser(ExecutionContext ctx) {
            if (assertionsParser == null) {
                assertionsParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24");
            }
            return assertionsParser;
        }

        private static final MethodMatcher JUNIT_FAIL_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions" + " fail(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = method;

            if (!JUNIT_FAIL_MATCHER.matches(m)) {
                return m;
            }

            List<Expression> args = m.getArguments();

            if (args.size() == 1) {
                // fail(), fail(String), fail(Supplier<String>), fail(Throwable)
                if (args.get(0) instanceof J.Empty) {
                    m = JavaTemplate.builder("org.assertj.core.api.Assertions.fail(\"\");")
                            .javaParser(assertionsParser(ctx))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace());
                } else if (args.get(0) instanceof J.Literal ||
                           TypeUtils.isAssignableTo("java.lang.String", args.get(0).getType())) {
                    m = JavaTemplate.builder("org.assertj.core.api.Assertions.fail(#{any()});")
                            .javaParser(assertionsParser(ctx))
                            .build()
                            .apply(
                                    getCursor(),
                                    m.getCoordinates().replace(),
                                    args.get(0)
                            );
                } else {
                    m = JavaTemplate.builder("org.assertj.core.api.Assertions.fail(\"\", #{any()});")
                            .javaParser(assertionsParser(ctx))
                            .build()
                            .apply(
                                    getCursor(),
                                    m.getCoordinates().replace(),
                                    args.get(0)
                            );
                }
            } else {
                // fail(String, Throwable)
                StringBuilder templateBuilder = new StringBuilder("org.assertj.core.api.Assertions.fail(");
                for (int i = 0; i < args.size(); i++) {
                    templateBuilder.append("#{any()}");
                    if (i < args.size() - 1) {
                        templateBuilder.append(", ");
                    }
                }
                templateBuilder.append(");");

                m = JavaTemplate.builder(templateBuilder.toString())
                        .javaParser(assertionsParser(ctx))
                        .build()
                        .apply(
                                getCursor(),
                                m.getCoordinates().replace(),
                                args.toArray()
                        );
            }

            doAfterVisit(new RemoveUnusedImports().getVisitor());
            doAfterVisit(new UnqualifiedMethodInvocations());
            return m;
        }

        private static class UnqualifiedMethodInvocations extends JavaIsoVisitor<ExecutionContext> {
            private static final MethodMatcher ASSERTJ_FAIL_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions" + " fail(..)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (!ASSERTJ_FAIL_MATCHER.matches(method)) {
                    return method;
                }

                StringBuilder templateBuilder = new StringBuilder("fail(");
                List<Expression> arguments = method.getArguments();
                for (int i = 0; i < arguments.size(); i++) {
                    templateBuilder.append("#{any()}");
                    if (i < arguments.size() - 1) {
                        templateBuilder.append(", ");
                    }
                }
                templateBuilder.append(");");

                method = JavaTemplate.builder(templateBuilder.toString())
                        .staticImports("org.assertj.core.api.Assertions" + ".fail")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().replace(),
                                arguments.toArray()
                        );
                //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat" (even if not referenced)
                maybeAddImport("org.assertj.core.api.Assertions", "fail", false);
                maybeRemoveImport("org.junit.jupiter.api.Assertions.fail");
                return super.visitMethodInvocation(method, ctx);
            }
        }
    }
}
