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

public class HamcrestHasPropertyToAssertJ extends Recipe {
    @Getter
    final String displayName = "Migrate Hamcrest `hasProperty` to AssertJ";

    @Getter
    final String description = "Migrate Hamcrest `hasProperty` to AssertJ `hasFieldOrProperty` and `hasFieldOrPropertyWithValue`.";

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher HAS_PROPERTY_MATCHER = new MethodMatcher("org.hamcrest.*Matchers hasProperty(..)");
    private static final MethodMatcher EQUAL_TO_MATCHER = new MethodMatcher("org.hamcrest.*Matchers equalTo(..)");
    private static final MethodMatcher IS_MATCHER = new MethodMatcher("org.hamcrest.*Matchers is(..)");
    private static final MethodMatcher IS_NESTED_MATCHER = new MethodMatcher("org.hamcrest.*Matchers is(org.hamcrest.Matcher)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(HAS_PROPERTY_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
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

                if (!HAS_PROPERTY_MATCHER.matches(matcherArgument)) {
                    return mi;
                }

                J.MethodInvocation hasPropertyInvocation = (J.MethodInvocation) matcherArgument;
                List<Expression> hasPropertyArgs = hasPropertyInvocation.getArguments();

                if (hasPropertyArgs.size() == 1) {
                    return handleSingleArg(mi, actualArgument, reasonArgument, hasPropertyArgs.get(0), ctx);
                } else if (hasPropertyArgs.size() == 2) {
                    return handleTwoArgs(mi, actualArgument, reasonArgument, hasPropertyArgs.get(0), hasPropertyArgs.get(1), ctx);
                }
                return mi;
            }

            private J.MethodInvocation handleSingleArg(J.MethodInvocation mi, Expression actual, Expression reason,
                                                       Expression propertyName, ExecutionContext ctx) {
                String reasonTemplate = reason != null ? ".as(#{any(String)})" : "";
                JavaTemplate template = JavaTemplate.builder(
                                "assertThat(#{any()})" + reasonTemplate + ".hasFieldOrProperty(#{any(String)})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build();

                removeImports();
                List<Object> templateArgs = new ArrayList<>();
                templateArgs.add(actual);
                if (reason != null) {
                    templateArgs.add(reason);
                }
                templateArgs.add(propertyName);
                return template.apply(getCursor(), mi.getCoordinates().replace(), templateArgs.toArray());
            }

            private J.MethodInvocation handleTwoArgs(J.MethodInvocation mi, Expression actual, Expression reason,
                                                     Expression propertyName, Expression valueMatcher, ExecutionContext ctx) {
                // Unwrap equalTo(val) or is(val) where is is not is(Matcher)
                Expression value;
                if (EQUAL_TO_MATCHER.matches(valueMatcher)) {
                    value = ((J.MethodInvocation) valueMatcher).getArguments().get(0);
                } else if (IS_MATCHER.matches(valueMatcher) && !IS_NESTED_MATCHER.matches(valueMatcher)) {
                    value = ((J.MethodInvocation) valueMatcher).getArguments().get(0);
                } else {
                    // Unknown matcher, don't convert
                    return mi;
                }

                String reasonTemplate = reason != null ? ".as(#{any(String)})" : "";
                JavaTemplate template = JavaTemplate.builder(
                                "assertThat(#{any()})" + reasonTemplate + ".hasFieldOrPropertyWithValue(#{any(String)}, #{any()})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build();

                removeImports();
                maybeRemoveImport("org.hamcrest.Matchers.equalTo");
                maybeRemoveImport("org.hamcrest.CoreMatchers.equalTo");
                maybeRemoveImport("org.hamcrest.Matchers.is");
                maybeRemoveImport("org.hamcrest.CoreMatchers.is");

                List<Object> templateArgs = new ArrayList<>();
                templateArgs.add(actual);
                if (reason != null) {
                    templateArgs.add(reason);
                }
                templateArgs.add(propertyName);
                templateArgs.add(value);
                return template.apply(getCursor(), mi.getCoordinates().replace(), templateArgs.toArray());
            }

            private void removeImports() {
                maybeRemoveImport("org.hamcrest.Matchers.hasProperty");
                maybeRemoveImport("org.hamcrest.CoreMatchers.hasProperty");
                maybeRemoveImport("org.hamcrest.MatcherAssert");
                maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            }
        });
    }
}
