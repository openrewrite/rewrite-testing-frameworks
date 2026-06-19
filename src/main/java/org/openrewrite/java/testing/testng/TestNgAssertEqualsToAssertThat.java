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

public class TestNgAssertEqualsToAssertThat extends Recipe {

    @Getter
    final String displayName = "TestNG `assertEquals` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style `assertEquals()` to AssertJ's `assertThat().isEqualTo()`, " +
                               "using element-wise assertions (`containsExactly`/`containsExactlyElementsOf`) for arrays and collections.";

    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher TESTNG_ASSERT_METHOD = new MethodMatcher("org.testng.Assert assertEquals(..)");

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
                JavaType actualType = actual.getType();

                maybeRemoveImport("org.testng.Assert");
                maybeAddImport(ASSERTJ, "assertThat", false);

                if (TypeUtils.asArray(actualType) != null) {
                    return arrayAssertion(method, args, actual, expected, ctx);
                }
                // Iterators are single-pass; `isEqualTo` would compare iterator identity. Convert through
                // `toIterable()` and wrap the expected iterator in an `Iterable` lambda (`() -> expected`) so the
                // comparison stays element-wise, matching TestNG semantics.
                if (TypeUtils.isAssignableTo("java.util.Iterator", actualType)) {
                    return iteratorAssertion(method, args, actual, expected, ctx);
                }
                if (TypeUtils.isAssignableTo("java.util.Set", actualType)) {
                    return elementsOfAssertion(method, args, actual, expected, "containsExactlyInAnyOrderElementsOf", ctx);
                }
                if (TypeUtils.isAssignableTo("java.lang.Iterable", actualType)) {
                    return elementsOfAssertion(method, args, actual, expected, "containsExactlyElementsOf", ctx);
                }
                return scalarAssertion(method, args, actual, expected, ctx);
            }

            private J.MethodInvocation arrayAssertion(J.MethodInvocation method, List<Expression> args, Expression actual, Expression expected, ExecutionContext ctx) {
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                }
                if (args.size() == 3 && isFloatingPointType(args.get(2))) {
                    maybeAddImport(ASSERTJ, "within", false);
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()}, within(#{any()}));")
                            .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected, args.get(2));
                }
                if (args.size() == 3) {
                    return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any(String)}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, args.get(2), expected);
                }
                maybeAddImport(ASSERTJ, "within", false);
                return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any(String)}).containsExactly(#{anyArray()}, within(#{any()}));")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, args.get(3), expected, args.get(2));
            }

            private J.MethodInvocation elementsOfAssertion(J.MethodInvocation method, List<Expression> args, Expression actual, Expression expected, String assertion, ExecutionContext ctx) {
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{any()})." + assertion + "(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                }
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)})." + assertion + "(#{any()});")
                        .staticImports(ASSERTJ + ".assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, args.get(2), expected);
            }

            private J.MethodInvocation iteratorAssertion(J.MethodInvocation method, List<Expression> args, Expression actual, Expression expected, ExecutionContext ctx) {
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{any()}).toIterable().containsExactlyElementsOf(() -> #{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                }
                return JavaTemplate.builder("assertThat(#{any()}).toIterable().as(#{any(String)}).containsExactlyElementsOf(() -> #{any()});")
                        .staticImports(ASSERTJ + ".assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, args.get(2), expected);
            }

            private J.MethodInvocation scalarAssertion(J.MethodInvocation method, List<Expression> args, Expression actual, Expression expected, ExecutionContext ctx) {
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{any()}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                }
                if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                    Expression message = args.get(2);
                    return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected);
                }
                if (args.size() == 3) {
                    // When actual is integral but delta is floating-point, use isEqualTo instead of isCloseTo
                    // to avoid a type mismatch (e.g. AbstractIntegerAssert.isCloseTo requires Offset<Integer>, not Offset<Double>).
                    if (isIntegralType(actual)) {
                        return JavaTemplate.builder("assertThat(#{any()}).isEqualTo(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                    }
                    maybeAddImport(ASSERTJ, "within", false);
                    return JavaTemplate.builder("assertThat(#{any()}).isCloseTo(#{any()}, within(#{any()}));")
                            .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected, args.get(2));
                }

                // The assertEquals is using a floating point with a delta argument and a message.
                Expression message = args.get(3);
                if (isIntegralType(actual)) {
                    return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected);
                }
                maybeAddImport(ASSERTJ, "within", false);
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isCloseTo(#{any()}, within(#{any()}));")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected, args.get(2));
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
