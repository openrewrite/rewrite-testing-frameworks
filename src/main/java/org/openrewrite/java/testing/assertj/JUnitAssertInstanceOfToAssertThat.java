/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

public class JUnitAssertInstanceOfToAssertThat extends Recipe {

    private static final MethodMatcher ASSERT_INSTANCE_OF_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertInstanceOf(..)", true);

    @Override
    public String getDisplayName() {
        return "JUnit `assertInstanceOf` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertInstanceOf()` to AssertJ's `assertThat().isInstanceOf()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_INSTANCE_OF_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_INSTANCE_OF_MATCHER.matches(mi)) {
                    return mi;
                }

                maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);
                maybeRemoveImport("org.junit.jupiter.api.Assertions");

                Expression expected = mi.getArguments().get(0);
                Expression actual = mi.getArguments().get(1);
                if (mi.getArguments().size() == 2) {
                    return JavaTemplate.builder("assertThat(#{any()}).isInstanceOf(#{any()});")
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
                }

                Expression messageOrSupplier = mi.getArguments().get(2);
                return JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isInstanceOf(#{any()});")
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, messageOrSupplier, expected);
            }
        });
    }
}
