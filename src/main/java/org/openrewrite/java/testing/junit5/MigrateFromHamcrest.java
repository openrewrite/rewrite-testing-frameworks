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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
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
                    if (targetAssertion.equals("")) {
                        return mi;
                    }

                    JavaTemplate template = JavaTemplate.builder(getTemplateForMatcher(matcherInvocation.getSimpleName()))
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "junit-jupiter-api-5.9"))
                      .staticImports("org.junit.jupiter.api.Assertions." + targetAssertion)
                      .build();

                    Expression strippedMatcher = mi.getArguments().get(1);
                    if (strippedMatcher instanceof J.MethodInvocation) {
                        strippedMatcher = ((J.MethodInvocation) strippedMatcher).getArguments().get(0);
                    } else {
                        throw new IllegalArgumentException("Second parameter expected to be a matcher constructor call.");
                    }
                    template.apply(getCursor(), method.getCoordinates().replace(), mi.getArguments().get(0), strippedMatcher);
                    maybeAddImport("org.junit.jupiter.api.Assertions", targetAssertion);
                }
                else throw new IllegalArgumentException("Parameter mismatch for " + mi + ".");
            }
            return mi;
        }

        private String getTranslatedAssert(J.MethodInvocation methodInvocation) {
            //to be replaced with a static map
            switch (methodInvocation.getSimpleName()) {
                case "equalTo":
                    return "assertEquals";
                case "greaterThan":
                    return "assertTrue";
            }
            return "";
        }

        private String getTemplateForMatcher(String translatedAssertion) {
            //to be replaced with a static map
            switch (translatedAssertion) {
                case "equalTo":
                    return "assertEquals(#{any(java.lang.Object)}, #{any(java.lang.Object)})";
                case "greaterThan":
                    return "assertTrue(#{any(java.lang.Object)} > #{any(java.lang.Object)})";
            }
            return "";
        }
    }
}
