/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.gatling;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.OmitParentheses;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Marker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.openrewrite.Tree.randomId;

public class ReplaceScalaPostfixDurations extends Recipe {

    private static final MethodMatcher DURATION_UNIT = new MethodMatcher("scala.concurrent.duration.DurationConversions *()");

    // Matched by name too, as calls inside Gatling DSL calls the compiler cannot resolve are untyped
    private static final Set<String> DURATION_UNITS = new HashSet<>(Arrays.asList(
            "nanoseconds", "nanosecond", "nanos", "nano",
            "microseconds", "microsecond", "micros", "micro",
            "milliseconds", "millisecond", "millis", "milli",
            "seconds", "second",
            "minutes", "minute",
            "hours", "hour",
            "days", "day"));

    @Getter
    final String displayName = "Replace postfix durations like `5 seconds` with `5.seconds`";

    @Getter
    final String description = "Gatling 3.5 moved to Scala 2.13, which is phasing out postfix operators, so the " +
            "Gatling upgrade guide recommends writing durations as `5.seconds` rather than `5 seconds`. Postfix " +
            "notation only compiles with `-language:postfixOps`, and Scala 3 drops it altogether. Rewrite the " +
            "postfix `scala.concurrent.duration` units in Scala sources importing Gatling.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(GatlingSources.importingGatling(false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                boolean durationUnit = m.getMethodType() == null ? DURATION_UNITS.contains(m.getSimpleName()) && m.getArguments().stream().allMatch(J.Empty.class::isInstance) : DURATION_UNIT.matches(m);
                if (!durationUnit || m.getSelect() == null || m.getMarkers().getMarkers().stream().noneMatch(ReplaceScalaPostfixDurations::isInfixNotation)) {
                    return m;
                }
                return m.withMarkers(m.getMarkers().withMarkers(ListUtils.filter(m.getMarkers().getMarkers(), marker -> !isInfixNotation(marker))))
                        .withName(m.getName().withPrefix(Space.EMPTY))
                        .getPadding().withArguments(m.getPadding().getArguments().withMarkers(m.getPadding().getArguments().getMarkers().add(new OmitParentheses(randomId()))));
            }
        });
    }

    // Matched by name, as `rewrite-scala` is not a dependency of this module
    private static boolean isInfixNotation(Marker marker) {
        return "InfixNotation".equals(marker.getClass().getSimpleName());
    }
}
