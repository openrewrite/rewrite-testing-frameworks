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
package org.openrewrite.java.testing.testng;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class TestNgAssertEqualsToAssertThat extends Recipe {

    @Override
    public String getDisplayName() {
        return "TestNG `assertEquals` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert TestNG-style `assertEquals()` to AssertJ's `assertThat().isEqualTo()`.";
    }

    private static final MethodMatcher TESTNG_ASSERT_METHOD = new MethodMatcher("org.testng.Assert assertEquals(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(TESTNG_ASSERT_METHOD), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (!TESTNG_ASSERT_METHOD.matches(method)) {
                    return method;
                }

                List<Expression> args = method.getArguments();
                Expression expected = args.get(1);
                Expression actual = args.get(0);

                //always add the import (even if not referenced)
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);

                // Remove import for "org.testng.Assert" if no longer used.
                maybeRemoveImport("org.testng.Assert");

                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{any()}).isEqualTo(#{any()});")
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                } else if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                    Expression message = args.get(2);
                    return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isEqualTo(#{any()});")
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .imports("java.util.function.Supplier")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(
                                    getCursor(),
                                    method.getCoordinates().replace(),
                                    actual,
                                    message,
                                    expected
                            );
                } else if (args.size() == 3) {
                    //always add the import (even if not referenced)
                    maybeAddImport("org.assertj.core.api.Assertions", "within", false);
                    return JavaTemplate.builder("assertThat(#{any()}).isCloseTo(#{any()}, within(#{any()}));")
                            .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected, args.get(2));

                }

                // The assertEquals is using a floating point with a delta argument and a message.
                Expression message = args.get(3);

                //always add the import (even if not referenced)
                maybeAddImport("org.assertj.core.api.Assertions", "within", false);
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isCloseTo(#{any()}, within(#{any()}));")
                        .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                        .imports("java.util.function.Supplier")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(
                                getCursor(),
                                method.getCoordinates().replace(),
                                actual,
                                message,
                                expected,
                                args.get(2)
                        );
            }

            private boolean isFloatingPointType(Expression expression) {

                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(expression.getType());
                if (fullyQualified != null) {
                    String typeName = fullyQualified.getFullyQualifiedName();
                    return "java.lang.Double".equals(typeName) || "java.lang.Float".equals(typeName);
                }

                JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
                return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
            }
        });
    }

}
