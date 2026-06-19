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

public class TestNgAssertNotSameToAssertThat extends Recipe {

    @Getter
    final String displayName = "TestNG `assertNotSame` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style `assertNotSame()` to AssertJ's `assertThat().isNotSameAs()`.";

    private static final MethodMatcher TESTNG_ASSERT_METHOD = new MethodMatcher("org.testng.Assert assertNotSame(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(TESTNG_ASSERT_METHOD), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (!TESTNG_ASSERT_METHOD.matches(method)) {
                    return method;
                }

                maybeRemoveImport("org.testng.Assert");
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);

                List<Expression> args = method.getArguments();
                Expression actual = args.get(0);
                Expression expected = args.get(1);
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{any()}).isNotSameAs(#{any()});")
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                }

                Expression message = args.get(2);
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any(String)}).isNotSameAs(#{any()});")
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected);
            }
        });
    }
}
