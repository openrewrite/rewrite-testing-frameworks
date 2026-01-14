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
package org.openrewrite.java.testing.mockito;

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
import org.openrewrite.java.tree.J;

import java.time.Duration;

/**
 * Replace Mockito 1.x `anyString()` with `nullable(String.class)`
 */
public class AnyStringToNullable extends Recipe {
    private static final MethodMatcher ANY_STRING = new MethodMatcher("org.mockito.Mockito anyString()");

    @Getter
    final String displayName = "Replace Mockito 1.x `anyString()` with `nullable(String.class)`";

    @Getter
    final String description = "Since Mockito 2.10 `anyString()` no longer matches null values. Use `nullable(Class)` instead.";

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ANY_STRING), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (ANY_STRING.matches(mi)) {
                    maybeRemoveImport("org.mockito.Mockito.anyString");
                    maybeAddImport("org.mockito.ArgumentMatchers", "nullable", false);
                    return JavaTemplate.builder("nullable(String.class)")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                            .staticImports("org.mockito.ArgumentMatchers.nullable")
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace());
                }
                return mi;
            }
        });
    }
}
