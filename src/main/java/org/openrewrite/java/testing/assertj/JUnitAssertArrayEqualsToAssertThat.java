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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class JUnitAssertArrayEqualsToAssertThat extends AbstractJUnitAssertToAssertThatRecipe {
    @Override
    public String getDisplayName() {
        return "JUnit `assertArrayEquals` to assertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertArrayEquals()` to AssertJ's `assertThat().contains()` equivalents.";
    }

    public JUnitAssertArrayEqualsToAssertThat () {
        super("assertArrayEquals(..)");
    }

    @Override
    protected JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config) {
        return new JUnitAssertionVisitor(config) {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation md = super.visitMethodInvocation(method, ctx);
                if (!config.matches(md)) {
                    return md;
                }

                maybeAddImport(ASSERTJ, "assertThat", false);
                maybeRemoveImport(config.getAssertionClass());

                List<Expression> args = md.getArguments();
                if (args.size() == 2) {
                    Expression expected = args.get(0);
                    Expression actual = args.get(1);
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERT_THAT)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, expected);
                }
                if (args.size() == 3 && isFloatingPointType(args.get(2))) {
                    Expression expected = args.get(0);
                    Expression actual = args.get(1);
                    Expression delta = args.get(2);
                    maybeAddImport(ASSERTJ, "within", false);
                    // assert is using floating points with a delta and no message.
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()}, within(#{any()}));")
                            .staticImports(ASSERT_THAT, ASSERT_WITHIN)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, expected, delta);
                }
                if (args.size() == 3) {
                    Expression message = config.isMessageIsFirstArg() ? args.get(0) : args.get(2);
                    Expression expected = config.isMessageIsFirstArg() ? args.get(1) : args.get(0);
                    Expression actual = config.isMessageIsFirstArg() ? args.get(2) : args.get(1);
                    return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any()}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERT_THAT)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, message, expected);
                }

                maybeAddImport(ASSERTJ, "within", false);

                // The assertEquals is using a floating point with a delta argument and a message.
                Expression message = config.isMessageIsFirstArg() ? args.get(0) : args.get(3);
                Expression expected = config.isMessageIsFirstArg() ? args.get(1) : args.get(0);
                Expression actual = config.isMessageIsFirstArg() ? args.get(2) : args.get(1);
                Expression delta = config.isMessageIsFirstArg() ? args.get(3) : args.get(2);
                return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any()}).containsExactly(#{anyArray()}, within(#{}));")
                        .staticImports(ASSERT_THAT, ASSERT_WITHIN)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                        .build()
                        .apply(getCursor(), md.getCoordinates().replace(), actual, message, expected, delta);
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
        };
    }
}
