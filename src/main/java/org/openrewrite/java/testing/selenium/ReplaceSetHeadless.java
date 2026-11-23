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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class ReplaceSetHeadless extends Recipe {

    private static final MethodMatcher CHROME = new MethodMatcher("org.openqa.selenium.chrome.ChromeOptions setHeadless(boolean)");
    private static final MethodMatcher FIREFOX = new MethodMatcher("org.openqa.selenium.firefox.FirefoxOptions setHeadless(boolean)");

    @Getter
    final String displayName = "Replace `setHeadless` with the arguments it added";

    @Getter
    final String description = "Selenium 4 removed `ChromeOptions.setHeadless(boolean)` and " +
            "`FirefoxOptions.setHeadless(boolean)`. Add the arguments that `setHeadless(true)` added under Selenium 3, " +
            "`--headless` and `--disable-gpu` for Chrome and `-headless` for Firefox, and drop `setHeadless(false)`, " +
            "which added none. A call with a flag that is not a literal is left for a person to look at.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(CHROME), new UsesMethod<>(FIREFOX)),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J j = super.visitMethodInvocation(method, ctx);
                        if (!(j instanceof J.MethodInvocation)) {
                            return j;
                        }
                        J.MethodInvocation m = (J.MethodInvocation) j;
                        boolean chrome = CHROME.matches(m);
                        if (!chrome && !FIREFOX.matches(m) || m.getSelect() == null) {
                            return m;
                        }
                        Expression flag = m.getArguments().get(0);
                        if (!(flag instanceof J.Literal) || !(((J.Literal) flag).getValue() instanceof Boolean)) {
                            return m;
                        }
                        if (Boolean.FALSE.equals(((J.Literal) flag).getValue())) {
                            if (getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                                //noinspection DataFlowIssue
                                return null;
                            }
                            return m.getSelect().withPrefix(m.getPrefix());
                        }
                        String options = chrome ? "org.openqa.selenium.chrome.ChromeOptions" : "org.openqa.selenium.firefox.FirefoxOptions";
                        String arguments = chrome ? "\"--headless\", \"--disable-gpu\"" : "\"-headless\"";
                        return JavaTemplate.builder("#{any(" + options + ")}.addArguments(" + arguments + ")")
                                .javaParser(SeleniumDurations.parser(ctx))
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(), m.getSelect());
                    }
                });
    }
}
