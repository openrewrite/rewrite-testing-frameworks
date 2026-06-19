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

import lombok.Getter;
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

public class TestNgAssertNotEqualsToAssertThat extends Recipe {

    @Getter
    final String displayName = "TestNG `assertNotEquals` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style `assertNotEquals()` to AssertJ's `assertThat().isNotEqualTo()`.";

    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher TESTNG_ASSERT_METHOD = new MethodMatcher("org.testng.Assert assertNotEquals(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(TESTNG_ASSERT_METHOD), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);
                if (!TESTNG_ASSERT_METHOD.matches(method)) {
                    return method;
                }

                List<Expression> args = method.getArguments();
                Expression actual = args.get(0);
                Expression expected = args.get(1);

                // Iterators are single-pass; `isNotEqualTo` would compare iterator identity which is never what TestNG
                // intends. Leave the call untouched rather than rewrite it wrong.
                if (TypeUtils.isAssignableTo("java.util.Iterator", actual.getType())) {
                    return method;
                }

                maybeRemoveImport("org.testng.Assert");
                maybeAddImport(ASSERTJ, "assertThat", false);

                if (args.size() == 2) {
                    method = JavaTemplate.builder("assertThat(#{any()}).isNotEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                } else if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                    Expression message = args.get(2);
                    method = JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isNotEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected);
                } else if (args.size() == 3) {
                    if (isIntegralType(actual)) {
                        method = JavaTemplate.builder("assertThat(#{any()}).isNotEqualTo(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                    } else {
                        maybeAddImport(ASSERTJ, "within", false);
                        method = JavaTemplate.builder("assertThat(#{any()}).isNotCloseTo(#{any()}, within(#{any()}));")
                                .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), actual, expected, args.get(2));
                    }
                } else {
                    Expression message = args.get(3);
                    if (isIntegralType(actual)) {
                        method = JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isNotEqualTo(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected);
                    } else {
                        maybeAddImport(ASSERTJ, "within", false);
                        method = JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isNotCloseTo(#{any()}, within(#{any()}));")
                                .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected, args.get(2));
                    }
                }

                return method;
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

            private boolean isIntegralType(Expression expression) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(expression.getType());
                if (fq != null) {
                    String typeName = fq.getFullyQualifiedName();
                    return "java.lang.Long".equals(typeName) || "java.lang.Integer".equals(typeName);
                }
                JavaType.Primitive p = TypeUtils.asPrimitive(expression.getType());
                return p == JavaType.Primitive.Long || p == JavaType.Primitive.Int;
            }
        });
    }
}
