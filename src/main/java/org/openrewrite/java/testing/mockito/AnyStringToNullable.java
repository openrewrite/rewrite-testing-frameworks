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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.PartProvider;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.time.Duration;

/**
 * Replace Mockito 1.x `anyString()` with `nullable(String.class)`
 */
public class AnyStringToNullable extends Recipe {
    private static final MethodMatcher ANY_STRING = new MethodMatcher("org.mockito.Mockito anyString()");
    private static final String MOCKITO_CLASS_PATH = "mockito-core-3.12.4";
    private static J.MethodInvocation nullableStringMethodTemplate = null;

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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ANY_STRING), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                if (ANY_STRING.matches(mi)) {
                    maybeAddImport("org.mockito.ArgumentMatchers", "nullable", false);
                    maybeRemoveImport("org.mockito.Mockito.anyString");
                    return getNullableMethodTemplate().withPrefix(mi.getPrefix());
                }
                return mi;
            }
        });
    }

    private static J.MethodInvocation getNullableMethodTemplate() {
        if (nullableStringMethodTemplate == null) {
            nullableStringMethodTemplate = PartProvider.buildPart("import static org.mockito.ArgumentMatchers" +
                    ".nullable;\n" +
                    "public class A {\n" +
                    "    void method() {\n" +
                    "        Object x = nullable(String.class);\n" +
                    "    }\n" +
                    "}", J.MethodInvocation.class, MOCKITO_CLASS_PATH);
        }
        return nullableStringMethodTemplate;
    }
}
