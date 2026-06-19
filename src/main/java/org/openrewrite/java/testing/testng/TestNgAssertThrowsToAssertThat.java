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

import java.util.List;
import java.util.Optional;

public class TestNgAssertThrowsToAssertThat extends Recipe {

    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher ASSERT_THROWS_MATCHER = new MethodMatcher("org.testng.Assert assertThrows(..)");
    private static final MethodMatcher EXPECT_THROWS_MATCHER = new MethodMatcher("org.testng.Assert expectThrows(..)");

    @Getter
    final String displayName = "TestNG `assertThrows`/`expectThrows` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style `assertThrows()` and `expectThrows()` to AssertJ's " +
                               "`assertThatExceptionOfType().isThrownBy()` (or `assertThatThrownBy()` when no exception type is given) " +
                               "to allow for chained assertions on the thrown exception.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(ASSERT_THROWS_MATCHER), new UsesMethod<>(EXPECT_THROWS_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        boolean isAssert = ASSERT_THROWS_MATCHER.matches(mi);
                        boolean isExpect = EXPECT_THROWS_MATCHER.matches(mi);
                        if (!isAssert && !isExpect) {
                            return mi;
                        }

                        List<Expression> args = mi.getArguments();

                        // assertThrows(ThrowingRunnable) — no exception type, so no chained type assertion possible.
                        if (args.size() == 1) {
                            maybeRemoveImport("org.testng.Assert");
                            maybeRemoveImport("org.testng.Assert.assertThrows");
                            maybeAddImport(ASSERTJ, "assertThatThrownBy");
                            return JavaTemplate.builder("assertThatThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})")
                                    .staticImports(ASSERTJ + ".assertThatThrownBy")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                    .build()
                                    .apply(getCursor(), mi.getCoordinates().replace(), args.get(0));
                        }

                        // assertThrows(Class, ThrowingRunnable) or expectThrows(Class, ThrowingRunnable)
                        Optional<Boolean> hasReturnType = hasReturnType();
                        if (!hasReturnType.isPresent()) {
                            return mi;
                        }

                        maybeRemoveImport("org.testng.Assert");
                        maybeRemoveImport("org.testng.Assert.assertThrows");
                        maybeRemoveImport("org.testng.Assert.expectThrows");
                        maybeAddImport(ASSERTJ, "assertThatExceptionOfType");

                        String code = "assertThatExceptionOfType(#{any(java.lang.Class)}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})";
                        if (hasReturnType.get()) {
                            code += ".actual()";
                        }
                        return JavaTemplate.builder(code)
                                .staticImports(ASSERTJ + ".assertThatExceptionOfType")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(1));
                    }

                    private Optional<Boolean> hasReturnType() {
                        Object parent = getCursor().getParentTreeCursor().getValue();

                        // These all have the method invocation return something
                        if (parent instanceof J.Assignment ||
                            parent instanceof J.VariableDeclarations ||
                            parent instanceof J.VariableDeclarations.NamedVariable ||
                            parent instanceof J.Return ||
                            parent instanceof J.Ternary) {
                            return Optional.of(true);
                        }

                        if (parent instanceof J.Block) {
                            return Optional.of(false);
                        }

                        // Unknown parent type so not supported
                        return Optional.empty();
                    }
                });
    }
}
