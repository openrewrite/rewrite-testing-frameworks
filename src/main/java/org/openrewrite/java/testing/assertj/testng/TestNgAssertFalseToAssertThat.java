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

public class TestNgAssertFalseToAssertThat
        extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit `assertFalse` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertFalse()` to AssertJ's `assertThat().isFalse()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.api.Assertions", false), new AssertFalseToAssertThatVisitor());
    }

    public static class AssertFalseToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private JavaParser.Builder<?, ?> assertionsParser;

        private JavaParser.Builder<?, ?> assertionsParser(ExecutionContext ctx) {
            if (assertionsParser == null) {
                assertionsParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24");
            }
            return assertionsParser;
        }

        private static final MethodMatcher JUNIT_ASSERT_FALSE = new MethodMatcher("org.junit.jupiter.api.Assertions" + " assertFalse(boolean, ..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_ASSERT_FALSE.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();
            Expression actual = args.get(0);

            if (args.size() == 1) {
                method = JavaTemplate.builder("assertThat(#{any(boolean)}).isFalse();")
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
                        JavaTemplate.builder("assertThat(#{any(boolean)}).as(#{any(String)}).isFalse();") :
                        JavaTemplate.builder("assertThat(#{any(boolean)}).as(#{any(java.util.function.Supplier)}).isFalse();");

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

            // Remove import for "org.junit.jupiter.api.Assertions" if no longer used.
            maybeRemoveImport("org.junit.jupiter.api.Assertions");

            return method;
        }
    }
}
