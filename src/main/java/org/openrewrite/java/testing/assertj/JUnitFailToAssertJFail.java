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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public class JUnitFailToAssertJFail extends Recipe {
    @Override
    public String getDisplayName() {
        return "JUnit fail to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `fail()` to AssertJ's `fail()`.";
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.jupiter.api.Assertions", false);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JUnitFailToAssertJFailVisitor();
    }

    public static class JUnitFailToAssertJFailVisitor extends JavaIsoVisitor<ExecutionContext> {
        private Supplier<JavaParser> assertionsParser;
        private Supplier<JavaParser> assertionsParser(ExecutionContext ctx) {
            if(assertionsParser == null) {
                assertionsParser = () -> JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24.2")
                        .build();
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
                    m = m.withTemplate(
                            JavaTemplate.builder(this::getCursor, "org.assertj.core.api.Assertions.fail(\"\");")
                                    .javaParser(assertionsParser(ctx))
                                    .build(),
                            m.getCoordinates().replace()
                    );
                } else if (args.get(0) instanceof J.Literal) {
                    m = m.withTemplate(
                            JavaTemplate.builder(this::getCursor, "org.assertj.core.api.Assertions.fail(#{});")
                                    .javaParser(assertionsParser(ctx))
                                    .build(),
                            m.getCoordinates().replace(),
                            args.get(0)
                    );
                } else {
                    m = m.withTemplate(
                            JavaTemplate.builder(this::getCursor, "org.assertj.core.api.Assertions.fail(\"\", #{any()});")
                                    .javaParser(assertionsParser(ctx))
                                    .build(),
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

                m = m.withTemplate(JavaTemplate.builder(this::getCursor, templateBuilder.toString())
                                .javaParser(assertionsParser(ctx))
                                .build(),
                        m.getCoordinates().replace(),
                        args.toArray()
                );
            }

            doAfterVisit(new RemoveUnusedImports());
            doAfterVisit(new UnqualifiedMethodInvocations());
            return m;
        }

        private static class UnqualifiedMethodInvocations extends JavaIsoVisitor<ExecutionContext> {
            private static final MethodMatcher ASSERTJ_FAIL_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions" + " fail(..)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
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

                method = method.withTemplate(JavaTemplate.builder(this::getCursor, templateBuilder.toString())
                                .staticImports("org.assertj.core.api.Assertions" + ".fail")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                        .classpathFromResources(executionContext, "assertj-core-3.24.2")
                                        .build())
                                .build(),
                        method.getCoordinates().replace(),
                        arguments.toArray()
                );
                maybeAddImport("org.assertj.core.api.Assertions", "fail");
                return super.visitMethodInvocation(method, executionContext);
            }
        }
    }
}
