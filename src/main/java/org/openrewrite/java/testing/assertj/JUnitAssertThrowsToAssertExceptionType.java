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

import java.util.List;
import java.util.function.UnaryOperator;

public class JUnitAssertThrowsToAssertExceptionType extends Recipe {

    private static final String JUNIT_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTIONS_FOR_CLASS_TYPES = "org.assertj.core.api.AssertionsForClassTypes";
    private static final MethodMatcher ASSERT_THROWS_MATCHER = new MethodMatcher(JUNIT_ASSERTIONS + " assertThrows(..)");

    @Override
    public String getDisplayName() {
        return "JUnit AssertThrows to AssertJ exceptionType";
    }

    @Override
    public String getDescription() {
        return "Convert `JUnit#AssertThrows` to `AssertJ#assertThatExceptionOfType` to allow for chained assertions on the thrown exception.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THROWS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_THROWS_MATCHER.matches(mi)) {
                    return mi;
                }

                boolean returnActual = !(getCursor().getParentTreeCursor().getValue() instanceof J.Block);

                maybeAddImport(ASSERTIONS_FOR_CLASS_TYPES, "assertThatExceptionOfType");
                maybeRemoveImport(JUNIT_ASSERTIONS + ".assertThrows");
                maybeRemoveImport(JUNIT_ASSERTIONS);

                List<Expression> args = mi.getArguments();

                UnaryOperator<String> decorator = returnActual ? code -> code + ".actual()" : UnaryOperator.identity();

                if (args.size() == 2) {
                    return JavaTemplate.builder(decorator.apply("assertThatExceptionOfType(#{any(java.lang.Class)}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})"))
                            .staticImports(ASSERTIONS_FOR_CLASS_TYPES + ".assertThatExceptionOfType")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(1));
                }

                return JavaTemplate.builder(decorator.apply("assertThatExceptionOfType(#{any()}).as(#{any()}).isThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)})"))
                        .staticImports(ASSERTIONS_FOR_CLASS_TYPES + ".assertThatExceptionOfType")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), args.get(2), args.get(1));
            }
        });
    }
}
