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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.SimplifyDurationCreationUnits;

import java.util.*;

public class AdoptAssertJDurationAssertions extends Recipe {
    static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    static final MethodMatcher GET_NANO_MATCHER = new MethodMatcher("java.time.Duration getNano()");
    static final MethodMatcher GET_SECONDS_MATCHER = new MethodMatcher("java.time.Duration getSeconds()");
    static final MethodMatcher ISEQUALTO_INT_MATCHER = new MethodMatcher("org.assertj.core.api.AbstractIntegerAssert isEqualTo(..)");
    static final MethodMatcher ISEQUALTO_LONG_MATCHER = new MethodMatcher("org.assertj.core.api.AbstractLongAssert isEqualTo(..)");
    static final Map<String, String> methodMap = new HashMap<String, String>() {{
        put("getSeconds", "hasSeconds");
        put("getNano", "hasNanos");
        put("hasMillis", "hasSeconds");
        put("hasSeconds", "hasMinutes");
        put("hasMinutes", "hasHours");
        put("hasHours", "hasDays");
    }};
    static List<MethodMatcher> timeUnitMatchers = Arrays.asList(
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasMillis(..)", true),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasSeconds(..)", true),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasNanos(..)", true),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasMinutes(..)", true),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasHours(..)", true),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasDays(..)", true)
    );

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
        // TODO: figure out why this Precondition check doesn't quite work
        return Preconditions.check(Preconditions.or(
                        new UsesMethod<>("org.assertj.core.api.AbstractDurationAssert has*(..)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractLongAssert isEqualTo(..)", true),
                        new UsesMethod<>("org.assertj.core.api.AbstractIntegerAssert isEqualTo(..)", true)
                ), new AdoptAssertJDurationAssertionsVisitor()
        );
    }

    @SuppressWarnings("DataFlowIssue")
    private static class AdoptAssertJDurationAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);
            if (timeUnitMatchers.stream().anyMatch(matcher -> matcher.matches(mi))) {
                return simplifyTimeUnits(mi, ctx);
            } else if (ISEQUALTO_INT_MATCHER.matches(mi) || ISEQUALTO_LONG_MATCHER.matches(mi)) {
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
            if (GET_NANO_MATCHER.matches(assertThatArg) || GET_SECONDS_MATCHER.matches(assertThatArg)) {
                String methodName = assertThatArg.getSimpleName();
                Expression assertThatArgSelect = assertThatArg.getSelect();
                String formatted_template = String.format("assertThat(#{any()}).%s(#{any()});", methodMap.get(methodName));
                return JavaTemplate.builder(formatted_template)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9", "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), assertThatArgSelect, isEqualToArg);
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
                // convert divided value to OpenRewrite LST type
                J.Literal newArg = rawValueToExpression(methodArg);
                // update method invocation with new name and arg
                Expression methodSelect = m.getSelect();
                return JavaTemplate.builder("#{any()}.#{}(#{any(int)})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), methodSelect, methodName, newArg);
            }

            return m;
        }

        private List<Object> getUnitInfo(String name, int argValue) {
            int timeLength = 60;
            if (name.equals("hasMillis")) {
                timeLength = 1000;
            } else if (name.equals("hasHours")) {
                timeLength = 24;
            }

            if (argValue % timeLength == 0) {
                String newName = methodMap.get(name);
                return getUnitInfo(newName, argValue / timeLength);
            } else {
                // returning name, newArg, isDivisible
                return Arrays.asList(name, argValue);
            }
        }

        private J.Literal rawValueToExpression(int rawValue) {
            return new J.Literal(
                    UUID.randomUUID(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    rawValue,
                    String.valueOf(rawValue),
                    null,
                    JavaType.Primitive.Int
            );
        }
    }
}
