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

import java.util.List;
import java.util.Optional;

public class JUnitAssertThrowsToAssertExceptionType extends Recipe {

    private static final String JUNIT_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTIONS_FOR_CLASS_TYPES = "org.assertj.core.api.Assertions";
    private static final MethodMatcher ASSERT_THROWS_MATCHER = new MethodMatcher(JUNIT_ASSERTIONS + " assertThrows(..)");

    @Getter
    final String displayName = "JUnit AssertThrows to AssertJ exceptionType";

    @Getter
    final String description = "Convert `JUnit#AssertThrows` to `AssertJ#assertThatExceptionOfType` to allow for chained assertions on the thrown exception.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THROWS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_THROWS_MATCHER.matches(mi)) {
                    return mi;
                }

                Optional<Boolean> hasReturnType = hasReturnType();

                if (!hasReturnType.isPresent()) {
                    return mi;
                }

                boolean returnActual = hasReturnType.get();

                maybeRemoveImport(JUNIT_ASSERTIONS);
                maybeRemoveImport(JUNIT_ASSERTIONS + ".assertThrows");
                maybeAddImport(ASSERTIONS_FOR_CLASS_TYPES, "assertThatExceptionOfType");

                List<Expression> args = mi.getArguments();

                if (args.size() == 2) {
                    String code = "assertThatExceptionOfType(#{any(java.lang.Class)}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})";
                    if (returnActual) {
                        code += ".actual()";
                    }
                    return JavaTemplate.builder(code)
                            .staticImports(ASSERTIONS_FOR_CLASS_TYPES + ".assertThatExceptionOfType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(1));
                }

                String code = "assertThatExceptionOfType(#{any()}).as(#{any()}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})";
                if (returnActual) {
                    code += ".actual()";
                }
                return JavaTemplate.builder(code)
                        .staticImports(ASSERTIONS_FOR_CLASS_TYPES + ".assertThatExceptionOfType")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(2), args.get(1));
            }

            /**
             * Check if there is a return type which would indicate the need for using
             * {@code .actual()} in the AssertJ call.
             * <p>
             * If the presence of a return type could not be determined then {@code Optional.empty()} is returned
             * and the current {@code J.MethodInvocation} should be used without further changes.
             *
             * @return {@code Optional.of(true)} if there is a return type otherwise {@code Optional.of(false)}.
             * If it could not be determined then {@code Optional.empty()}.
             */
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
