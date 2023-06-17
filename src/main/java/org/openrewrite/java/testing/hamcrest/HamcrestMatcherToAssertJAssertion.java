/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.hamcrest;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
@AllArgsConstructor
public class HamcrestMatcherToAssertJAssertion extends Recipe {

    @Option(displayName = "Hamcrest Matcher",
            description = "The Hamcrest matcher to migrate to JUnit5.",
            example = "equalTo",
            required = false)
    @Nullable
    String matcher;

    @Option(displayName = "AssertJ Assertion",
            description = "The AssertJ method to migrate to.",
            example = "isEqualTo",
            required = false)
    @Nullable
    String assertion;

    @Override
    public String getDisplayName() {
        return "Migrate from Hamcrest to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest Matchers to AssertJ assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrateToAssertJVisitor();
    }

    private class MigrateToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher assertThatMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
        private final MethodMatcher matchersMatcher = new MethodMatcher("org.hamcrest.Matchers " + matcher + "(..)");
        private final MethodMatcher subMatcher = new MethodMatcher("org.hamcrest.Matchers *(org.hamcrest.Matcher)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (assertThatMatcher.matches(mi) && mi.getArguments().size() == 2) {
                Expression firstArgument = mi.getArguments().get(0);
                Expression secondArgument = mi.getArguments().get(1);
                if (!matchersMatcher.matches(secondArgument) || subMatcher.matches(secondArgument)) {
                    return mi;
                }
                String actual = typeToIndicator(firstArgument.getType());
                List<Expression> originalArguments = ((J.MethodInvocation) secondArgument).getArguments().stream()
                        .filter(a -> !(a instanceof J.Empty))
                        .collect(Collectors.toList());
                String argumentsTemplate = originalArguments.stream()
                        .map(a -> typeToIndicator(a.getType()))
                        .collect(Collectors.joining(", "));
                JavaTemplate template = JavaTemplate.builder(String.format("assertThat(%s).%s(%s)",
                                actual, assertion, argumentsTemplate))
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9", "assertj-core-3.24"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build();
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
                maybeRemoveImport("org.hamcrest.Matchers." + matcher);
                maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");

                List<Expression> templateArguments = new ArrayList<>();
                templateArguments.add(firstArgument);
                templateArguments.addAll(originalArguments);
                return template.apply(getCursor(), method.getCoordinates().replace(), templateArguments.toArray());
            }
            // TODO Also handle assertThat(String, boolean) and assertThat(String, Object, Matcher)
            return mi;
        }

        private String typeToIndicator(JavaType type) {
            String str = type instanceof JavaType.Primitive || type.toString().startsWith("java.lang.") ?
                    type.toString() : "java.lang.Object";
            return String.format("#{any(%s)}", str);
        }
    }
}
