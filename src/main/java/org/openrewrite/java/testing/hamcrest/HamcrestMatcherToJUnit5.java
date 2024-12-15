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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class HamcrestMatcherToJUnit5 extends Recipe {

    private static final MethodMatcher MATCHER_ASSERT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(.., org.hamcrest.Matcher)");

    @Override
    public String getDisplayName() {
        return "Migrate from Hamcrest `Matcher` to JUnit 5";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest `Matcher` to JUnit 5 assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(MATCHER_ASSERT_MATCHER),
                new MigrationFromHamcrestVisitor());
    }

    enum Replacement {
        EQUALTO("equalTo", "assertEquals", "assertNotEquals", "#{any(java.lang.Object)}, #{any(java.lang.Object)}", "examinedObjThenMatcherArgs"),
        EMPTYARRAY("emptyArray", "assertEquals", "assertNotEquals", "0, #{anyArray(java.lang.Object)}.length", "examinedObjOnly"),
        HASENTRY("hasEntry", "assertEquals", "assertNotEquals", "#{any(java.lang.Object)}, #{any(java.util.Map)}.get(#{any(java.lang.Object)})", "matcher1ExaminedObjMatcher0"),
        HASSIZE("hasSize", "assertEquals", "assertNotEquals", "#{any(java.util.Collection)}.size(), #{any(double)}", "examinedObjThenMatcherArgs"),
        HASTOSTRING("hasToString", "assertEquals", "assertNotEquals", "#{any(java.lang.Object)}.toString(), #{any(java.lang.String)}", "examinedObjThenMatcherArgs"),
        CLOSETO("closeTo", "assertTrue", "assertFalse", "Math.abs(#{any(double)} - #{any(double)}) < #{any(double)}", "examinedObjThenMatcherArgs"),
        CONTAINSSTRING("containsString", "assertTrue", "assertFalse", "#{any(java.lang.String)}.contains(#{any(java.lang.String)}", "examinedObjThenMatcherArgs"),
        EMPTY("empty", "assertTrue", "assertFalse", "#{any(java.util.Collection)}.isEmpty()", "examinedObjOnly"),
        ENDSWITH("endsWith", "assertTrue", "assertFalse", "#{any(java.lang.String)}.endsWith(#{any(java.lang.String)})", "examinedObjThenMatcherArgs"),
        EQUALTOIGNORINGCASE("equalToIgnoringCase", "assertTrue", "assertFalse", "#{any(java.lang.String)}.equalsIgnoreCase(#{any(java.lang.String)})", "examinedObjThenMatcherArgs"),
        GREATERTHAN("greaterThan", "assertTrue", "assertFalse", "#{any(double)} > #{any(double)}", "examinedObjThenMatcherArgs"),
        GREATERTHANOREQUALTO("greaterThanOrEqualTo", "assertTrue", "assertFalse", "#{any(double)} >= #{any(double)}", "examinedObjThenMatcherArgs"),
        HASKEY("hasKey", "assertTrue", "assertFalse", "#{any(java.util.Map)}.containsKey(#{any(java.lang.Object)})", "examinedObjThenMatcherArgs"),
        HASVALUE("hasValue", "assertTrue", "assertFalse", "#{any(java.util.Map)}.containsValue(#{any(java.lang.Object)})", "examinedObjThenMatcherArgs"),
        LESSTHAN("lessThan", "assertTrue", "assertFalse", "#{any(double)} < #{any(double)}", "examinedObjThenMatcherArgs"),
        LESSTHANOREQUALTO("lessThanOrEqualTo", "assertTrue", "assertFalse", "#{any(double)} <= #{any(double)}", "examinedObjThenMatcherArgs"),
        STARTSWITH("startsWith", "assertTrue", "assertFalse", "#{any(java.lang.String)}.startsWith(#{any(java.lang.String)})", "examinedObjThenMatcherArgs"),
        TYPECOMPATIBLEWITH("typeCompatibleWith", "assertTrue", "assertFalse", "#{any(java.lang.Class)}.isAssignableFrom(#{any(java.lang.Class)})", "matcherArgsThenExaminedObj"),
        NOTNULLVALUE("notNullValue", "assertNotNull", "assertNull", "#{any(java.lang.Object)}", "examinedObjOnly"),
        NULLVALUE("nullValue", "assertNull", "assertNotNull", "#{any(java.lang.Object)}", "examinedObjOnly"),
        SAMEINSTANCE("sameInstance", "assertSame", "assertNotSame", "#{any(java.lang.Object)}, #{any(java.lang.Object)}", "examinedObjThenMatcherArgs"),
        THEINSTANCE("theInstance", "assertSame", "assertNotSame", "#{any(java.lang.Object)}, #{any(java.lang.Object)}", "examinedObjThenMatcherArgs"),
        EMPTYITERABLE("emptyIterable", "assertFalse", "assertTrue", "#{any(java.lang.Iterable)}.iterator().hasNext()", "examinedObjOnly");

        final String hamcrest, junitPositive, junitNegative, template;
        final String argumentsMethod;

        private static final Map<String, BiFunction<Expression, J.MethodInvocation, List<Expression>>> methods = new HashMap<>();

        static {
            methods.put("examinedObjThenMatcherArgs", (ex, matcher) -> {
                List<Expression> arguments = matcher.getArguments();
                arguments.add(0, ex);
                return arguments;
            });
            methods.put("matcherArgsThenExaminedObj", (ex, matcher) -> {
                List<Expression> arguments = matcher.getArguments();
                arguments.add(ex);
                return arguments;
            });
            methods.put("examinedObjOnly", (ex, matcher) -> {
                List<Expression> arguments = new ArrayList<>();
                arguments.add(ex);
                return arguments;
            });
            methods.put("matcher1ExaminedObjMatcher0", (ex, matcher) -> {
                List<Expression> arguments = new ArrayList<>();
                arguments.add(matcher.getArguments().get(1));
                arguments.add(ex);
                arguments.add(matcher.getArguments().get(0));
                return arguments;
            });
        }

        Replacement(String hamcrest, String junitPositive, String junitNegative, String template, String argumentsMethod) {
            this.hamcrest = hamcrest;
            this.junitPositive = junitPositive;
            this.junitNegative = junitNegative;
            this.template = template;
            this.argumentsMethod = argumentsMethod;
        }
    }

    private static class MigrationFromHamcrestVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            if (MATCHER_ASSERT_MATCHER.matches(mi)) {
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
                } else {
                    return mi;
                }

                if (hamcrestMatcher instanceof J.MethodInvocation) {
                    J.MethodInvocation matcherInvocation = (J.MethodInvocation) hamcrestMatcher;
                    maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");

                    while ("not".equals(matcherInvocation.getSimpleName())) {
                        maybeRemoveImport("org.hamcrest.Matchers.not");
                        maybeRemoveImport("org.hamcrest.CoreMatchers.not");
                        matcherInvocation = (J.MethodInvocation) new RemoveNotMatcherVisitor().visit(matcherInvocation, ctx);
                    }

                    //we do not handle nested matchers
                    if (!(matcherInvocation.getArguments().get(0) instanceof J.Empty)) {
                        if ((matcherInvocation.getArguments().get(0).getType()).toString().startsWith("org.hamcrest")) {
                            return mi;
                        }
                    }

                    boolean logicalContext = RemoveNotMatcherVisitor.getLogicalContext(matcherInvocation, ctx);

                    Replacement replacement;
                    try {
                        replacement = Replacement.valueOf(matcherInvocation.getSimpleName().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return mi;
                    }
                    String assertion = logicalContext ? replacement.junitPositive : replacement.junitNegative;
                    String templateString = assertion + "(" + replacement.template + (reason == null ? ")" : ", #{any(java.lang.String)})");
                    JavaTemplate template = JavaTemplate.builder(templateString)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9"))
                            .staticImports("org.junit.jupiter.api.Assertions." + assertion)
                            .build();

                    maybeRemoveImport("org.hamcrest.Matchers." + replacement.hamcrest);
                    maybeRemoveImport("org.hamcrest.CoreMatchers." + replacement.hamcrest);
                    maybeAddImport("org.junit.jupiter.api.Assertions", assertion);

                    List<Expression> arguments = Replacement.methods.get(replacement.argumentsMethod).apply(examinedObject, matcherInvocation);
                    if (reason != null) {
                        arguments.add(reason);
                    }

                    return template.apply(getCursor(), method.getCoordinates().replace(), arguments.toArray());
                }
            }
            return mi;
        }
    }
}
