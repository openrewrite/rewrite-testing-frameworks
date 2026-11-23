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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MigrateLegacyCapabilities extends Recipe {

    private static final MethodMatcher SET_CAPABILITY = new MethodMatcher("org.openqa.selenium.MutableCapabilities setCapability(String, ..)", true);
    private static final MethodMatcher SET_JAVASCRIPT_ENABLED = new MethodMatcher("org.openqa.selenium.remote.DesiredCapabilities setJavascriptEnabled(boolean)", true);

    /**
     * Legacy names of capabilities that W3C defines under another name.
     */
    private static final Map<String, String> W3C_NAMES = new HashMap<>();

    static {
        W3C_NAMES.put("platform", "platformName");
        W3C_NAMES.put("version", "browserVersion");
        W3C_NAMES.put("acceptSslCerts", "acceptInsecureCerts");
        W3C_NAMES.put("unexpectedAlertBehaviour", "unhandledPromptBehavior");
        W3C_NAMES.put("pageLoadingStrategy", "pageLoadStrategy");
        W3C_NAMES.put("loggingPrefs", "goog:loggingPrefs");
    }

    /**
     * Legacy capabilities without a W3C equivalent. Selenium 3 already left them out of what it sent a W3C
     * driver, while Selenium 4 refuses to start a session with them at all.
     */
    private static final Set<String> WITHOUT_W3C_EQUIVALENT = new HashSet<>(Arrays.asList(
            "javascriptEnabled",
            "takesScreenshot",
            "handlesAlerts",
            "databaseEnabled",
            "locationContextEnabled",
            "applicationCacheEnabled",
            "networkConnectionEnabled",
            "cssSelectorsEnabled",
            "webStorageEnabled",
            "nativeEvents",
            "hasTouchScreen",
            "elementScrollBehavior",
            "overlappingCheckDisabled",
            "enablePersistentHover",
            "webdriver.logging.profiler.enabled"));

    @Getter
    final String displayName = "Use W3C capability names";

    @Getter
    final String description = "Selenium 4 refuses to start a session with a capability whose name is neither a " +
            "W3C name nor carries a vendor prefix such as `goog:`. Rename legacy capabilities that W3C defines " +
            "under another name, such as `platform` to `platformName` and `version` to `browserVersion`, and stop " +
            "setting those it has no equivalent for, such as `takesScreenshot` and `javascriptEnabled`, which " +
            "Selenium 3 already left out of what it sent a W3C driver.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(SET_CAPABILITY), new UsesMethod<>(SET_JAVASCRIPT_ENABLED)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J j = super.visitMethodInvocation(method, ctx);
                        if (!(j instanceof J.MethodInvocation)) {
                            return j;
                        }
                        J.MethodInvocation m = (J.MethodInvocation) j;
                        boolean statement = getCursor().getParentTreeCursor().getValue() instanceof J.Block;
                        if (SET_JAVASCRIPT_ENABLED.matches(m)) {
                            //noinspection DataFlowIssue
                            return statement ? null : m;
                        }
                        if (!SET_CAPABILITY.matches(m)) {
                            return m;
                        }
                        Expression name = m.getArguments().get(0);
                        if (!(name instanceof J.Literal) || !(((J.Literal) name).getValue() instanceof String)) {
                            return m;
                        }
                        J.Literal literal = (J.Literal) name;
                        String legacyName = (String) literal.getValue();
                        if (WITHOUT_W3C_EQUIVALENT.contains(legacyName)) {
                            //noinspection DataFlowIssue
                            return statement ? null : m;
                        }
                        String w3cName = W3C_NAMES.get(legacyName);
                        if (w3cName == null) {
                            return m;
                        }
                        J.Literal renamed = literal.withValue(w3cName).withValueSource("\"" + w3cName + "\"");
                        return m.withArguments(replaceFirst(m, renamed));
                    }

                    private List<Expression> replaceFirst(J.MethodInvocation m, Expression first) {
                        List<Expression> args = new ArrayList<>(m.getArguments());
                        args.set(0, first);
                        return args;
                    }
                });
    }
}
