/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;

/**
 * S
 */
public class AnyStringToNullable extends Recipe {

    public static final String ARGUMENT_MATCHERS_ANY_STRING = "org.mockito.Mockito anyString()";

    @Override
    public String getDisplayName() {
        return "Replace Mockito 1.x `anyString()` with `nullable(String.class)`";
    }

    @Override
    public String getDescription() {
        return "Since Mockito 2.10 `anyString()` no longer matches null values. Use `nullable(Class)` instead.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(ARGUMENT_MATCHERS_ANY_STRING);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AnyStringToNullableVisitor();
    }

    private static class AnyStringToNullableVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final JavaTemplate template = JavaTemplate.builder(this::getCursor, "nullable(String.class)")
                .staticImports("org.mockito.ArgumentMatchers.nullable")
                .build();

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            if (new MethodMatcher(ARGUMENT_MATCHERS_ANY_STRING).matches(mi)) {
                maybeAddImport("org.mockito.ArgumentMatchers", "nullable", false);
                maybeRemoveImport("org.mockito.Mockito.anyString");
                return mi.withTemplate(template, mi.getCoordinates().replace());
            }
            return mi;
        }
    }
}
