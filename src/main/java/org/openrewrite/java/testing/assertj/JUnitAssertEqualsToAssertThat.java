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

public class JUnitAssertEqualsToAssertThat extends Recipe {
    private static final String ASSERTJ = "org.assertj.core.api.Assertions";

    public enum AssertionConfig {
        JUNIT4("org.junit.Assert", true),
        JUNIT5("org.junit.jupiter.api.Assertions", false);

        private final String assertionClass;
        private final MethodMatcher methodMatcher;
        private final boolean messageIsFirstArg;
        private final TreeVisitor<?, ExecutionContext> visitor;

        AssertionConfig(String assertionClass, boolean messageIsFirstArg) {
            this.assertionClass = assertionClass;
            this.methodMatcher = new MethodMatcher(assertionClass + " assertEquals(..)", true);
            this.messageIsFirstArg = messageIsFirstArg;
            this.visitor = Preconditions.check(new UsesMethod<>(methodMatcher), new JUnitAssertEqualsToAssertThatVisitor(this));
        }
    }

    @Override
    public String getDisplayName() {
        return "JUnit `assertEquals` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertEquals()` to AssertJ's `assertThat().isEqualTo()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext> () {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                for (AssertionConfig assertion : AssertionConfig.values()) {
                    if (new UsesMethod<>(assertion.methodMatcher).isAcceptable(sourceFile, ctx)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                for (AssertionConfig assertion : AssertionConfig.values()) {
                    tree = assertion.visitor.visit(tree, ctx);
                }
                return tree;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx, Cursor parent) {
                for (AssertionConfig assertion : AssertionConfig.values()) {
                    tree = assertion.visitor.visit(tree, ctx, parent);
                }
                return tree;
            }

        };
    }

    private static class JUnitAssertEqualsToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final AssertionConfig junitAssertionConfig;

        private JUnitAssertEqualsToAssertThatVisitor(AssertionConfig junitAssertionConfig) {
            this.junitAssertionConfig = junitAssertionConfig;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (!junitAssertionConfig.methodMatcher.matches(mi)) {
                return mi;
            }

            maybeAddImport(ASSERTJ, "assertThat", false);
            maybeRemoveImport(junitAssertionConfig.assertionClass);

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
            if (args.size() == 3 && !isFloatingPointType(junitAssertionConfig.messageIsFirstArg ? args.get(0) : args.get(2))) {
                Expression message = junitAssertionConfig.messageIsFirstArg ? args.get(0) : args.get(2);
                Expression expected = junitAssertionConfig.messageIsFirstArg ? args.get(1) : args.get(0);
                Expression actual = junitAssertionConfig.messageIsFirstArg ? args.get(2) : args.get(1);
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
            Expression message = junitAssertionConfig.messageIsFirstArg ? args.get(0) : args.get(3);
            Expression expected = junitAssertionConfig.messageIsFirstArg ? args.get(1) : args.get(0);
            Expression actual = junitAssertionConfig.messageIsFirstArg ? args.get(2) : args.get(1);
            Expression delta = junitAssertionConfig.messageIsFirstArg ? args.get(3) : args.get(2);
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
