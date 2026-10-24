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

import java.util.List;

import static org.openrewrite.java.testing.assertj.JUnitDelta.floatingPointDeltaType;
import static org.openrewrite.java.testing.assertj.JUnitDelta.isIntegralType;
import static org.openrewrite.java.testing.assertj.JUnitDelta.isLiteralZero;
import static org.openrewrite.java.testing.assertj.JUnitDelta.widenedTo;

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
                if (isNullLiteral(actual) && !isNullLiteral(expected)) {
                    // `assertThat(null)` is an ambiguous method call; asserting on the other argument calls the same `equals`
                    expected = actual;
                    actual = args.get(0);
                }
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
                Expression delta = args.get(2);
                boolean dropDelta = isLiteralZero(delta) && isIntegralType(actual) && isIntegralType(expected);
                if (args.size() == 3) {
                    if (dropDelta) {
                        return JavaTemplate.builder("assertThat(#{any()}).isEqualTo(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), actual, expected);
                    }
                    maybeAddImport(ASSERTJ, "within", false);
                    return JavaTemplate.builder("assertThat(" + widenedTo(deltaType, actual) + ").isCloseTo(#{any()}, within(" + widenedTo(deltaType, delta) + "));")
                            .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, expected, delta);
                }

                Expression message = args.get(3);
                if (dropDelta) {
                    return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isEqualTo(#{any()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .imports("java.util.function.Supplier")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected);
                }
                maybeAddImport(ASSERTJ, "within", false);
                return JavaTemplate.builder("assertThat(" + widenedTo(deltaType, actual) + ").as(#{any()}).isCloseTo(#{any()}, within(" + widenedTo(deltaType, delta) + "));")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .imports("java.util.function.Supplier")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected, delta);
            }

            private boolean isNullLiteral(Expression expression) {
                return J.Literal.isLiteralValue(expression.unwrap(), null);
            }
        });
    }
}
