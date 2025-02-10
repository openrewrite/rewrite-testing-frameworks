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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
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
            this.methodMatcher = new MethodMatcher(assertionClass + " assertArrayEquals(..)", true);
            this.messageIsFirstArg = messageIsFirstArg;
            this.visitor = Preconditions.check(new UsesMethod<>(methodMatcher), new JUnitAssertArrayEqualsToAssertThatVisitor(this));
        }
    }

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

    private static class JUnitAssertArrayEqualsToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final AssertionConfig junitAssertionConfig;

        private JUnitAssertArrayEqualsToAssertThatVisitor(AssertionConfig junitAssertionConfig) {
            this.junitAssertionConfig = junitAssertionConfig;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation md = super.visitMethodInvocation(method, ctx);
            if (!junitAssertionConfig.methodMatcher.matches(md)) {
                return md;
            }

            maybeAddImport(ASSERTJ, "assertThat", false);
            maybeRemoveImport(junitAssertionConfig.assertionClass);

            List<Expression> args = md.getArguments();
            if (args.size() == 2) {
                Expression expected = args.get(0);
                Expression actual = args.get(1);
                return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()});")
                        .staticImports(ASSERTJ + ".assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), md.getCoordinates().replace(), actual, expected);
            }
            if (args.size() == 3 && isFloatingPointType(args.get(2))) {
                Expression expected = junitAssertionConfig.messageIsFirstArg ? args.get(1) : args.get(0);
                Expression actual = junitAssertionConfig.messageIsFirstArg ? args.get(2) : args.get(1);
                Expression delta = junitAssertionConfig.messageIsFirstArg ? args.get(3) : args.get(2);
                maybeAddImport(ASSERTJ, "within", false);
                // assert is using floating points with a delta and no message.
                return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()}, within(#{any()}));")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), md.getCoordinates().replace(), actual, expected, delta);
            }
            if (args.size() == 3) {
                Expression message = junitAssertionConfig.messageIsFirstArg ? args.get(0) : args.get(2);
                Expression expected = junitAssertionConfig.messageIsFirstArg ? args.get(1) : args.get(0);
                Expression actual = junitAssertionConfig.messageIsFirstArg ? args.get(2) : args.get(1);
                return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any()}).containsExactly(#{anyArray()});")
                        .staticImports(ASSERTJ + ".assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), md.getCoordinates().replace(), actual, message, expected);
            }

            maybeAddImport(ASSERTJ, "within", false);

            // The assertEquals is using a floating point with a delta argument and a message.
            Expression message = junitAssertionConfig.messageIsFirstArg ? args.get(0) : args.get(3);
            Expression expected = junitAssertionConfig.messageIsFirstArg ? args.get(1) : args.get(0);
            Expression actual = junitAssertionConfig.messageIsFirstArg ? args.get(2) : args.get(1);
            Expression delta = junitAssertionConfig.messageIsFirstArg ? args.get(3) : args.get(2);
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
    }
}
