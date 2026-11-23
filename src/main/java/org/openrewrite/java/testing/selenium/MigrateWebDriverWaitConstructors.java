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
package org.openrewrite.java.testing.selenium;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class MigrateWebDriverWaitConstructors extends Recipe {

    private static final String WEB_DRIVER_WAIT = "org.openqa.selenium.support.ui.WebDriverWait";

    private static final MethodMatcher TIMEOUT = new MethodMatcher(
            WEB_DRIVER_WAIT + " <constructor>(org.openqa.selenium.WebDriver, long)");
    private static final MethodMatcher TIMEOUT_AND_SLEEP = new MethodMatcher(
            WEB_DRIVER_WAIT + " <constructor>(org.openqa.selenium.WebDriver, long, long)");
    private static final MethodMatcher CLOCK_SLEEPER_TIMEOUT_AND_SLEEP = new MethodMatcher(
            WEB_DRIVER_WAIT + " <constructor>(org.openqa.selenium.WebDriver, java.time.Clock, org.openqa.selenium.support.ui.Sleeper, long, long)");

    @Getter
    final String displayName = "Pass `WebDriverWait` timeouts as a `Duration`";

    @Getter
    final String description = "Selenium 4 removed the `WebDriverWait` constructors that took the timeout in seconds " +
            "and the sleep between polls in milliseconds as a `long`. Pass them as a `java.time.Duration` instead, " +
            "including from the `super` call of a subclass.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // An anonymous subclass calls a constructor of its own, which `UsesMethod` does not match
        return Preconditions.check(new UsesType<>(WEB_DRIVER_WAIT, false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        List<Expression> args = durations(SeleniumDurations.constructorType(nc), nc.getArguments(), updateCursor(nc));
                        if (args == null) {
                            return nc;
                        }
                        return nc.withArguments(args).withConstructorType(newType(nc.getConstructorType()));
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        // `super(driver, 10)` in a subclass
                        List<Expression> args = durations(m.getMethodType(), m.getArguments(), updateCursor(m));
                        if (args == null) {
                            return m;
                        }
                        JavaType.Method type = newType(m.getMethodType());
                        return m.withArguments(args)
                                .withName(m.getName().withType(type))
                                .withMethodType(type);
                    }

                    private @Nullable List<Expression> durations(JavaType.@Nullable Method type, List<Expression> args, Cursor cursor) {
                        List<Expression> newArgs;
                        if (TIMEOUT.matches(type)) {
                            newArgs = asList(args.get(0), seconds(cursor, args.get(1)));
                        } else if (TIMEOUT_AND_SLEEP.matches(type)) {
                            newArgs = asList(args.get(0), seconds(cursor, args.get(1)), millis(cursor, args.get(2)));
                        } else if (CLOCK_SLEEPER_TIMEOUT_AND_SLEEP.matches(type)) {
                            // Selenium 4 takes the durations before the clock and sleeper
                            newArgs = asList(args.get(0),
                                    seconds(cursor, args.get(3)).withPrefix(args.get(1).getPrefix()),
                                    millis(cursor, args.get(4)).withPrefix(args.get(2).getPrefix()),
                                    args.get(1).withPrefix(args.get(3).getPrefix()),
                                    args.get(2).withPrefix(args.get(4).getPrefix()));
                        } else {
                            return null;
                        }
                        maybeAddImport("java.time.Duration");
                        return newArgs;
                    }

                    private Expression seconds(Cursor cursor, Expression amount) {
                        return SeleniumDurations.amountOf(new Cursor(cursor, amount), amount, "ofSeconds")
                                .withPrefix(amount.getPrefix());
                    }

                    private Expression millis(Cursor cursor, Expression amount) {
                        return SeleniumDurations.amountOf(new Cursor(cursor, amount), amount, "ofMillis")
                                .withPrefix(amount.getPrefix());
                    }

                    private JavaType.@Nullable Method newType(JavaType.@Nullable Method type) {
                        if (type == null) {
                            return null;
                        }
                        List<JavaType> parameterTypes = new ArrayList<>(type.getParameterTypes());
                        if (parameterTypes.size() == 5) {
                            JavaType clock = parameterTypes.get(1);
                            JavaType sleeper = parameterTypes.get(2);
                            parameterTypes = asList(parameterTypes.get(0), SeleniumDurations.DURATION,
                                    SeleniumDurations.DURATION, clock, sleeper);
                        } else {
                            for (int i = 1; i < parameterTypes.size(); i++) {
                                parameterTypes.set(i, SeleniumDurations.DURATION);
                            }
                        }
                        return SeleniumDurations.withSignature(type, type.getName(), parameterTypes);
                    }
                });
    }
}
