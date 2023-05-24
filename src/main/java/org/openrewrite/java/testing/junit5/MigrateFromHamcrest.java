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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

public class MigrateFromHamcrest extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate from Hamcrest Matchers to JUnit5";
    }

    @Override
    public String getDescription() {
        return "This recipe will migrate all Hamcrest Matchers to JUnit5 assertions.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrationFromHamcrestVisitor();
    }

    private static class MigrationFromHamcrestVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            MethodMatcher matcherAssertTrue = new MethodMatcher("org.hamcrest.MatchAssert assertThat(String, boolean)");
            MethodMatcher matcherAssertMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(*, org.hamcrest.Matcher)");
            MethodMatcher matcherAssertMatcherWithReason = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(String,*,org.hamcrest.Matcher)");

            if (matcherAssertTrue.matches(mi)) {
                //TODO simple
            } else if (matcherAssertMatcher.matches(mi)) {
                Expression hamcrestMatcher = mi.getArguments().get(1);
                if (hamcrestMatcher instanceof J.MethodInvocation) {
                    J.MethodInvocation matcherInvocation = (J.MethodInvocation)hamcrestMatcher;
                    maybeRemoveImport("org.hamcrest.Matchers." + matcherInvocation.getSimpleName());
                    maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                    String targetAssertion = getTranslatedAssert(matcherInvocation);
                    JavaTemplate template = JavaTemplate.builder(this::getCursor, getTemplateForTranslatedAssertion(targetAssertion))
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "junit-jupiter-api-5.9"))
                      .imports("org.junit.jupiter.api.Assertions." + targetAssertion)
                      .build();
                    mi = withTemplate(mi, template, mi.getArguments().get(0), stripMatcherInvocation(mi.getArguments().get(1)));
                    maybeAddImport("org.junit.jupiter.api.Assertions", targetAssertion);
                }
                else throw new IllegalArgumentException("Parameter mismatch for " + mi + ".");
            }
            return mi;
        }

        private J.MethodInvocation withTemplate(J.MethodInvocation method, JavaTemplate template, Expression firstArg, List<Expression> matcherArgs) {
            switch (matcherArgs.size()) {
                case 0:
                    return method.withTemplate(template, method.getCoordinates().replace(), firstArg);
                case 1:
                    return method.withTemplate(template, method.getCoordinates().replace(), firstArg, matcherArgs.get(0));
                case 2 :
                    return method.withTemplate(template, method.getCoordinates().replace(), firstArg, matcherArgs.get(0), matcherArgs.get(1));
                case 3 :
                    return method.withTemplate(template, method.getCoordinates().replace(), firstArg, matcherArgs.get(0), matcherArgs.get(1), matcherArgs.get(2));
                default:
                    throw new IllegalArgumentException("List of matcher arguments is too long for " + method + ".");
            }
        }

        private List<Expression> stripMatcherInvocation(Expression e) {
            if (e instanceof J.MethodInvocation) {
                MethodMatcher matchesMatcher = new MethodMatcher("org.hamcrest.Matchers *(..)");
                if (matchesMatcher.matches(e)) {
                    return ((J.MethodInvocation) e).getArguments();
                }
            }
            throw new IllegalArgumentException("Trying to strip an expression which is not a matcher invocation:\n" + e);
        }

        private String getTranslatedAssert(J.MethodInvocation methodInvocation) {
            //to be replaced with a static map
            switch (methodInvocation.getSimpleName()) {
                case "equalTo":
                    return "assertEquals";
            }
            throw new IllegalArgumentException("Translation of matcher " + methodInvocation.getSimpleName() + " not yet supported.");
        }

        private String getTemplateForTranslatedAssertion(String translatedAssertion) {
            //to be replaced with a static map
            switch (translatedAssertion) {
                case "assertEquals":
                    return "assertEquals(#{any()}, #{any()})";
            }
            throw new IllegalArgumentException("There is no template defined for assertion " + translatedAssertion);
        }
    }
}
