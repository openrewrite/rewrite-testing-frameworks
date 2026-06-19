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
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class TestNgAssertEqualsNoOrderToAssertThat extends Recipe {

    @Getter
    final String displayName = "TestNG `assertEqualsNoOrder` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style `assertEqualsNoOrder()` to AssertJ's " +
                               "`assertThat().containsExactlyInAnyOrder()` (arrays) or " +
                               "`assertThat().containsExactlyInAnyOrderElementsOf()` (collections).";

    private static final MethodMatcher TESTNG_ASSERT_METHOD = new MethodMatcher("org.testng.Assert assertEqualsNoOrder(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(TESTNG_ASSERT_METHOD), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                method = super.visitMethodInvocation(method, ctx);
                if (!TESTNG_ASSERT_METHOD.matches(method)) {
                    return method;
                }

                maybeRemoveImport("org.testng.Assert");
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);

                List<Expression> args = method.getArguments();
                Expression actual = args.get(0);
                Expression expected = args.get(1);
                boolean hasMessage = args.size() == 3;
                String as = hasMessage ? ".as(#{any(String)})" : "";

                String code;
                if (TypeUtils.asArray(actual.getType()) != null) {
                    code = "assertThat(#{anyArray()})" + as + ".containsExactlyInAnyOrder(#{anyArray()});";
                } else if (TypeUtils.isAssignableTo("java.util.Iterator", actual.getType())) {
                    // Iterators are single-pass and `assertThat(Iterator)` has no element assertions; route through
                    // `toIterable()` and wrap the expected iterator in an `Iterable` lambda (`() -> expected`).
                    code = "assertThat(#{any()}).toIterable()" + as + ".containsExactlyInAnyOrderElementsOf(() -> #{any()});";
                } else {
                    code = "assertThat(#{any()})" + as + ".containsExactlyInAnyOrderElementsOf(#{any()});";
                }

                JavaTemplate template = JavaTemplate.builder(code)
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build();
                return hasMessage ?
                        template.apply(getCursor(), method.getCoordinates().replace(), actual, args.get(2), expected) :
                        template.apply(getCursor(), method.getCoordinates().replace(), actual, expected);
            }
        });
    }
}
