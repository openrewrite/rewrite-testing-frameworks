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
    static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    static final MethodMatcher GET_NANO_MATCHER = new MethodMatcher("java.time.Duration getNano()");
    static final MethodMatcher GET_SECONDS_MATCHER = new MethodMatcher("java.time.Duration getSeconds()");
    static final MethodMatcher ISEQUALTO_MATCHER = new MethodMatcher("org.assertj.core.api.AbstractLongAssert isEqualTo(..)");
    static final Map<String, String> methodMap = new HashMap<String, String>() {{
        put("getSeconds", "hasSeconds");
        put("getNano", "hasNanos");
    }};
    static List<MethodMatcher> timeUnitMatchers = Arrays.asList(
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasMillis(..)"),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasSeconds(..)"),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasNanos(..)"),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasMinutes(..)"),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasHours(..)"),
            new MethodMatcher("org.assertj.core.api.AbstractDurationAssert hasDays(..)")
    );

    @Override
    public String getDisplayName() {
        return "Adopt AssertJ Duration assertions";
    }

    @Override
    public String getDescription() {
        return "Adopt AssertJ DurationAssert assertions for more expressive messages.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesMethod<>("org.assertj.core.api.AbstractDurationAssert has*(int)"),
                new UsesMethod<>("org.assertj.core.api.AbstractLongAssert isEqualTo(..)")
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
            }else if (ISEQUALTO_MATCHER.matches(mi)) {
                return simplifyMultipleAssertions(mi, ctx);
            }
            return mi;
        }

        private J.MethodInvocation simplifyMultipleAssertions(J.MethodInvocation m, ExecutionContext ctx) {
            if (!ISEQUALTO_MATCHER.matches(m)) {
                return m;
            }
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
            int timeLength = 60;
            String mName = m.getSimpleName();
            if (mName.equals("hasMillis")) {
                timeLength = 1000;
            }else if (mName.equals("hasHours")) {
                timeLength = 24;
            }

            Expression arg = m.getArguments().get(0);
            Long argValue = SimplifyDurationCreationUnits.getConstantIntegralValue(arg); // note: guess my machine hasn't updated to the new commit yet, method should be public
            if (argValue % timeLength == 0) {
                // convert divided value to OpenRewrite LST type
                int rawValue = (int)(argValue / timeLength);
                J.Literal newArg = rawValueToExpression(rawValue);
                // get new method name

                // update method invocation with new name and arg
                Expression methodSelect = m.getSelect();
                return JavaTemplate.builder("#{any()}.#{any()}(#{int})")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9", "assertj-core-3.24"))
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), methodSelect, "new method name", newArg);
            }

            return m;
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
