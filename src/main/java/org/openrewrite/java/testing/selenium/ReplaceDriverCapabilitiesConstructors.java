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
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class ReplaceDriverCapabilitiesConstructors extends Recipe {

    private static final String CAPABILITIES = "org.openqa.selenium.Capabilities";

    private static final List<Replacement> REPLACEMENTS = asList(
            new Replacement("org.openqa.selenium.chrome.ChromeDriver", "(" + CAPABILITIES + ")",
                    0, "org.openqa.selenium.chrome.ChromeOptions"),
            new Replacement("org.openqa.selenium.chrome.ChromeDriver", "(org.openqa.selenium.chrome.ChromeDriverService, " + CAPABILITIES + ")",
                    1, "org.openqa.selenium.chrome.ChromeOptions"),
            new Replacement("org.openqa.selenium.firefox.FirefoxDriver", "(" + CAPABILITIES + ")",
                    0, "org.openqa.selenium.firefox.FirefoxOptions"),
            new Replacement("org.openqa.selenium.firefox.FirefoxDriver", "(org.openqa.selenium.firefox.GeckoDriverService, " + CAPABILITIES + ")",
                    1, "org.openqa.selenium.firefox.FirefoxOptions"),
            new Replacement("org.openqa.selenium.ie.InternetExplorerDriver", "(" + CAPABILITIES + ")",
                    0, "org.openqa.selenium.ie.InternetExplorerOptions"),
            new Replacement("org.openqa.selenium.ie.InternetExplorerDriver", "(org.openqa.selenium.ie.InternetExplorerDriverService, " + CAPABILITIES + ")",
                    1, "org.openqa.selenium.ie.InternetExplorerOptions"),
            new Replacement("org.openqa.selenium.edge.EdgeDriver", "(" + CAPABILITIES + ")",
                    0, "org.openqa.selenium.edge.EdgeOptions"),
            new Replacement("org.openqa.selenium.edge.EdgeDriver", "(org.openqa.selenium.edge.EdgeDriverService, " + CAPABILITIES + ")",
                    1, "org.openqa.selenium.edge.EdgeOptions"),
            new Replacement("org.openqa.selenium.safari.SafariDriver", "(" + CAPABILITIES + ")",
                    0, "org.openqa.selenium.safari.SafariOptions"),
            // Selenium 2
            new Replacement("org.openqa.selenium.firefox.FirefoxDriver", "(org.openqa.selenium.firefox.FirefoxProfile)",
                    0, "org.openqa.selenium.firefox.FirefoxOptions", "setProfile", "org.openqa.selenium.firefox.FirefoxProfile"));

    @Getter
    final String displayName = "Pass browser options to driver constructors that took `Capabilities`";

    @Getter
    final String description = "Selenium 4 removed the constructors of `ChromeDriver`, `FirefoxDriver`, " +
            "`InternetExplorerDriver`, `EdgeDriver` and `SafariDriver` that took `Capabilities`. Merge the " +
            "capabilities into the browser's options and pass those instead, also from the `super` call of a subclass. " +
            "The Selenium 2 `FirefoxDriver(FirefoxProfile)` constructor likewise becomes one taking `FirefoxOptions` " +
            "with that profile.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // An anonymous subclass calls a constructor of its own, which `UsesMethod` does not match
        return Preconditions.check(Preconditions.or(REPLACEMENTS.stream()
                        .map(Replacement::getDriver)
                        .distinct()
                        .map(driver -> new UsesType<>(driver, false))
                        .toArray(TreeVisitor[]::new)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        Replacement replacement = replacement(SeleniumDurations.constructorType(nc));
                        if (replacement == null) {
                            return nc;
                        }
                        return nc.withArguments(withOptions(nc, replacement, updateCursor(nc), ctx))
                                .withConstructorType(newType(nc.getConstructorType(), replacement));
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        // `super(capabilities)` in a subclass
                        Replacement replacement = replacement(m.getMethodType());
                        if (replacement == null) {
                            return m;
                        }
                        JavaType.Method type = newType(m.getMethodType(), replacement);
                        return m.withArguments(withOptions(m, replacement, updateCursor(m), ctx))
                                .withName(m.getName().withType(type))
                                .withMethodType(type);
                    }

                    private @Nullable Replacement replacement(JavaType.@Nullable Method type) {
                        for (Replacement replacement : REPLACEMENTS) {
                            if (replacement.getMatcher().matches(type)) {
                                return replacement;
                            }
                        }
                        return null;
                    }

                    private List<Expression> withOptions(MethodCall call, Replacement replacement, Cursor cursor, ExecutionContext ctx) {
                        List<Expression> args = new ArrayList<>(call.getArguments());
                        Expression capabilities = args.get(replacement.getCapabilitiesIndex());
                        String options = replacement.getOptions();
                        Expression merged = JavaTemplate.builder("new " + options.substring(options.lastIndexOf('.') + 1) +
                                                                 "()." + replacement.getMethod() + "(#{any(" + replacement.getArgumentType() + ")})")
                                .imports(options)
                                .javaParser(SeleniumDurations.parser(ctx))
                                .build()
                                .apply(new Cursor(cursor, capabilities), capabilities.getCoordinates().replace(), capabilities);
                        maybeAddImport(options);
                        args.set(replacement.getCapabilitiesIndex(), merged.withPrefix(capabilities.getPrefix()));
                        return args;
                    }

                    private JavaType.@Nullable Method newType(JavaType.@Nullable Method type, Replacement replacement) {
                        if (type == null) {
                            return null;
                        }
                        List<JavaType> parameterTypes = new ArrayList<>(type.getParameterTypes());
                        parameterTypes.set(replacement.getCapabilitiesIndex(), JavaType.ShallowClass.build(replacement.getOptions()));
                        return type.withParameterTypes(parameterTypes);
                    }
                });
    }

    @Value
    private static class Replacement {
        String driver;
        MethodMatcher matcher;
        int capabilitiesIndex;
        String options;
        String method;
        String argumentType;

        Replacement(String driver, String parameters, int capabilitiesIndex, String options) {
            this(driver, parameters, capabilitiesIndex, options, "merge", CAPABILITIES);
        }

        Replacement(String driver, String parameters, int capabilitiesIndex, String options, String method, String argumentType) {
            this.driver = driver;
            this.matcher = new MethodMatcher(driver + " <constructor>" + parameters);
            this.capabilitiesIndex = capabilitiesIndex;
            this.options = options;
            this.method = method;
            this.argumentType = argumentType;
        }
    }
}
