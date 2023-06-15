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

import java.util.ArrayList;
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrationFromHamcrestVisitor();
    }

    private static class MigrationFromHamcrestVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            System.out.println("RECIPE RUN");
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            MethodMatcher matcherAssertTrue = new MethodMatcher("org.hamcrest.MatchAssert assertThat(String, boolean)");
            MethodMatcher matcherAssertMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
            MethodMatcher matcherAssertMatcherWithReason = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(String,*,org.hamcrest.Matcher)");

            if (matcherAssertTrue.matches(mi)) {
                //TODO simple
            } else if (matcherAssertMatcher.matches(mi)) {
                Expression hamcrestMatcher = mi.getArguments().get(1);
                if (hamcrestMatcher instanceof J.MethodInvocation) {
                    System.out.println("matched");
                    J.MethodInvocation matcherInvocation = (J.MethodInvocation)hamcrestMatcher;
                    maybeRemoveImport("org.hamcrest.Matchers." + matcherInvocation.getSimpleName());
                    maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                    String targetAssertion = getTranslatedAssert(matcherInvocation);
                    if (targetAssertion.equals("")) {
                        return mi;
                    }

                    JavaTemplate template = JavaTemplate.builder(getTemplateForMatcher(matcherInvocation,null))
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "junit-jupiter-api-5.9"))
                      .staticImports("org.junit.jupiter.api.Assertions." + targetAssertion)
                      .build();

                    maybeAddImport("org.junit.jupiter.api.Assertions", targetAssertion);
                    return template.apply(getCursor(), method.getCoordinates().replace(),
                        getArgumentsForTemplate(matcherInvocation, null, mi.getArguments().get(0)));
                }
                else throw new IllegalArgumentException("Parameter mismatch for " + mi + ".");
            }
            System.out.println("FINISH RUN");
            return mi;
        }

        private String getTranslatedAssert(J.MethodInvocation methodInvocation) {
            //to be replaced with a static map
            String str = ";";
            switch (methodInvocation.getSimpleName()) {
                case "equalTo":
                case "emptyArray":
                    return "assertEquals";
                case "greaterThan":
                case "closeTo":
                case "containsString":
                case "empty":
                case "emptyCollectionOf":
                case "emptyIterable":
                case "emptyIterableOf":
                case "endsWith":
                    return "assertTrue";
            }
            return "";
        }

        private String getTemplateForMatcher(J.MethodInvocation matcher, Expression errorMsg) {
            StringBuilder sb = new StringBuilder();
            sb.append(getTranslatedAssert(matcher));

            //to be replaced with a static map
            switch (matcher.getSimpleName()) {
                case "equalTo":
                    sb.append("(#{any(java.lang.Object)}, #{any(java.lang.Object)}");
                    break;
                case "greaterThan":
                    sb.append("(#{any(java.lang.Object)} > #{any(java.lang.Object)}");
                    break;
                case "closeTo":
                    sb.append("(Math.abs(#{any(java.lang.Object)} - #{any(java.lang.Object)}) < #{any(java.lang.Object)}");
                    break;
                case "containsString":
                    sb.append("(#{any(java.lang.Object)}.contains(#{any(java.lang.Object)}");
                    break;
                case "empty":
                    sb.append("(#{any(java.lang.Object)}.isEmpty()");
                    break;
                case "emptyArray":
                    sb.append("(0, #{any(java.lang.Object)}.length");
                    break;
                case "emptyCollectionOf":
                    sb.append("(#{any(java.lang.Object)}.isEmpty() && ");
                    sb.append("#{any(java.lang.Object)}.isAssignableFrom(#{any(java.lang.Object)}.getClass())");
                    break;
                case "emptyIterable":
                    sb.append("(#{any(java.lang.Object)}.iterator().hasNext()");
                case "emptyIterableOf":
                    sb.append("(#{any(java.lang.Object)}.iterator().hasNext() && ");
                    sb.append("#{any(java.lang.Object)}.isAssignableFrom(#{any(java.lang.Object)}.getClass())");
                    break;
                case "endsWith":
                    sb.append("(#{any(java.lang.Object)}.substring(Math.abs(#{any(java.lang.Object)}.length() - #{any(java.lang.Object)}.length()))");
                    sb.append(".equals(#{any(java.lang.Object)})");
                    break;
            }

            if (errorMsg != null) {
                sb.append(", #{any(java.lang.Object)})");
            } else {
                sb.append(")");
            }
            return getTranslatedAssert(matcher).equals("") ? "" : sb.toString();
        }

        private Object[] getArgumentsForTemplate(J.MethodInvocation matcher, Expression errorMsg, Expression examinedObj) {
            List<Expression> result = new ArrayList<>();

            switch (matcher.getSimpleName()) {
                case "equalTo":
                case "greaterThan":
                case "closeTo":
                case "containsString":
                    result.add(examinedObj);
                    result.addAll(matcher.getArguments());
                    break;
                case "empty":
                case "emptyArray":
                case "emptyIterable":
                    result.add(examinedObj);
                    break;
                case "emptyCollectionOf":
                case "emptyIterableOf":
                    result.add(examinedObj);
                    result.add(matcher.getArguments().get(0));
                    result.add(examinedObj);
                    break;
                case "endsWith":
                    result.add(examinedObj);
                    result.add(examinedObj);
                    result.add(matcher.getArguments().get(0));
                    result.add(matcher.getArguments().get(0));
                    break;
            }

            if (errorMsg != null) result.add(errorMsg);

            return getTranslatedAssert(matcher).equals("") ? new Object[]{} : result.toArray();
        }
    }
}
