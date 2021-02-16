/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junitassertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This is a refactoring visitor that will convert JUnit-style fail() to assertJ's fail().
 * <p>
 * This visitor only supports the migration of the following JUnit 5 fail() methods:
 *
 * <PRE>
 * fail()                                  ->   fail("")
 * fail(String message)                    ->   fail(String message)
 * fail(String message, Throwable cause)   ->   fail(String message, Throwable cause)
 * fail(Throwable cause)                   ->   fail("", Throwable cause)
 * </PRE>
 * <p>
 * Note: There is an additional method signature in JUnit that accepts a StringSupplier as an argument. Attempts
 * to map this signature into assertJ's model obfuscates the original assertion.
 */
public class JUnitFailToAssertJFail extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JUnitFailToAssertJFailVisitor();
    }

    public static class JUnitFailToAssertJFailVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
        private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";

        /**
         * This matcher finds the junit methods that will be migrated by this visitor.
         */
        private static final MethodMatcher JUNIT_FAIL_MATCHER = new MethodMatcher(
                JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " fail(..)"
        );

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_FAIL_MATCHER.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();

            if (args.size() == 1) {
                // fail(), fail(String), fail(Supplier<String>), fail(Throwable)
                if (args.get(0) instanceof J.Empty) {
                    method = method.withTemplate(
                            template("org.assertj.core.api.Assertions.fail(\"\");")
                                    .build(),
                            method.getCoordinates().replace()
                    );
                } else if (args.get(0) instanceof J.Literal) {
                    method = method.withTemplate(
                            template("org.assertj.core.api.Assertions.fail(#{});")
                                    .build(),
                            method.getCoordinates().replace(),
                            args.get(0)
                    );
                } else {
                    method = method.withTemplate(
                            template("org.assertj.core.api.Assertions.fail(\"\", #{});")
                                    .build(),
                            method.getCoordinates().replace(),
                            args.get(0)
                    );
                }
            } else {
                // fail(String, Throwable)
                method = method.withTemplate(
                        template("org.assertj.core.api.Assertions.fail(#{});")
                                .build(),
                        method.getCoordinates().replace(),
                        args.stream().map(Tree::print).collect(Collectors.joining(","))
                );
            }
            doAfterVisit(new RemoveUnusedImports());
            doAfterVisit(new UnqualifyMethodInvocations());
            return method;
        }

        private static class UnqualifyMethodInvocations extends JavaIsoVisitor<ExecutionContext> {

            private static final MethodMatcher ASSERTJ_FAIL_MATCHER = new MethodMatcher(
                    ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME + " fail(..)"
            );

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (!ASSERTJ_FAIL_MATCHER.matches(method)) {
                    return method;
                }
                String args = method.getArguments().stream().map(Tree::print).collect(Collectors.joining(","));

                method = method.withTemplate(
                        template("fail(#{});")
                                .staticImports(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME + ".fail")
                                .doAfterVariableSubstitution(s -> System.out.println("after vars: " + s))
                                .doBeforeParseTemplate(s -> System.out.println("before parse: " + s))
                                .build(),
                        method.getCoordinates().replace(),
                        args
                );
                maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, "fail");
                return super.visitMethodInvocation(method, executionContext);
            }
        }
    }
}
