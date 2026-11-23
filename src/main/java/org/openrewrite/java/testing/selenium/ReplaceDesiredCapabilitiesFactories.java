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
import org.openrewrite.java.tree.J;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReplaceDesiredCapabilitiesFactories extends Recipe {

    private static final String DESIRED_CAPABILITIES = "org.openqa.selenium.remote.DesiredCapabilities";

    /**
     * The browsers Selenium 4 has options for, by the factory that named them.
     */
    private static final Map<String, String> OPTIONS = new LinkedHashMap<>();

    /**
     * The browsers it has none for, with the browser name and platform the factory gave them.
     */
    private static final Map<String, String[]> NO_OPTIONS = new LinkedHashMap<>();

    static {
        OPTIONS.put("chrome", "org.openqa.selenium.chrome.ChromeOptions");
        OPTIONS.put("firefox", "org.openqa.selenium.firefox.FirefoxOptions");
        OPTIONS.put("internetExplorer", "org.openqa.selenium.ie.InternetExplorerOptions");
        OPTIONS.put("edge", "org.openqa.selenium.edge.EdgeOptions");
        OPTIONS.put("safari", "org.openqa.selenium.safari.SafariOptions");

        NO_OPTIONS.put("htmlUnit", new String[]{"htmlunit", "ANY"});
        NO_OPTIONS.put("phantomjs", new String[]{"phantomjs", "ANY"});
        NO_OPTIONS.put("opera", new String[]{"opera", "ANY"});
        NO_OPTIONS.put("operaBlink", new String[]{"operablink", "ANY"});
        NO_OPTIONS.put("android", new String[]{"android", "ANDROID"});
        NO_OPTIONS.put("iphone", new String[]{"iPhone", "MAC"});
        NO_OPTIONS.put("ipad", new String[]{"iPad", "MAC"});
    }

    private static final MethodMatcher FACTORY = new MethodMatcher(DESIRED_CAPABILITIES + " *()");

    @Getter
    final String displayName = "Replace the browser factories of `DesiredCapabilities`";

    @Getter
    final String description = "Selenium 4 removed static factories such as `DesiredCapabilities.chrome()`. " +
            "Where Selenium 4 has options for the browser, start from those, which name the browser the same way " +
            "and are all that a W3C driver read of the factory's capabilities. For the other browsers, construct " +
            "the `DesiredCapabilities` the factory did.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(FACTORY), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (!(j instanceof J.MethodInvocation)) {
                    return j;
                }
                J.MethodInvocation m = (J.MethodInvocation) j;
                if (!FACTORY.matches(m) || m.getMethodType() == null || !m.getMethodType().hasFlags(org.openrewrite.java.tree.Flag.Static)) {
                    return m;
                }
                String factory = m.getSimpleName();
                maybeRemoveImport(DESIRED_CAPABILITIES + "." + factory);
                maybeAddImport(DESIRED_CAPABILITIES);
                if (OPTIONS.containsKey(factory)) {
                    String options = OPTIONS.get(factory);
                    maybeAddImport(options);
                    return JavaTemplate.builder("new DesiredCapabilities(new " + options.substring(options.lastIndexOf('.') + 1) + "())")
                            .imports(DESIRED_CAPABILITIES, options)
                            .javaParser(SeleniumDurations.parser(ctx))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace());
                }
                if (NO_OPTIONS.containsKey(factory)) {
                    String[] browser = NO_OPTIONS.get(factory);
                    maybeAddImport("org.openqa.selenium.Platform");
                    return JavaTemplate.builder("new DesiredCapabilities(\"" + browser[0] + "\", \"\", Platform." + browser[1] + ")")
                            .imports(DESIRED_CAPABILITIES, "org.openqa.selenium.Platform")
                            .javaParser(SeleniumDurations.parser(ctx))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace());
                }
                return m;
            }
        });
    }
}
