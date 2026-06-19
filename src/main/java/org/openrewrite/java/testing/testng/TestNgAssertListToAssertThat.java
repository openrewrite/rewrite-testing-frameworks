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

public class TestNgAssertListToAssertThat extends Recipe {

    @Getter
    final String displayName = "TestNG `assertList*` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style `assertListContainsObject`/`assertListNotContainsObject` to " +
                               "AssertJ's `assertThat().contains()`/`.doesNotContain()`, and " +
                               "`assertListContains`/`assertListNotContains` (predicate-based) to `.anyMatch()`/`.noneMatch()`.";

    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher CONTAINS_OBJECT = new MethodMatcher("org.testng.Assert assertListContainsObject(..)");
    private static final MethodMatcher NOT_CONTAINS_OBJECT = new MethodMatcher("org.testng.Assert assertListNotContainsObject(..)");
    private static final MethodMatcher CONTAINS = new MethodMatcher("org.testng.Assert assertListContains(..)");
    private static final MethodMatcher NOT_CONTAINS = new MethodMatcher("org.testng.Assert assertListNotContains(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(CONTAINS_OBJECT),
                        new UsesMethod<>(NOT_CONTAINS_OBJECT),
                        new UsesMethod<>(CONTAINS),
                        new UsesMethod<>(NOT_CONTAINS)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        method = super.visitMethodInvocation(method, ctx);
                        String assertion;
                        if (CONTAINS_OBJECT.matches(method)) {
                            assertion = "contains";
                        } else if (NOT_CONTAINS_OBJECT.matches(method)) {
                            assertion = "doesNotContain";
                        } else if (CONTAINS.matches(method)) {
                            assertion = "anyMatch";
                        } else if (NOT_CONTAINS.matches(method)) {
                            assertion = "noneMatch";
                        } else {
                            return method;
                        }

                        maybeRemoveImport("org.testng.Assert");
                        maybeAddImport(ASSERTJ, "assertThat", false);

                        List<Expression> args = method.getArguments();
                        Expression list = args.get(0);
                        Expression valueOrPredicate = args.get(1);
                        Expression message = args.get(2);
                        return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)})." + assertion + "(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), list, message, valueOrPredicate);
                    }
                });
    }
}
