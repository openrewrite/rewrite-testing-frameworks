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
package org.openrewrite.java.testing.awaitility;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.SimplifyDurationCreationUnits;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class AwaitilityUseDuration extends Recipe {

    private static final String TIME_UNIT = "java.util.concurrent.TimeUnit";
    private static final List<MethodMatcher> MATCHERS = Stream.of(
                    "org.awaitility.core.ConditionFactory atMost(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.core.ConditionFactory atLeast(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.core.ConditionFactory during(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.core.ConditionFactory pollDelay(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.core.ConditionFactory pollInterval(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.core.ConditionFactory timeout(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.Awaitility waitAtMost(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.Awaitility setDefaultPollDelay(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.Awaitility setDefaultPollInterval(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.Awaitility setDefaultTimeout(long, java.util.concurrent.TimeUnit)",
                    "org.awaitility.pollinterval.FixedPollInterval fixed(long, java.util.concurrent.TimeUnit)")
            .map(MethodMatcher::new)
            .collect(toList());

    @Getter
    final String displayName = "Use `Duration` instead of `long` and `TimeUnit` in Awaitility";

    @Getter
    final String description = "Replace Awaitility timing calls such as `atMost(5, SECONDS)` with their `Duration` equivalent, `atMost(Duration.ofSeconds(5))`. The `Duration` overloads are more readable and make the unit impossible to confuse.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(MATCHERS.stream().map(UsesMethod::new).toArray(TreeVisitor[]::new)), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (MATCHERS.stream().noneMatch(matcher -> matcher.matches(m))) {
                    return m;
                }
                JavaType.Variable unit = timeUnitConstant(m.getArguments().get(1));
                String duration = unit == null ? null : duration(unit.getName());
                if (duration == null) {
                    return m;
                }
                maybeRemoveImport(TIME_UNIT);
                maybeRemoveImport(TIME_UNIT + "." + unit.getName());
                maybeAddImport("java.time.Duration");
                maybeAddImport("java.time.temporal.ChronoUnit");
                doAfterVisit(new SimplifyDurationCreationUnits().getVisitor());
                return JavaTemplate.builder(duration)
                        .imports("java.time.Duration", "java.time.temporal.ChronoUnit")
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().replaceArguments(), m.getArguments().get(0));
            }
        });
    }

    private static JavaType.@Nullable Variable timeUnitConstant(Expression unit) {
        J.Identifier name = unit instanceof J.FieldAccess ? ((J.FieldAccess) unit).getName() : unit instanceof J.Identifier ? (J.Identifier) unit : null;
        JavaType.Variable field = name == null ? null : name.getFieldType();
        return field != null && TypeUtils.isOfClassType(field.getOwner(), TIME_UNIT) ? field : null;
    }

    private static @Nullable String duration(String timeUnit) {
        switch (timeUnit) {
            case "NANOSECONDS":
                return "Duration.ofNanos(#{any(long)})";
            case "MICROSECONDS":
                return "Duration.of(#{any(long)}, ChronoUnit.MICROS)";
            case "MILLISECONDS":
                return "Duration.ofMillis(#{any(long)})";
            case "SECONDS":
                return "Duration.ofSeconds(#{any(long)})";
            case "MINUTES":
                return "Duration.ofMinutes(#{any(long)})";
            case "HOURS":
                return "Duration.ofHours(#{any(long)})";
            case "DAYS":
                return "Duration.ofDays(#{any(long)})";
            default:
                return null;
        }
    }
}
