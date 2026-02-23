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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
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

import java.util.ArrayList;
import java.util.List;

public class HamcrestHasItemMatcherToAssertJ extends Recipe {
    @Getter
    final String displayName = "Migrate Hamcrest `hasItem(Matcher)` to AssertJ";

    @Getter
    final String description = "Migrate Hamcrest `hasItem(Matcher)` to AssertJ `hasAtLeastOneElementOfType` or `anySatisfy`.";

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher HAS_ITEM_WITH_MATCHER = new MethodMatcher("org.hamcrest.*Matchers hasItem(org.hamcrest.Matcher)");
    private static final MethodMatcher INSTANCE_OF_MATCHER = new MethodMatcher("org.hamcrest.*Matchers instanceOf(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(HAS_ITEM_WITH_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);
                if (!ASSERT_THAT_MATCHER.matches(mi)) {
                    return mi;
                }

                List<Expression> args = mi.getArguments();
                Expression reasonArgument = args.size() == 3 ? args.get(0) : null;
                Expression actualArgument = args.get(args.size() - 2);
                Expression matcherArgument = args.get(args.size() - 1);
                if (!HAS_ITEM_WITH_MATCHER.matches(matcherArgument)) {
                    return mi;
                }

                Expression innerMatcher = ((J.MethodInvocation) matcherArgument).getArguments().get(0);
                if (INSTANCE_OF_MATCHER.matches(innerMatcher)) {
                    return handleInstanceOf(mi, actualArgument, reasonArgument, innerMatcher, ctx);
                }

                return handleGeneralMatcher(mi, actualArgument, reasonArgument, innerMatcher, ctx);
            }

            private J.MethodInvocation handleInstanceOf(J.MethodInvocation mi, Expression actual, @Nullable Expression reason,
                                                        Expression innerMatcher, ExecutionContext ctx) {
                Expression typeArg = ((J.MethodInvocation) innerMatcher).getArguments().get(0);
                String reasonTemplate = reason != null ? ".as(#{any(String)})" : "";
                JavaTemplate template = JavaTemplate.builder(
                                "assertThat(#{any()})" + reasonTemplate + ".hasAtLeastOneElementOfType(#{any(java.lang.Class)})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build();

                removeImports();
                maybeRemoveImport("org.hamcrest.Matchers.instanceOf");
                maybeRemoveImport("org.hamcrest.CoreMatchers.instanceOf");

                List<Object> templateArgs = new ArrayList<>();
                templateArgs.add(actual);
                if (reason != null) {
                    templateArgs.add(reason);
                }
                templateArgs.add(typeArg);
                return template.apply(getCursor(), mi.getCoordinates().replace(), templateArgs.toArray());
            }

            private J.MethodInvocation handleGeneralMatcher(J.MethodInvocation mi, Expression actual, @Nullable Expression reason,
                                                            Expression innerMatcher, ExecutionContext ctx) {
                String reasonTemplate = reason != null ? ".as(#{any(String)})" : "";
                JavaTemplate template = JavaTemplate.builder(
                                "assertThat(#{any()})" + reasonTemplate + ".anySatisfy(arg -> assertThat(arg, #{any()}))")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                                "assertj-core-3", "hamcrest-3", "junit-jupiter-api-5"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build();

                removeImports();

                List<Object> templateArgs = new ArrayList<>();
                templateArgs.add(actual);
                if (reason != null) {
                    templateArgs.add(reason);
                }
                templateArgs.add(innerMatcher);
                return template.apply(getCursor(), mi.getCoordinates().replace(), templateArgs.toArray());
            }

            private void removeImports() {
                maybeRemoveImport("org.hamcrest.Matchers.hasItem");
                maybeRemoveImport("org.hamcrest.CoreMatchers.hasItem");
                maybeRemoveImport("org.hamcrest.MatcherAssert");
                maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            }
        });
    }
}
