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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HamcrestMatcherToJUnit5 extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate from Hamcrest `Matcher` to JUnit5";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest `Matcher` to JUnit5 assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MigrationFromHamcrestVisitor();
    }

    private static class MigrationFromHamcrestVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            MethodMatcher matcherAssertMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(.., org.hamcrest.Matcher)");

            if (matcherAssertMatcher.matches(mi)) {
                Expression reason;
                Expression examinedObject;
                Expression hamcrestMatcher;

                if (mi.getArguments().size() == 2) {
                    reason = null;
                    examinedObject = mi.getArguments().get(0);
                    hamcrestMatcher = mi.getArguments().get(1);
                } else if (mi.getArguments().size() == 3) {
                    reason = mi.getArguments().get(0);
                    examinedObject = mi.getArguments().get(1);
                    hamcrestMatcher = mi.getArguments().get(2);
                } else return mi;

                if (hamcrestMatcher instanceof J.MethodInvocation) {
                    J.MethodInvocation matcherInvocation = (J.MethodInvocation)hamcrestMatcher;
                    maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                    String targetAssertion = getTranslatedAssert(matcherInvocation, false);
                    if (targetAssertion.equals("")) {
                        return mi;
                    }

                    String staticImport = "org.junit.jupiter.api.Assertions." + targetAssertion;

                    JavaTemplate template = JavaTemplate.builder(getTemplateForMatcher(matcherInvocation, reason, false))
                      .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "junit-jupiter-api-5.9"))
                      .staticImports(staticImport)
                      .build();

                    maybeAddImport("org.junit.jupiter.api.Assertions", targetAssertion);
                    return template.apply(getCursor(), method.getCoordinates().replace(),
                        getArgumentsForTemplate(matcherInvocation, reason, examinedObject, false));
                }
            }
            return mi;
        }

        private String getTranslatedAssert(J.MethodInvocation methodInvocation, boolean negated) {
            maybeRemoveImport("org.hamcrest.Matchers." + methodInvocation.getSimpleName());

            switch (methodInvocation.getSimpleName()) {
                case "equalTo":
                case "emptyArray":
                case "hasEntry":
                case "hasSize":
                case "hasToString":
                    return negated ? "assertNotEquals" : "assertEquals";
                case "closeTo":
                case "containsString":
                case "empty":
                case "endsWith":
                case "equalToIgnoringCase":
                case "greaterThan":
                case "greaterThanOrEqualTo":
                case "hasKey":
                case "hasValue":
                case "lessThan":
                case "lessThanOrEqualTo":
                case "startsWith":
                case "typeCompatibleWith":
                    return negated ? "assertFalse" : "assertTrue";
                case "instanceOf":
                case "isA":
                    return negated ? "assertFalse" : "assertInstanceOf";
                case "is":
                    if (Objects.requireNonNull(methodInvocation.getArguments().get(0).getType()).toString().startsWith("org.hamcrest")) {
                        if (methodInvocation.getArguments().get(0) instanceof J.MethodInvocation) {
                            return getTranslatedAssert((J.MethodInvocation)methodInvocation.getArguments().get(0), negated);
                        }
                    } else {
                        return negated ? "assertNotEquals" : "assertEquals";
                    }
                case "not":
                    if (Objects.requireNonNull(methodInvocation.getArguments().get(0).getType()).toString().startsWith("org.hamcrest")) {
                        if (methodInvocation.getArguments().get(0) instanceof J.MethodInvocation) {
                            return getTranslatedAssert((J.MethodInvocation)methodInvocation.getArguments().get(0), !negated);
                        }
                    } else {
                        return negated ? "assertEquals" : "assertNotEquals";
                    }
                case "notNullValue":
                    return negated ? "assertNull" : "assertNotNull";
                case "nullValue":
                    return negated ? "assertNotNull" : "assertNull";
                case "sameInstance":
                case "theInstance":
                    return negated ? "assertNotSame" : "assertSame";
                case "emptyIterable":
                    return negated ? "assertTrue" : "assertFalse";

            }
            return "";
        }

        private String getTemplateForMatcher(J.MethodInvocation matcher, @Nullable Expression errorMsg, boolean negated) {
            StringBuilder sb = new StringBuilder();
            sb.append(getTranslatedAssert(matcher, negated));

            switch (matcher.getSimpleName()) {
                case "equalTo":
                case "sameInstance":
                case "theInstance":
                    sb.append("(#{any(java.lang.Object)}, #{any(java.lang.Object)}");
                    break;
                case "closeTo":
                    sb.append("(Math.abs(#{any(double)} - #{any(double)}) < #{any(double)}");
                    break;
                case "containsString":
                    sb.append("(#{any(java.lang.String)}.contains(#{any(java.lang.String)}");
                    break;
                case "empty":
                    sb.append("(#{any(java.util.Collection)}.isEmpty()");
                    break;
                case "emptyArray":
                    sb.append("(0, #{anyArray(java.lang.Object)}.length");
                    break;
                case "emptyIterable":
                    sb.append("(#{any(java.lang.Iterable)}.iterator().hasNext()");
                    break;
                case "endsWith":
                    sb.append("(#{any(java.lang.String)}.endsWith(#{any(java.lang.String)})");
                    break;
                case "equalToIgnoringCase":
                    sb.append("(#{any(java.lang.String)}.equalsIgnoreCase(#{any(java.lang.String)})");
                    break;
                case "greaterThan":
                    sb.append("(#{any(double)} > #{any(double)}");
                    break;
                case "greaterThanOrEqualTo":
                    sb.append("(#{any(double)} >= #{any(double)}");
                    break;
                case "hasEntry":
                    if (containsMatcherAsArgument(matcher)) {
                        return "";
                    }
                    sb.append("(#{any(java.lang.Object)}, #{any(java.util.Map)}.get(#{any(java.lang.Object)})");
                    break;
                case "hasKey":
                    if (containsMatcherAsArgument(matcher)) {
                        return "";
                    }
                    sb.append("(#{any(java.util.Map)}.containsKey(#{any(java.lang.Object)})");
                    break;
                case "hasSize":
                    if (containsMatcherAsArgument(matcher)) {
                        return "";
                    }
                    sb.append("(#{any(java.util.Collection)}.size(), #{any(double)}");
                    break;
                case "hasToString":
                    if (containsMatcherAsArgument(matcher)) {
                        return "";
                    }
                    sb.append("(#{any(java.lang.Object)}.toString(), #{any(java.lang.String)}");
                    break;
                case "hasValue":
                    if (containsMatcherAsArgument(matcher)) {
                        return "";
                    }
                    sb.append("(#{any(java.util.Map)}.containsValue(#{any(java.lang.Object)})");
                    break;
                case "instanceOf":
                case "isA":
                    if (negated) {
                        sb.append("(#{any(java.lang.Class)}.isAssignableFrom(#{any(java.lang.Object)}.getClass())");
                    } else {
                        sb.append("(#{any(java.lang.Class)}, #{any(java.lang.Object)}");
                    }
                    break;
                case "is":
                    if (containsMatcherAsArgument(matcher)) {
                        if (matcher.getArguments().get(0) instanceof J.MethodInvocation) {
                            return getTemplateForMatcher((J.MethodInvocation) matcher.getArguments().get(0), errorMsg, negated);
                        }
                    } else {
                        sb.append("(#{any(java.lang.Object)}, #{any(java.lang.Object)}");
                    }
                    break;
                case "lessThan":
                    sb.append("(#{any(double)} < #{any(double)}");
                    break;
                case "lessThanOrEqualTo":
                    sb.append("(#{any(double)} <= #{any(double)}");
                    break;
                case "not":
                    if (containsMatcherAsArgument(matcher)) {
                        if (matcher.getArguments().get(0) instanceof J.MethodInvocation) {
                            return getTemplateForMatcher((J.MethodInvocation) matcher.getArguments().get(0), errorMsg, !negated);
                        }
                    } else {
                        sb.append("(#{any(java.lang.Object)}, #{any(java.lang.Object)}");
                    }
                    break;
                case "notNullValue":
                case "nullValue":
                    sb.append("(#{any(java.lang.Object)}");
                    break;
                case "startsWith":
                    sb.append("(#{any(java.lang.String)}.startsWith(#{any(java.lang.String)})");
                    break;
                case "typeCompatibleWith":
                    sb.append("(#{any(java.lang.Class)}.isAssignableFrom(#{any(java.lang.Class)})");
                    break;
                default:
                    return "";
            }

            if (errorMsg != null) {
                sb.append(", #{any(java.lang.String)})");
            } else {
                sb.append(")");
            }
            return sb.toString();
        }

        private Object[] getArgumentsForTemplate(J.MethodInvocation matcher, @Nullable Expression errorMsg, Expression examinedObj, boolean negated) {
            List<Expression> result = new ArrayList<>();

            switch (matcher.getSimpleName()) {
                case "equalTo":
                case "closeTo":
                case "containsString":
                case "endsWith":
                case "equalToIgnoringCase":
                case "greaterThan":
                case "greaterThanOrEqualTo":
                case "hasKey":
                case "hasSize":
                case "hasToString":
                case "hasValue":
                case "lessThan":
                case "lessThanOrEqualTo":
                case "sameInstance":
                case "startsWith":
                case "theInstance":
                    result.add(examinedObj);
                    result.addAll(matcher.getArguments());
                    break;
                case "empty":
                case "emptyArray":
                case "emptyIterable":
                case "notNullValue":
                case "nullValue":
                    result.add(examinedObj);
                    break;
                case "hasEntry":
                    result.add(matcher.getArguments().get(1));
                    result.add(examinedObj);
                    result.add(matcher.getArguments().get(0));
                    break;
                case "instanceOf":
                case "isA":
                case "typeCompatibleWith":
                    result.add(matcher.getArguments().get(0));
                    result.add(examinedObj);
                    break;
                case "is":
                    if (containsMatcherAsArgument(matcher)) {
                        if (matcher.getArguments().get(0) instanceof J.MethodInvocation) {
                            return getArgumentsForTemplate((J.MethodInvocation) matcher.getArguments().get(0), errorMsg, examinedObj, negated);
                        }
                    } else {
                        result.add(examinedObj);
                        result.addAll(matcher.getArguments());
                    }
                    break;
                case "not":
                    if (containsMatcherAsArgument(matcher)) {
                        if (matcher.getArguments().get(0) instanceof J.MethodInvocation) {
                            return getArgumentsForTemplate((J.MethodInvocation) matcher.getArguments().get(0), errorMsg, examinedObj, !negated);
                        }
                    } else {
                        result.add(examinedObj);
                        result.addAll(matcher.getArguments());
                    }
                    break;
                default:
                    return new Object[]{};
            }

            if (errorMsg != null) result.add(errorMsg);

            return result.toArray();
        }
    }

    private static boolean containsMatcherAsArgument(J.MethodInvocation matcher) {
        return Objects.requireNonNull(matcher.getArguments().get(0).getType()).toString().startsWith("org.hamcrest");
    }
}
