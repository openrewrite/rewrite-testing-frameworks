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
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
@AllArgsConstructor
public class HamcrestMatcherToAssertJ extends Recipe {

    @Option(displayName = "Hamcrest Matcher",
            description = "The Hamcrest `Matcher` to migrate to JUnit5.",
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
        return "Migrate from Hamcrest `Matcher` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest `Matcher` to AssertJ assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>("org.hamcrest.Matchers " + matcher + "(..)"), new MigrateToAssertJVisitor());
    }

    private class MigrateToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher assertThatMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
        private final MethodMatcher matchersMatcher = new MethodMatcher("org.hamcrest.Matchers " + matcher + "(..)");
        private final MethodMatcher subMatcher = new MethodMatcher("org.hamcrest.Matchers *(org.hamcrest.Matcher)");


        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (assertThatMatcher.matches(mi)) {
                return replace(mi, ctx);
            }
            return mi;
        }

        private J.MethodInvocation replace(J.MethodInvocation mi, ExecutionContext ctx) {
            List<Expression> mia = mi.getArguments();
            Expression reasonArgument = mia.size() == 3 ? mia.get(0) : null;
            Expression actualArgument = mia.get(mia.size() - 2);
            Expression matcherArgument = mia.get(mia.size() - 1);
            if (!matchersMatcher.matches(matcherArgument) || subMatcher.matches(matcherArgument)) {
                return mi;
            }
            String actual = typeToIndicator(actualArgument.getType());
            List<Expression> originalArguments = ((J.MethodInvocation) matcherArgument).getArguments().stream()
                    .filter(a -> !(a instanceof J.Empty))
                    .collect(Collectors.toList());
            String special = specialArgumentsTemplateFor((J.MethodInvocation) matcherArgument);
            String argumentsTemplate = special != null ? special : originalArguments.stream()
                    .map(a -> typeToIndicator(a.getType()))
                    .collect(Collectors.joining(", "));
            JavaTemplate template = JavaTemplate.builder(String.format(
                            "assertThat(%s)" +
                            (reasonArgument != null ? ".as(#{any(String)})" : "") +
                            ".%s(%s)",
                            actual, assertion, argumentsTemplate))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                    .staticImports(
                            "org.assertj.core.api.Assertions.assertThat",
                            "org.assertj.core.api.Assertions.within")
                    .build();
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            maybeAddImport("org.assertj.core.api.Assertions", "within");
            maybeRemoveImport("org.hamcrest.Matchers." + matcher);
            maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");

            List<Expression> templateArguments = new ArrayList<>();
            templateArguments.add(actualArgument);
            if (reasonArgument != null) {
                templateArguments.add(reasonArgument);
            }
            templateArguments.addAll(originalArguments);
            return template.apply(getCursor(), mi.getCoordinates().replace(), templateArguments.toArray());
        }

        private String typeToIndicator(JavaType type) {
            if (type instanceof JavaType.Array) {
                type = ((JavaType.Array) type).getElemType();
                String str = type instanceof JavaType.Primitive || type.toString().startsWith("java.") ?
                        type.toString().replaceAll("<.*>", "") : "java.lang.Object";
                return String.format("#{anyArray(%s)}", str);
            } else {
                String str = type instanceof JavaType.Primitive || type.toString().startsWith("java.") ?
                        type.toString().replaceAll("<.*>", "") : "java.lang.Object";
                return String.format("#{any(%s)}", str);
            }
        }

        private final MethodMatcher CLOSE_TO_MATCHER = new MethodMatcher("org.hamcrest.Matchers closeTo(..)");

        private String specialArgumentsTemplateFor(J.MethodInvocation mi) {
            if (CLOSE_TO_MATCHER.matches(mi)) {
                String indicator = typeToIndicator(mi.getArguments().get(0).getType());
                return String.format("%s, within(%s)", indicator, indicator);
            }
            return null;
        }
    }
}
