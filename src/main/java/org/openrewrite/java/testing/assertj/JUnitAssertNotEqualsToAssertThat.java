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

public class JUnitAssertNotEqualsToAssertThat extends AbstractJUnitAssertToAssertThatRecipe {

    private static final String JUNIT = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher ASSERT_NOT_EQUALS_MATCHER = new MethodMatcher(JUNIT + " assertNotEquals(..)", true);

    @Override
    public String getDisplayName() {
        return "JUnit `assertNotEquals` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertNotEquals()` to AssertJ's `assertThat().isNotEqualTo()`.";
    }

    public JUnitAssertNotEqualsToAssertThat() {
        super("assertNotEquals(..)");
    }

    @Override
    protected JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config) {
        return new JUnitAssertionVisitor(config) {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!config.getMethodMatcher().matches(mi)) {
                    return mi;
                }

                maybeAddImport(ASSERTJ, "assertThat", false);
                maybeRemoveImport(config.getAssertionClass());

                List<Expression> args = mi.getArguments();
                if (args.size() == 2) {
                    Expression expected = args.get(0);
                    Expression actual = args.get(1);
                    return JavaTemplate.builder("assertThat(#{any()}).isNotEqualTo(#{any()});")
                            .staticImports(ASSERT_THAT)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, expected);
                }
                if (args.size() == 3 && isFloatingPointType(args.get(2))) {
                    Expression expected = args.get(0);
                    Expression actual = args.get(1);
                    Expression delta = args.get(2);
                    maybeAddImport(ASSERTJ, "within", false);
                    return JavaTemplate.builder("assertThat(#{any()}).isNotCloseTo(#{any()}, within(#{any()}));")
                            .staticImports(ASSERT_THAT, ASSERT_WITHIN)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, expected, delta);
                }
                if (args.size() == 3) {
                    Expression message = config.isMessageIsFirstArg() ? args.get(0) : args.get(2);
                    Expression expected = config.isMessageIsFirstArg() ? args.get(1) : args.get(0);
                    Expression actual = config.isMessageIsFirstArg() ? args.get(2) : args.get(1);
                    return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isNotEqualTo(#{any()});")
                            .staticImports(ASSERT_THAT)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected);
                }

                maybeAddImport(ASSERTJ, "within", false);

                Expression message = config.isMessageIsFirstArg() ? args.get(0) : args.get(3);
                Expression expected = config.isMessageIsFirstArg() ? args.get(1) : args.get(0);
                Expression actual = config.isMessageIsFirstArg() ? args.get(2) : args.get(1);
                Expression delta = config.isMessageIsFirstArg() ? args.get(3) : args.get(2);
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isNotCloseTo(#{any()}, within(#{any()}));")
                        .staticImports(ASSERT_THAT, ASSERT_WITHIN)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, ASSERTJ_CORE))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected, delta);
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
        };
    }
}
