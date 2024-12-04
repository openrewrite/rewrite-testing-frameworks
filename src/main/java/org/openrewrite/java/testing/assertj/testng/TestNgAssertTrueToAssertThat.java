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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class TestNgAssertTrueToAssertThat
        extends Recipe {
    @Override
    public String getDisplayName() {
        return "TestNG `assertTrue` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert TestNG-style `assertTrue()` to AssertJ's `assertThat().isTrue()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.testng.Assert", false), new AssertTrueToAssertThatVisitor());
    }

    public static class AssertTrueToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private JavaParser.Builder<?, ?> assertionsParser;

        private JavaParser.Builder<?, ?> assertionsParser(ExecutionContext ctx) {
            if (assertionsParser == null) {
                assertionsParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24");
            }
            return assertionsParser;
        }

        private static final MethodMatcher TESTNG_ASSERT_TRUE = new MethodMatcher("org.testng.Assert" + " assertTrue(boolean, ..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!TESTNG_ASSERT_TRUE.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();
            Expression actual = args.get(0);

            if (args.size() == 1) {
                method = JavaTemplate.builder("assertThat(#{any(boolean)}).isTrue();")
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(assertionsParser(ctx))
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().replace(),
                                actual
                        );
            } else {
                Expression message = args.get(1);

                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        JavaTemplate.builder("assertThat(#{any(boolean)}).as(#{any(String)}).isTrue();") :
                        JavaTemplate.builder("assertThat(#{any(boolean)}).as(#{any(java.util.function.Supplier)}).isTrue();");

                method = template
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(assertionsParser(ctx))
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().replace(),
                                actual,
                                message
                        );
            }

            //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat" (even if not referenced)
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);

            // Remove import for "org.testng.Assert" if no longer used.
            maybeRemoveImport("org.testng.Assert");

            return method;
        }
    }
}
