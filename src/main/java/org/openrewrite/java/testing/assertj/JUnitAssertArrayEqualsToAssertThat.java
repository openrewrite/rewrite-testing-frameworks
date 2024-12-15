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

public class JUnitAssertArrayEqualsToAssertThat extends Recipe {

    private static final String JUNIT = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher ASSERT_ARRAY_EQUALS_MATCHER = new MethodMatcher(JUNIT + " assertArrayEquals(..)", true);

    @Override
    public String getDisplayName() {
        return "JUnit `assertArrayEquals` to assertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertArrayEquals()` to AssertJ's `assertThat().contains()` equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_ARRAY_EQUALS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation md = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_ARRAY_EQUALS_MATCHER.matches(md)) {
                    return md;
                }

                maybeAddImport(ASSERTJ, "assertThat", false);
                maybeRemoveImport(JUNIT);

                List<Expression> args = md.getArguments();
                Expression expected = args.get(0);
                Expression actual = args.get(1);
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, expected);
                }
                if (args.size() == 3 && isFloatingPointType(args.get(2))) {
                    maybeAddImport(ASSERTJ, "within", false);
                    // assert is using floating points with a delta and no message.
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()}, within(#{any()}));")
                            .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, expected, args.get(2));
                }
                if (args.size() == 3) {
                    Expression message = args.get(2);
                    return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any()}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, message, expected);
                }

                maybeAddImport(ASSERTJ, "within", false);

                // The assertEquals is using a floating point with a delta argument and a message.
                Expression message = args.get(3);
                return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any()}).containsExactly(#{anyArray()}, within(#{}));")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), md.getCoordinates().replace(), actual, message, expected, args.get(2));
            }

            /**
             * Returns true if the expression's type is either a primitive float/double or their object forms Float/Double
             *
             * @param expression The expression parsed from the original AST.
             * @return true if the type is a floating point number.
             */
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
