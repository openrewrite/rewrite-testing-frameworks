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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
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

import static org.openrewrite.java.testing.assertj.JUnitAssertionConfig.JUNIT4_ASSERT;
import static org.openrewrite.java.testing.assertj.JUnitAssertionConfig.JUNIT5_ASSERT;

public class JUnitAssertEqualsToAssertThat extends AbstractJUnitAssertToAssertThatRecipe {

    @Override
    public String getDisplayName() {
        return "JUnit `assertEquals` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertEquals()` to AssertJ's `assertThat().isEqualTo()`.";
    }

    @Override
    protected JUnitAssertionConfig getJunit4Config() {
        return new JUnitAssertionConfig.JUnit4(new MethodMatcher(JUNIT4_ASSERT + " assertEquals(..)"));
    }

    @Override
    protected JUnitAssertionConfig getJunit5Config() {
        return new JUnitAssertionConfig.JUnit5(new MethodMatcher(JUNIT5_ASSERT + " assertEquals(..)"));
    }

    @Override
    protected JUnitAssertionVisitor getJUnitAssertionVisitor(JUnitAssertionConfig config) {
        return new JUnitAssertEqualsToAssertThatVisitor(config);
    }

    private static class JUnitAssertEqualsToAssertThatVisitor extends JUnitAssertionVisitor {

        private JUnitAssertEqualsToAssertThatVisitor(JUnitAssertionConfig junitAssertionConfig) {
            super(junitAssertionConfig);
        }

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
                return JavaTemplate.builder("assertThat(#{any()}).isEqualTo(#{any()});")
                    .staticImports(ASSERTJ + ".assertThat")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), actual, expected);
            }
            if (args.size() == 3 && !isFloatingPointType(config.isMessageIsFirstArg() ? args.get(0) : args.get(2))) {
                Expression message = config.isMessageIsFirstArg() ? args.get(0) : args.get(2);
                Expression expected = config.isMessageIsFirstArg() ? args.get(1) : args.get(0);
                Expression actual = config.isMessageIsFirstArg() ? args.get(2) : args.get(1);
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isEqualTo(#{any()});")
                    .staticImports(ASSERTJ + ".assertThat")
                    .imports("java.util.function.Supplier")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected);
            }
            if (args.size() == 3) {
                Expression expected = args.get(0);
                Expression actual = args.get(1);
                maybeAddImport(ASSERTJ, "within", false);
                return JavaTemplate.builder("assertThat(#{any()}).isCloseTo(#{any()}, within(#{any()}));")
                    .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), actual, expected, args.get(2));
            }

            maybeAddImport(ASSERTJ, "within", false);

            // The assertEquals is using a floating point with a delta argument and a message.
            Expression message = config.isMessageIsFirstArg() ? args.get(0) : args.get(3);
            Expression expected = config.isMessageIsFirstArg() ? args.get(1) : args.get(0);
            Expression actual = config.isMessageIsFirstArg() ? args.get(2) : args.get(1);
            Expression delta = config.isMessageIsFirstArg() ? args.get(3) : args.get(2);
            return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isCloseTo(#{any()}, within(#{any()}));")
                .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                .imports("java.util.function.Supplier")
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                .build()
                .apply(getCursor(), mi.getCoordinates().replace(), actual, message, expected, delta);
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
    }
}
