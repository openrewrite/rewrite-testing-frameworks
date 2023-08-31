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
package org.openrewrite.java.testing.assertj;

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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.SimplifyDurationCreationUnits;

import java.util.*;

public class AdoptAssertJDurationAssertions extends Recipe {

    @Override
    public String getDisplayName() {
        return "Adopt AssertJ Duration assertions";
    }

    @Override
    public String getDescription() {
        return "Adopt AssertJ `DurationAssert` assertions for more expressive messages.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesMethod<>("org.assertj.core.api.AbstractDurationAssert has*(long)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractIntegerAssert isEqualTo(..)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractIntegerAssert isGreaterThan(..)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractIntegerAssert isLessThan(..)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractLongAssert isEqualTo(..)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractLongAssert isGreaterThan(..)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractLongAssert isLessThan(..)", true)
                ), new AdoptAssertJDurationAssertionsVisitor()
        );
    }

    @SuppressWarnings("DataFlowIssue")
    private static class AdoptAssertJDurationAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
        private static final MethodMatcher GET_NANO_MATCHER = new MethodMatcher("java.time.Duration getNano()");
        private static final MethodMatcher GET_SECONDS_MATCHER = new MethodMatcher("java.time.Duration getSeconds()");
        private static final List<MethodMatcher> IS_MATCHERS = Arrays.asList(
                new MethodMatcher("org.assertj.core.api.AbstractIntegerAssert isEqualTo(..)", true),
                new MethodMatcher("org.assertj.core.api.AbstractIntegerAssert isGreaterThan(..)", true),
                new MethodMatcher("org.assertj.core.api.AbstractIntegerAssert isLessThan(..)", true),
                new MethodMatcher("org.assertj.core.api.AbstractLongAssert isEqualTo(..)", true),
                new MethodMatcher("org.assertj.core.api.AbstractLongAssert isGreaterThan(..)", true),
                new MethodMatcher("org.assertj.core.api.AbstractLongAssert isLessThan(..)", true)
        );
        private static final Map<String, String> METHOD_MAP = new HashMap<String, String>() {{
            put("getSeconds", "hasSeconds");
            put("getNano", "hasNanos");
            put("hasMillis", "hasSeconds");
            put("hasSeconds", "hasMinutes");
            put("hasMinutes", "hasHours");
            put("hasHours", "hasDays");
            put("isGreaterThan", "isPositive");
            put("isLessThan", "isNegative");
            put("isEqualTo", "isZero");
        }};
        private static final MethodMatcher TIME_UNIT_MATCHERS = new MethodMatcher("org.assertj.core.api.AbstractDurationAssert has*(long)", true);

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (TIME_UNIT_MATCHERS.matches(mi)) {
                return simplifyTimeUnits(mi, ctx);
            } else if (IS_MATCHERS.stream().anyMatch(matcher -> matcher.matches(mi))) {
                return simplifyMultipleAssertions(mi, ctx);
            }
            return mi;
        }

        private J.MethodInvocation simplifyMultipleAssertions(J.MethodInvocation m, ExecutionContext ctx) {
            Expression isEqualToArg = m.getArguments().get(0);
            if (!ASSERT_THAT_MATCHER.matches(m.getSelect())) {
                return m;
            }

            J.MethodInvocation assertThatArg = (J.MethodInvocation) ((J.MethodInvocation) m.getSelect()).getArguments().get(0);
            if (!(assertThatArg instanceof J.MethodInvocation)) {
                return m;
            }

            Long isEqualToArgRaw = SimplifyDurationCreationUnits.getConstantIntegralValue(isEqualToArg);
            if (isEqualToArgRaw == 0) {
                String formatted_template = String.format("assertThat(#{any()}).%s()", METHOD_MAP.get(m.getSimpleName()));
                return applyTemplate(ctx, m, formatted_template, assertThatArg);
            }

            if (GET_NANO_MATCHER.matches(assertThatArg) || GET_SECONDS_MATCHER.matches(assertThatArg)) {
                Expression assertThatArgSelect = assertThatArg.getSelect();
                String methodName = assertThatArg.getSimpleName();
                String formatted_template = String.format("assertThat(#{any()}).%s(#{any()});", METHOD_MAP.get(methodName));
                return applyTemplate(ctx, m, formatted_template, assertThatArgSelect, isEqualToArg);
            }

            return m;
        }

        private J.MethodInvocation simplifyTimeUnits(J.MethodInvocation m, ExecutionContext ctx) {
            Expression arg = m.getArguments().get(0);
            Long argValue = SimplifyDurationCreationUnits.getConstantIntegralValue(arg);
            if (argValue == null) {
                return m;
            }

            List<Object> unitInfo = getUnitInfo(m.getSimpleName(), Math.toIntExact(argValue));
            String methodName = (String) unitInfo.get(0);
            int methodArg = (int) unitInfo.get(1);
            if (!(m.getSimpleName().equals(methodName))) {
                // update method invocation with new name and arg
                String template = String.format("#{any()}.%s(%d)", methodName, methodArg);
                return applyTemplate(ctx, m, template, m.getSelect());
            }

            return m;
        }

        private static List<Object> getUnitInfo(String name, int argValue) {
            int timeLength = 60;
            if (name.equals("hasMillis")) {
                timeLength = 1000;
            } else if (name.equals("hasHours")) {
                timeLength = 24;
            }

            if (argValue % timeLength == 0) {
                String newName = METHOD_MAP.get(name);
                return getUnitInfo(newName, argValue / timeLength);
            } else {
                // returning name, newArg
                return Arrays.asList(name, argValue);
            }
        }

        private J.MethodInvocation applyTemplate(ExecutionContext ctx, J.MethodInvocation m, String template, Object... parameters) {
            return JavaTemplate.builder(template)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), m.getCoordinates().replace(), parameters);
        }
    }
}
