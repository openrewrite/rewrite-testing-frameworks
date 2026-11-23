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
    private static final String FIREFOX_DRIVER = "org.openqa.selenium.firefox.FirefoxDriver";
    private static final String FIREFOX_OPTIONS = "org.openqa.selenium.firefox.FirefoxOptions";
    private static final String FIREFOX_BINARY = "org.openqa.selenium.firefox.FirefoxBinary";
    private static final String FIREFOX_PROFILE = "org.openqa.selenium.firefox.FirefoxProfile";

    private static final List<Replacement> REPLACEMENTS = asList(
            new Replacement("org.openqa.selenium.chrome.ChromeDriver", "(" + CAPABILITIES + ")",
                    "org.openqa.selenium.chrome.ChromeOptions", merge(0)),
            new Replacement("org.openqa.selenium.chrome.ChromeDriver", "(org.openqa.selenium.chrome.ChromeDriverService, " + CAPABILITIES + ")",
                    "org.openqa.selenium.chrome.ChromeOptions", merge(1)),
            new Replacement(FIREFOX_DRIVER, "(" + CAPABILITIES + ")", FIREFOX_OPTIONS, merge(0)),
            new Replacement(FIREFOX_DRIVER, "(org.openqa.selenium.firefox.GeckoDriverService, " + CAPABILITIES + ")",
                    FIREFOX_OPTIONS, merge(1)),
            new Replacement("org.openqa.selenium.ie.InternetExplorerDriver", "(" + CAPABILITIES + ")",
                    "org.openqa.selenium.ie.InternetExplorerOptions", merge(0)),
            new Replacement("org.openqa.selenium.ie.InternetExplorerDriver", "(org.openqa.selenium.ie.InternetExplorerDriverService, " + CAPABILITIES + ")",
                    "org.openqa.selenium.ie.InternetExplorerOptions", merge(1)),
            new Replacement("org.openqa.selenium.edge.EdgeDriver", "(" + CAPABILITIES + ")",
                    "org.openqa.selenium.edge.EdgeOptions", merge(0)),
            new Replacement("org.openqa.selenium.edge.EdgeDriver", "(org.openqa.selenium.edge.EdgeDriverService, " + CAPABILITIES + ")",
                    "org.openqa.selenium.edge.EdgeOptions", merge(1)),
            new Replacement("org.openqa.selenium.safari.SafariDriver", "(" + CAPABILITIES + ")",
                    "org.openqa.selenium.safari.SafariOptions", merge(0)),
            // Selenium 2, whose `FirefoxDriver` also took a binary and a profile, and required capabilities
            new Replacement(FIREFOX_DRIVER, "(" + FIREFOX_PROFILE + ")", FIREFOX_OPTIONS, setProfile(0)),
            new Replacement(FIREFOX_DRIVER, "(" + CAPABILITIES + ", " + CAPABILITIES + ")", FIREFOX_OPTIONS,
                    merge(0), merge(1)),
            new Replacement(FIREFOX_DRIVER, "(" + FIREFOX_BINARY + ", " + FIREFOX_PROFILE + ")", FIREFOX_OPTIONS,
                    setBinary(0), setProfile(1)),
            new Replacement(FIREFOX_DRIVER, "(" + FIREFOX_BINARY + ", " + FIREFOX_PROFILE + ", " + CAPABILITIES + ")",
                    FIREFOX_OPTIONS, merge(2), setBinary(0), setProfile(1)),
            new Replacement(FIREFOX_DRIVER, "(" + FIREFOX_BINARY + ", " + FIREFOX_PROFILE + ", " + CAPABILITIES + ", " +
                                            CAPABILITIES + ")",
                    FIREFOX_OPTIONS, merge(2), merge(3), setBinary(0), setProfile(1)),
            // Selenium 3.0 to 3.4
            new Replacement(FIREFOX_DRIVER, "(org.openqa.selenium.firefox.GeckoDriverService, " + CAPABILITIES + ", " +
                                            CAPABILITIES + ")",
                    FIREFOX_OPTIONS, merge(1), merge(2)));

    @Getter
    final String displayName = "Pass browser options to driver constructors that took `Capabilities`";

    @Getter
    final String description = "Selenium 4 removed the constructors of `ChromeDriver`, `FirefoxDriver`, " +
            "`InternetExplorerDriver`, `EdgeDriver` and `SafariDriver` that took `Capabilities`. Merge the " +
            "capabilities into the browser's options and pass those instead, also from the `super` call of a subclass. " +
            "The `FirefoxDriver` constructors of Selenium 2 and early Selenium 3 that took a `FirefoxBinary`, a " +
            "`FirefoxProfile` or required capabilities likewise become the one taking `FirefoxOptions`, set to the " +
            "same binary, profile and capabilities.";

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
                        return SeleniumDurations.withArguments(nc, withOptions(nc, replacement, updateCursor(nc), ctx))
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
                        return SeleniumDurations.withArguments(m, withOptions(m, replacement, updateCursor(m), ctx))
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
                        List<Expression> args = call.getArguments();
                        Expression first = args.get(replacement.getFirstIndex());
                        String options = replacement.getOptions();
                        StringBuilder template = new StringBuilder("new ")
                                .append(options.substring(options.lastIndexOf('.') + 1)).append("()");
                        List<Expression> parameters = new ArrayList<>();
                        for (Setter setter : replacement.getSetters()) {
                            Expression argument = args.get(setter.getIndex());
                            // Selenium 2 took `null` for a binary, profile or capabilities it was not given
                            if (!J.Literal.isLiteralValue(argument, null)) {
                                template.append('.').append(setter.getMethod())
                                        .append("(#{any(").append(setter.getArgumentType()).append(")})");
                                parameters.add(argument);
                            }
                        }
                        Expression merged = JavaTemplate.builder(template.toString())
                                .imports(options)
                                .javaParser(SeleniumDurations.parser(ctx))
                                .build()
                                .apply(new Cursor(cursor, first), first.getCoordinates().replace(), parameters.toArray());
                        maybeAddImport(options);
                        List<Expression> newArgs = new ArrayList<>();
                        for (int i = 0; i < args.size(); i++) {
                            if (i == replacement.getFirstIndex()) {
                                newArgs.add(merged.withPrefix(first.getPrefix()));
                            } else if (!replacement.folds(i)) {
                                newArgs.add(args.get(i));
                            }
                        }
                        return newArgs;
                    }

                    private JavaType.@Nullable Method newType(JavaType.@Nullable Method type, Replacement replacement) {
                        if (type == null) {
                            return null;
                        }
                        List<JavaType> parameterTypes = new ArrayList<>();
                        for (int i = 0; i < type.getParameterTypes().size(); i++) {
                            if (i == replacement.getFirstIndex()) {
                                parameterTypes.add(JavaType.ShallowClass.build(replacement.getOptions()));
                            } else if (!replacement.folds(i)) {
                                parameterTypes.add(type.getParameterTypes().get(i));
                            }
                        }
                        List<String> parameterNames = new ArrayList<>();
                        for (int i = 0; i < type.getParameterNames().size(); i++) {
                            if (i == replacement.getFirstIndex() || !replacement.folds(i)) {
                                parameterNames.add(type.getParameterNames().get(i));
                            }
                        }
                        return type.withParameterTypes(parameterTypes).withParameterNames(parameterNames);
                    }
                });
    }

    private static Setter merge(int index) {
        return new Setter(index, "merge", CAPABILITIES);
    }

    private static Setter setBinary(int index) {
        return new Setter(index, "setBinary", FIREFOX_BINARY);
    }

    private static Setter setProfile(int index) {
        return new Setter(index, "setProfile", FIREFOX_PROFILE);
    }

    @Value
    private static class Replacement {
        String driver;
        MethodMatcher matcher;
        String options;

        /**
         * The arguments the options take in place of the constructor, in the order the options are set to them.
         * The options take the place of the first of these arguments, and the others are dropped.
         */
        List<Setter> setters;

        Replacement(String driver, String parameters, String options, Setter... setters) {
            this.driver = driver;
            this.matcher = new MethodMatcher(driver + " <constructor>" + parameters);
            this.options = options;
            this.setters = asList(setters);
        }

        int getFirstIndex() {
            int first = Integer.MAX_VALUE;
            for (Setter setter : setters) {
                first = Math.min(first, setter.getIndex());
            }
            return first;
        }

        boolean folds(int index) {
            for (Setter setter : setters) {
                if (setter.getIndex() == index) {
                    return true;
                }
            }
            return false;
        }
    }

    @Value
    private static class Setter {
        int index;
        String method;
        String argumentType;
    }
}
