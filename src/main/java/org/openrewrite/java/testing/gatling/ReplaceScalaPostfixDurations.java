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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openrewrite.Tree.randomId;

public class ReplaceScalaPostfixDurations extends Recipe {

    // The postfix conversions `scala.concurrent.duration` adds to numbers
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
        return Preconditions.check(GatlingSources.importingGatling(false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (!(j instanceof J.MethodInvocation)) {
                    return j;
                }
                J.MethodInvocation m = (J.MethodInvocation) j;
                if (m.getSelect() == null || !DURATION_UNITS.contains(m.getSimpleName()) ||
                    !isPostfix(m)) {
                    return m;
                }
                // The Scala parser models `5.seconds` as a field access, so produce exactly that
                return new J.FieldAccess(
                        randomId(),
                        m.getPrefix(),
                        Markers.EMPTY,
                        m.getSelect(),
                        JLeftPadded.build(m.getName().withPrefix(Space.EMPTY)),
                        m.getType());
            }

            // Postfix calls are parsed as infix notation without an argument. The marker is matched by name, as
            // `rewrite-scala` is not a dependency of this module
            private boolean isPostfix(J.MethodInvocation method) {
                if (method.getMarkers().getMarkers().stream()
                        .noneMatch(marker -> "InfixNotation".equals(marker.getClass().getSimpleName()))) {
                    return false;
                }
                List<Expression> arguments = method.getArguments();
                return arguments.isEmpty() || (arguments.size() == 1 && arguments.get(0) instanceof J.Empty);
            }
        });
    }
}
