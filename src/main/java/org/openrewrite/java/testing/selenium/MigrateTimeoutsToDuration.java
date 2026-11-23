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
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Collections.singletonList;

public class MigrateTimeoutsToDuration extends Recipe {

    private static final String TIMEOUTS = "org.openqa.selenium.WebDriver.Timeouts";
    private static final String FLUENT_WAIT = "org.openqa.selenium.support.ui.FluentWait";

    private static final Map<MethodMatcher, String> NEW_NAMES = new LinkedHashMap<>();

    static {
        NEW_NAMES.put(new MethodMatcher(TIMEOUTS + " implicitlyWait(long, java.util.concurrent.TimeUnit)", true), "implicitlyWait");
        NEW_NAMES.put(new MethodMatcher(TIMEOUTS + " setScriptTimeout(long, java.util.concurrent.TimeUnit)", true), "scriptTimeout");
        NEW_NAMES.put(new MethodMatcher(TIMEOUTS + " pageLoadTimeout(long, java.util.concurrent.TimeUnit)", true), "pageLoadTimeout");
        NEW_NAMES.put(new MethodMatcher(FLUENT_WAIT + " withTimeout(long, java.util.concurrent.TimeUnit)", true), "withTimeout");
        NEW_NAMES.put(new MethodMatcher(FLUENT_WAIT + " pollingEvery(long, java.util.concurrent.TimeUnit)", true), "pollingEvery");
    }

    @Getter
    final String displayName = "Pass Selenium timeouts as a `Duration`";

    @Getter
    final String description = "Selenium 4 removed the timeouts that took an amount and a `TimeUnit`. " +
            "`implicitlyWait`, `pageLoadTimeout`, `FluentWait.withTimeout` and `FluentWait.pollingEvery` take a " +
            "`java.time.Duration` instead, and `setScriptTimeout` became `scriptTimeout`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(NEW_NAMES.keySet().stream()
                        .map(UsesMethod::new)
                        .toArray(TreeVisitor[]::new)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        String newName = newName(m);
                        if (newName == null) {
                            return m;
                        }
                        Expression amount = m.getArguments().get(0);
                        Expression unit = m.getArguments().get(1);
                        Cursor amountCursor = new Cursor(updateCursor(m), amount);
                        Expression duration = SeleniumDurations.amountOf(amountCursor, amount, unit, ctx)
                                .withPrefix(amount.getPrefix());

                        maybeAddImport("java.time.Duration");
                        if (SeleniumDurations.usesChronoUnit(unit)) {
                            maybeAddImport("java.time.temporal.ChronoUnit");
                        }
                        maybeRemoveImport(SeleniumDurations.TIME_UNIT);
                        String constant = SeleniumDurations.timeUnitConstant(unit);
                        if (constant != null) {
                            maybeRemoveImport(SeleniumDurations.TIME_UNIT + "." + constant);
                        }

                        JavaType.Method type = SeleniumDurations.withSignature(m.getMethodType(), newName,
                                singletonList(SeleniumDurations.DURATION));
                        return SeleniumDurations.withArguments(m, singletonList(duration))
                                .withName(m.getName().withSimpleName(newName).withType(type))
                                .withMethodType(type);
                    }

                    private @Nullable String newName(J.MethodInvocation m) {
                        for (Map.Entry<MethodMatcher, String> entry : NEW_NAMES.entrySet()) {
                            if (entry.getKey().matches(m)) {
                                return entry.getValue();
                            }
                        }
                        return null;
                    }
                });
    }
}
