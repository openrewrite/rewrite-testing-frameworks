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
package org.openrewrite.java.testing.junit5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

@Value
@EqualsAndHashCode(callSuper = false)
public class ImplausibleTimeoutToMinutes extends Recipe {

    private static final String TIMEOUT = "org.junit.jupiter.api.Timeout";
    private static final AnnotationMatcher TIMEOUT_MATCHER = new AnnotationMatcher("@" + TIMEOUT);

    @Option(displayName = "Threshold in seconds",
            description = "Timeouts of at least this many seconds (when the time unit is the default `SECONDS`) are " +
                    "considered implausibly long and are rewritten to the equivalent number of minutes. " +
                    "Defaults to `1000` seconds, about 17 minutes.",
            required = false,
            example = "1000")
    @Nullable
    Integer thresholdSeconds;

    String displayName = "Make implausibly long `@Timeout` values explicit in minutes";

    String description = "JUnit Jupiter's `@Timeout` defaults to `TimeUnit.SECONDS`, so a value such as `@Timeout(10000)` is " +
            "interpreted as almost three hours, which is most likely a mistake where milliseconds were intended. " +
            "This recipe rewrites such implausibly large second-based timeouts to the equivalent number of minutes, " +
            "for instance `@Timeout(value = 167, unit = TimeUnit.MINUTES)`, preserving the original (likely " +
            "erroneous) semantics while making the mistake far more visible for review.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        long threshold = thresholdSeconds == null ? 1000 : thresholdSeconds;
        return Preconditions.check(new UsesType<>(TIMEOUT, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!TIMEOUT_MATCHER.matches(a) || a.getArguments() == null || a.getArguments().isEmpty()) {
                    return a;
                }

                Long seconds = null;
                boolean defaultUnit = true;
                for (Expression arg : a.getArguments()) {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                        if ("value".equals(name)) {
                            seconds = longValue(assignment.getAssignment());
                        } else if ("unit".equals(name)) {
                            defaultUnit = isSeconds(assignment.getAssignment());
                        }
                    } else {
                        seconds = longValue(arg);
                    }
                }

                if (seconds == null || !defaultUnit || seconds < threshold) {
                    return a;
                }

                long minutes = Math.round(seconds / 60.0);
                maybeAddImport("java.util.concurrent.TimeUnit");
                return JavaTemplate.builder("@Timeout(value = " + minutes + ", unit = TimeUnit.MINUTES)")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "junit-jupiter-api-5"))
                        .imports(TIMEOUT, "java.util.concurrent.TimeUnit")
                        .build()
                        .apply(getCursor(), a.getCoordinates().replace());
            }

            private @Nullable Long longValue(Expression expr) {
                if (expr instanceof J.Literal) {
                    Object value = ((J.Literal) expr).getValue();
                    if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    }
                    if (value instanceof Long) {
                        return (Long) value;
                    }
                }
                return null;
            }

            private boolean isSeconds(Expression expr) {
                if (expr instanceof J.FieldAccess) {
                    return "SECONDS".equals(((J.FieldAccess) expr).getSimpleName());
                }
                if (expr instanceof J.Identifier) {
                    return "SECONDS".equals(((J.Identifier) expr).getSimpleName());
                }
                return false;
            }
        });
    }
}
