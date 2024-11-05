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
        MethodMatcher methodMatcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertInstanceOf(..)", true);
        return Preconditions.check(new UsesMethod<>(methodMatcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation md = super.visitMethodInvocation(method, ctx);
                if (!methodMatcher.matches(md)) {
                    return md;
                }

                Expression expectedType = md.getArguments().get(0);
                Expression actualValue = md.getArguments().get(1);

                if (md.getArguments().size() == 2) {
                    md = JavaTemplate.builder("assertThat(#{any()}).isInstanceOf(#{any()});")
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actualValue, expectedType);
                } else {
                    Expression messageOrSupplier = md.getArguments().get(2);
                    md = JavaTemplate.builder("assertThat(#{any()}).as(#{any()}).isInstanceOf(#{any()});")
                            .staticImports("org.assertj.core.api.Assertions.assertThat")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), actualValue, messageOrSupplier, expectedType);
                }

                maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);
                maybeRemoveImport("org.junit.jupiter.api.Assertions");
                return md;
            }
        });
    }
}
