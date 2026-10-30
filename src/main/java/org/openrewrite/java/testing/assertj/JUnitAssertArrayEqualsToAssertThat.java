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
import org.openrewrite.java.tree.JavaType;

import java.util.List;

import static org.openrewrite.java.testing.assertj.JUnitDelta.floatingPointDeltaType;
import static org.openrewrite.java.testing.assertj.JUnitDelta.widenedTo;

public class JUnitAssertArrayEqualsToAssertThat extends Recipe {

    private static final String JUNIT = "org.junit.jupiter.api.Assertions";
    private static final String ASSERTJ = "org.assertj.core.api.Assertions";
    private static final MethodMatcher ASSERT_ARRAY_EQUALS_MATCHER = new MethodMatcher(JUNIT + " assertArrayEquals(..)", true);

    @Getter
    final String displayName = "JUnit `assertArrayEquals` to AssertJ";

    @Getter
    final String description = "Convert JUnit-style `assertArrayEquals()` to AssertJ's `assertThat().containsExactly()` equivalents.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_ARRAY_EQUALS_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation md = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_ARRAY_EQUALS_MATCHER.matches(md)) {
                    return md;
                }

                maybeRemoveImport(JUNIT);
                maybeAddImport(ASSERTJ, "assertThat");

                List<Expression> args = md.getArguments();
                Expression expected = args.get(0);
                Expression actual = args.get(1);
                if (args.size() == 2) {
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, expected);
                }
                JavaType.Primitive deltaType = floatingPointDeltaType(md);
                if (deltaType == null) {
                    Expression message = args.get(2);
                    return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any()}).containsExactly(#{anyArray()});")
                            .staticImports(ASSERTJ + ".assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, message, expected);
                }

                maybeAddImport(ASSERTJ, "within");
                Expression delta = args.get(2);
                if (args.size() == 3) {
                    return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()}, within(" + widenedTo(deltaType, delta) + "));")
                            .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(getCursor(), md.getCoordinates().replace(), actual, expected, delta);
                }

                Expression message = args.get(3);
                return JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any()}).containsExactly(#{anyArray()}, within(" + widenedTo(deltaType, delta) + "));")
                        .staticImports(ASSERTJ + ".assertThat", ASSERTJ + ".within")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), md.getCoordinates().replace(), actual, message, expected, delta);
            }
        });
    }
}
