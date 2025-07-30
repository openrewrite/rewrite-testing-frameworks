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
package org.openrewrite.java.testing.hamcrest;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@NoArgsConstructor
@AllArgsConstructor
public class HamcrestNotMatcherToAssertJ extends Recipe {

    @Option(displayName = "Hamcrest Matcher",
            description = "The Hamcrest `not(Matcher)` to migrate to JUnit5.",
            example = "equalTo",
            required = false)
    @Nullable
    String notMatcher;

    @Option(displayName = "AssertJ Assertion",
            description = "The AssertJ method to migrate to.",
            example = "isNotEqualTo",
            required = false)
    @Nullable
    String assertion;

    @Override
    public String getDisplayName() {
        return "Migrate Hamcrest `not(Matcher)` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest `not(Matcher)` to AssertJ assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>("org.hamcrest.*Matchers " + notMatcher + "(..)"), new MigrateToAssertJVisitor());
    }

    private class MigrateToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
        private final MethodMatcher NOT_MATCHER = new MethodMatcher("org.hamcrest.*Matchers not(org.hamcrest.Matcher)");
        private final MethodMatcher MATCHERS_MATCHER = new MethodMatcher("org.hamcrest.*Matchers " + notMatcher + "(..)");
        private final MethodMatcher SUB_MATCHER = new MethodMatcher("org.hamcrest.*Matchers *(org.hamcrest.Matcher)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (ASSERT_THAT_MATCHER.matches(mi)) {
                Expression notMethodInvocation = mi.getArguments().get(mi.getArguments().size() - 1);
                if (!NOT_MATCHER.matches(notMethodInvocation)) {
                    return mi;
                }
                Expression matcherArgument = ((J.MethodInvocation) notMethodInvocation).getArguments().get(0);
                if (mi.getArguments().size() == 2) {
                    return handleTwoArgumentCase(mi, matcherArgument, ctx);
                }
                if (mi.getArguments().size() == 3) {
                    return handleThreeArgumentCase(mi, matcherArgument, ctx);
                }
            }
            return mi;
        }

        private J.MethodInvocation handleTwoArgumentCase(J.MethodInvocation mi, Expression matcherArgument, ExecutionContext ctx) {
            Expression actualArgument = mi.getArguments().get(0);
            if (!MATCHERS_MATCHER.matches(matcherArgument) || SUB_MATCHER.matches(matcherArgument)) {
                return mi;
            }
            String actual = typeToIndicator(actualArgument.getType());
            List<Expression> originalArguments = ((J.MethodInvocation) matcherArgument).getArguments().stream()
                    .filter(a -> !(a instanceof J.Empty))
                    .collect(toList());
            String argumentsTemplate = originalArguments.stream()
                    .map(a -> typeToIndicator(a.getType()))
                    .collect(joining(", "));
            JavaTemplate template = JavaTemplate.builder(String.format("assertThat(%s).%s(%s)",
                            actual, assertion, argumentsTemplate))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                    .staticImports("org.assertj.core.api.Assertions.assertThat")
                    .build();
            maybeRemoveImport("org.hamcrest.Matchers.not");
            maybeRemoveImport("org.hamcrest.Matchers." + notMatcher);
            maybeRemoveImport("org.hamcrest.CoreMatchers.not");
            maybeRemoveImport("org.hamcrest.CoreMatchers." + notMatcher);
            maybeRemoveImport("org.hamcrest.MatcherAssert");
            maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");

            List<Expression> templateArguments = new ArrayList<>();
            templateArguments.add(actualArgument);
            templateArguments.addAll(originalArguments);
            return template.apply(getCursor(), mi.getCoordinates().replace(), templateArguments.toArray());
        }

        private J.MethodInvocation handleThreeArgumentCase(J.MethodInvocation mi, Expression matcherArgument, ExecutionContext ctx) {
            Expression reasonArgument = mi.getArguments().get(0);
            Expression actualArgument = mi.getArguments().get(1);
            if (!MATCHERS_MATCHER.matches(matcherArgument) || SUB_MATCHER.matches(matcherArgument)) {
                return mi;
            }
            String actual = typeToIndicator(actualArgument.getType());
            List<Expression> originalArguments = ((J.MethodInvocation) matcherArgument).getArguments().stream()
                    .filter(a -> !(a instanceof J.Empty))
                    .collect(toList());
            String argumentsTemplate = originalArguments.stream()
                    .map(a -> typeToIndicator(a.getType()))
                    .collect(joining(", "));
            JavaTemplate template = JavaTemplate.builder(String.format("assertThat(%s).as(#{any(String)}).%s(%s)",
                            actual, assertion, argumentsTemplate))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                    .staticImports("org.assertj.core.api.Assertions.assertThat")
                    .build();
            maybeRemoveImport("org.hamcrest.Matchers.not");
            maybeRemoveImport("org.hamcrest.Matchers." + notMatcher);
            maybeRemoveImport("org.hamcrest.MatcherAssert");
            maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");

            List<Expression> templateArguments = new ArrayList<>();
            templateArguments.add(actualArgument);
            templateArguments.add(reasonArgument);
            templateArguments.addAll(originalArguments);
            return template.apply(getCursor(), mi.getCoordinates().replace(), templateArguments.toArray());
        }

        private String typeToIndicator(JavaType type) {
            String str = type instanceof JavaType.Primitive || type.toString().startsWith("java.") ?
                    type.toString().replaceAll("<.*>", "") : "java.lang.Object";
            return String.format("#{any(%s)}", str);
        }
    }
}
