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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class AwaitilityUseDuration extends Recipe {

    private static final List<String> METHODS = Arrays.asList("atMost", "atLeast", "pollInterval", "pollDelay", "timeout");
    private static final List<MethodMatcher> MATCHERS = METHODS.stream()
            .map(name -> new MethodMatcher("org.awaitility.core.ConditionFactory " + name + "(long, java.util.concurrent.TimeUnit)"))
            .collect(toList());

    @Getter
    final String displayName = "Use `Duration` instead of `long` and `TimeUnit` in Awaitility";

    @Getter
    final String description = "Replace Awaitility timing calls such as `atMost(5, SECONDS)` with their `Duration` " +
            "equivalent, `atMost(Duration.ofSeconds(5))`. The `Duration` overloads are more readable and make the unit " +
            "impossible to confuse.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> precondition = Preconditions.or(MATCHERS.stream()
                .map(UsesMethod::new)
                .toArray(UsesMethod[]::new));
        return Preconditions.check(precondition, new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (m.getSelect() == null || MATCHERS.stream().noneMatch(matcher -> matcher.matches(m))) {
                    return m;
                }
                String factory = durationFactory(m.getArguments().get(1));
                if (factory == null) {
                    return m;
                }
                maybeRemoveImport("java.util.concurrent.TimeUnit");
                maybeRemoveImport("java.util.concurrent.TimeUnit." + unitName(m.getArguments().get(1)));
                maybeAddImport("java.time.Duration");
                return JavaTemplate.builder("#{any(org.awaitility.core.ConditionFactory)}." + m.getSimpleName() +
                                "(Duration." + factory + "(#{any(long)}))")
                        .imports("java.time.Duration")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "awaitility-4", "hamcrest"))
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().replace(), m.getSelect(), m.getArguments().get(0));
            }

            private @Nullable String durationFactory(Expression unit) {
                if (!TypeUtils.isOfClassType(unit.getType(), "java.util.concurrent.TimeUnit")) {
                    return null;
                }
                String name = unitName(unit);
                switch (name) {
                    case "NANOSECONDS":
                        return "ofNanos";
                    case "MILLISECONDS":
                        return "ofMillis";
                    case "SECONDS":
                        return "ofSeconds";
                    case "MINUTES":
                        return "ofMinutes";
                    case "HOURS":
                        return "ofHours";
                    case "DAYS":
                        return "ofDays";
                    default:
                        return null;
                }
            }

            private String unitName(Expression unit) {
                if (unit instanceof J.FieldAccess) {
                    return ((J.FieldAccess) unit).getSimpleName();
                }
                return unit instanceof J.Identifier ? ((J.Identifier) unit).getSimpleName() : "";
            }
        });
    }
}
