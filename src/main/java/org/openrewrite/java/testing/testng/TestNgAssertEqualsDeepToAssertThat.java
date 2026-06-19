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

public class TestNgAssertEqualsDeepToAssertThat extends Recipe {

    @Getter
    final String displayName = "TestNG `assertEqualsDeep`/`assertNotEqualsDeep` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style `assertEqualsDeep()` and `assertNotEqualsDeep()` to AssertJ's " +
                               "`assertThat().usingRecursiveComparison().isEqualTo()` / `.isNotEqualTo()`, which performs " +
                               "a deep, recursive comparison of the `Map`/`Set` contents.";

    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher EQUALS_DEEP_MATCHER = new MethodMatcher("org.testng.Assert assertEqualsDeep(..)");
    private static final MethodMatcher NOT_EQUALS_DEEP_MATCHER = new MethodMatcher("org.testng.Assert assertNotEqualsDeep(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(EQUALS_DEEP_MATCHER), new UsesMethod<>(NOT_EQUALS_DEEP_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        method = super.visitMethodInvocation(method, ctx);
                        boolean isEquals = EQUALS_DEEP_MATCHER.matches(method);
                        boolean isNotEquals = NOT_EQUALS_DEEP_MATCHER.matches(method);
                        if (!isEquals && !isNotEquals) {
                            return method;
                        }

                        maybeRemoveImport("org.testng.Assert");
                        maybeAddImport(ASSERTJ, "assertThat", false);

                        List<Expression> args = method.getArguments();
                        Expression actual = args.get(0);
                        Expression expected = args.get(1);
                        String assertion = isEquals ? "isEqualTo" : "isNotEqualTo";

                        if (args.size() == 2) {
                            return JavaTemplate.builder("assertThat(#{any()}).usingRecursiveComparison()." + assertion + "(#{any()});")
                                    .staticImports(ASSERTJ + ".assertThat")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                    .build()
                                    .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                        }

                        Expression message = args.get(2);
                        return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).usingRecursiveComparison()." + assertion + "(#{any()});")
                                .staticImports(ASSERTJ + ".assertThat")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected);
                    }
                });
    }
}
