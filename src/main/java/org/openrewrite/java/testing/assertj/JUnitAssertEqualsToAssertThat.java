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
package org.openrewrite.java.testing.assertj;

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

public class JUnitAssertEqualsToAssertThat extends Recipe {

    private static final String JUNIT = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher ASSERT_EQUALS_MATCHER = new MethodMatcher(JUNIT + " assertEquals(..)", true);

    @Getter
    final String displayName = "JUnit `assertEquals` to AssertJ";

    @Getter
    final String description = "Convert JUnit-style `assertEquals()` to AssertJ's `assertThat().isEqualTo()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_EQUALS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_EQUALS_MATCHER.matches(mi)) {
                    return mi;
                }

                maybeRemoveImport(JUNIT);
                maybeAddImport(ASSERTJ, "assertThat", false);

                List<Expression> args = mi.getArguments();
                Expression expected = args.get(0);
                Expression actual = args.get(1);
                String assertThatArg = isTypeObject(actual) ? "assertThat((Object) #{any()})" : "assertThat(#{any()})";
                if (args.size() == 2) {
                    return JavaTemplate.builder(assertThatArg + ".isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, expected);
                }
                if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                    Expression message = args.get(2);
                    return JavaTemplate.builder(assertThatArg + ".as(#{any()}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .imports("java.util.function.Supplier")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected);
                }
                if (args.size() == 3) {
                    // When actual is integral but delta is floating-point, use isEqualTo instead of isCloseTo
                    // to avoid type mismatch (e.g. AbstractLongAssert.isCloseTo requires Offset<Long>, not Offset<Double>)
                    if (isIntegralType(actual)) {
                        return JavaTemplate.builder(assertThatArg + ".isEqualTo(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), actual, expected);
                    }
                    maybeAddImport(ASSERTJ, "within", false);
                    return JavaTemplate.builder(assertThatArg + ".isCloseTo(#{any()}, within(#{any()}));")
                            .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, expected, args.get(2));
                }

                // The assertEquals is using a floating point with a delta argument and a message.
                Expression message = args.get(3);
                if (isIntegralType(actual)) {
                    return JavaTemplate.builder(assertThatArg + ".as(#{any()}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .imports("java.util.function.Supplier")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected);
                }
                maybeAddImport(ASSERTJ, "within", false);
                return JavaTemplate.builder(assertThatArg + ".as(#{any()}).isCloseTo(#{any()}, within(#{any()}));")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .imports("java.util.function.Supplier")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected, args.get(2));
            }

            private boolean isTypeObject(Expression expression) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(expression.getType());
                return fq != null && "java.lang.Object".equals(fq.getFullyQualifiedName());
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
