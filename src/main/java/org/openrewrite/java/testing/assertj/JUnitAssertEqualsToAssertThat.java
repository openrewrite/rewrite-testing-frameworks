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
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{any()}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, expected);
                }
                JavaType.Primitive deltaType = floatingPointDeltaType(mi);
                if (args.size() == 3 && deltaType == null) {
                    Expression message = args.get(2);
                    return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isEqualTo(#{any()});")
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
                        return JavaTemplate.builder("assertThat(#{any()}).isEqualTo(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), actual, expected);
                    }
                    maybeAddImport(ASSERTJ, "within", false);
                    return JavaTemplate.builder("assertThat(#{any()}).isCloseTo(#{any()}, " + withinExpression(deltaType, args.get(2)) + ");")
                            .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, expected, args.get(2));
                }

                // The assertEquals is using a floating point with a delta argument and a message.
                Expression message = args.get(3);
                if (isIntegralType(actual)) {
                    return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .imports("java.util.function.Supplier")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected);
                }
                maybeAddImport(ASSERTJ, "within", false);
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isCloseTo(#{any()}, " + withinExpression(deltaType, args.get(2)) + ");")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .imports("java.util.function.Supplier")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected, args.get(2));
            }

            private String withinExpression(JavaType.Primitive paramType, Expression delta) {
                // When the delta argument's expression type is narrower than the resolved parameter type
                // (e.g. literal `0` widened to `float` at the call site), insert an explicit cast so that
                // AssertJ resolves `within(...)` to the matching Offset<T> overload.
                JavaType argType = delta.getType();
                if (TypeUtils.asPrimitive(argType) == paramType) {
                    return "within(#{any()})";
                }
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(argType);
                if (fq != null && paramType.getClassName().equals(fq.getFullyQualifiedName())) {
                    return "within(#{any()})";
                }
                return "within((" + paramType.getKeyword() + ") #{any()})";
            }

            private JavaType.Primitive floatingPointDeltaType(J.MethodInvocation mi) {
                // Inspect the resolved method's third parameter type, not the argument's expression type:
                // a literal like `0` passed to `assertEquals(float, float, float)` is widened to float by the compiler.
                if (mi.getMethodType() == null || mi.getMethodType().getParameterTypes().size() < 3) {
                    return null;
                }
                JavaType.Primitive primitive = TypeUtils.asPrimitive(mi.getMethodType().getParameterTypes().get(2));
                return primitive == JavaType.Primitive.Double || primitive == JavaType.Primitive.Float ? primitive : null;
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
