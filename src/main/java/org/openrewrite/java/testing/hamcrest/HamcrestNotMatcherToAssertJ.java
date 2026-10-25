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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
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

@NoArgsConstructor
@AllArgsConstructor
public class HamcrestNotMatcherToAssertJ extends Recipe {
    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher NOT_MATCHER = new MethodMatcher("org.hamcrest.*Matchers not(org.hamcrest.Matcher)");

    @Option(displayName = "Hamcrest Matcher",
            description = "The Hamcrest `not(Matcher)` to migrate to AssertJ.",
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

    @Getter
    final String displayName = "Migrate Hamcrest `not(Matcher)` to AssertJ";

    @Getter
    final String description = "Migrate from Hamcrest `not(Matcher)` to AssertJ assertions.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>("org.hamcrest.*Matchers " + notMatcher + "(..)"), new MigrateToAssertJVisitor());
    }

    private class MigrateToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher matchersMatcher = new MethodMatcher("org.hamcrest.*Matchers " + notMatcher + "(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (!ASSERT_THAT_MATCHER.matches(mi)) {
                return mi;
            }
            List<Expression> mia = mi.getArguments();
            Expression notMethodInvocation = mia.get(mia.size() - 1);
            if (!NOT_MATCHER.matches(notMethodInvocation)) {
                return mi;
            }
            Expression matcherArgument = ((J.MethodInvocation) notMethodInvocation).getArguments().get(0);
            if (!matchersMatcher.matches(matcherArgument) || HamcrestMatcherToAssertJ.takesMatcher((J.MethodInvocation) matcherArgument)) {
                return mi;
            }
            Expression reasonArgument = mia.size() == 3 ? mia.get(0) : null;
            Expression actualArgument = mia.get(mia.size() - 2);
            List<Expression> originalArguments = ListUtils.filter(((J.MethodInvocation) matcherArgument).getArguments(), a -> !(a instanceof J.Empty));
            String argumentsTemplate = originalArguments.stream()
                    .map(a -> typeToIndicator(a.getType()))
                    .collect(joining(", "));
            JavaTemplate template = JavaTemplate.builder(String.format("assertThat(%s)%s.%s(%s)",
                            typeToIndicator(actualArgument.getType()),
                            reasonArgument != null ? ".as(#{any(String)})" : "",
                            assertion,
                            argumentsTemplate))
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
            if (reasonArgument != null) {
                templateArguments.add(reasonArgument);
            }
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
