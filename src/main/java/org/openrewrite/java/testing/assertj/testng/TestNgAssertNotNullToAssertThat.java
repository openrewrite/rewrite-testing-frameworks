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

public class TestNgAssertNotNullToAssertThat extends Recipe {

    @Override
    public String getDisplayName() {
        return "TestNG `assertNotNull` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert TestNG-style `assertNotNull()` to AssertJ's `assertThat().isNotNull()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.testng.Assert", false), new JavaIsoVisitor<ExecutionContext>() {
            private final MethodMatcher TESTNG_ASSERT_NOT_NULL_MATCHER = new MethodMatcher("org.testng.Assert" + " assertNotNull(..)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (!TESTNG_ASSERT_NOT_NULL_MATCHER.matches(method)) {
                    return method;
                }

                List<Expression> args = method.getArguments();
                Expression actual = args.get(0);

                if (args.size() == 1) {
                    method = JavaTemplate.builder("assertThat(#{any()}).isNotNull();")
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "assertj-core-3.24"))
                            .build()
                            .apply(
                                    getCursor(),
                                    method.getCoordinates().replace(),
                                    actual
                            );

                } else {
                    Expression message = args.get(1);

                    JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                            JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isNotNull();") :
                            JavaTemplate.builder("assertThat(#{any()}).as(#{any(java.util.function.Supplier)}).isNotNull();");

                    method = template
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "assertj-core-3.24"))
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

                //And if there are no longer references to the TestNG assertions class, we can remove the import.
                maybeRemoveImport("org.testng.Assert");

                return method;
            }
        });
    }

}
