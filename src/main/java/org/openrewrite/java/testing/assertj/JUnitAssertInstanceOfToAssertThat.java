/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
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

public class JUnitAssertInstanceOfToAssertThat extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit `assertInstanceOf` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertInstanceOf()` to AssertJ's `assertThat().isInstanceOf()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.api.Assertions", false),
                new AssertInstanceOfToToAssertThatVisitor());
    }

    public static class AssertInstanceOfToToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher JUNIT_ASSERT_NULL_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertInstanceOf(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_ASSERT_NULL_MATCHER.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();

            if (args.size() == 2) {
                Expression expectedType = args.get(0);
                Expression actualValue = args.get(1);

                method = JavaTemplate.builder("assertThat(#{any()}).isInstanceOf(#{any()});")
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().replace(),
                                actualValue,
                                expectedType
                        );
            } else if (args.size() == 3) {
                Expression expectedType = args.get(0);
                Expression actualValue = args.get(1);
                Expression messageOrSupplier = args.get(2);

                JavaTemplate.Builder template = TypeUtils.isString(messageOrSupplier.getType()) ?
                        JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isInstanceOf(#{any()});") :
                        JavaTemplate.builder("assertThat(#{any()}).as(#{any(java.util.function.Supplier)}).isInstanceOf(#{any()});");

                method = template
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(assertionsParser(ctx))
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().replace(),
                                actualValue,
                                messageOrSupplier,
                                expectedType
                        );
            }

            maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);
            maybeRemoveImport("org.junit.jupiter.api.Assertions");

            return method;
        }
    }
}
