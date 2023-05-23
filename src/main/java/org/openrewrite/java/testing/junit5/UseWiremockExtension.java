/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.function.Supplier;

import static java.util.Collections.emptyList;

public class UseWiremockExtension extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use wiremock extension";
    }

    @Override
    public String getDescription() {
        return "As of 2.31.0, wiremock [supports JUnit 5](http://wiremock.org/docs/junit-jupiter/) via an extension.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("com.github.tomakehurst.wiremock.junit.WireMockRule", false);
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        MethodMatcher newWiremockRule = new MethodMatcher("com.github.tomakehurst.wiremock.junit.WireMockRule <constructor>(..)");

        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitJavaSourceFile(JavaSourceFile cu, ExecutionContext ctx) {
                doNext(new ChangeType("com.github.tomakehurst.wiremock.junit.WireMockRule",
                        "com.github.tomakehurst.wiremock.junit5.WireMockExtension", true));
                return super.visitJavaSourceFile(cu, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (newWiremockRule.matches(n)) {
                    maybeAddImport("com.github.tomakehurst.wiremock.junit5.WireMockExtension");
                    doAfterVisit(new ChangeType("org.junit.Rule", "org.junit.jupiter.api.extension.RegisterExtension", true));
                    doNext(new UpgradeDependencyVersion("com.github.tomakehurst", "wiremock-jre8", "2.x", null, true, emptyList()));

                    Expression arg = n.getArguments().get(0);

                    Supplier<JavaParser> wiremockParser = () -> JavaParser.fromJavaVersion()
                            .dependsOn(
                                    //language=java
                                    "" +
                                    "package com.github.tomakehurst.wiremock.junit5;" +
                                    "import com.github.tomakehurst.wiremock.core.Options;" +
                                    "public class WireMockExtension {" +
                                    "  public native static Builder newInstance();" +
                                    "  public static class Builder {" +
                                    "    public native Builder options(Options options);" +
                                    "    public native Builder failOnUnmatchedRequests(boolean failOnUnmatched);" +
                                    "    public native WireMockExtension build();" +
                                    "  }" +
                                    "}",
                                    //language=java
                                    "" +
                                    "package com.github.tomakehurst.wiremock.core;" +
                                    "public class WireMockConfiguration implements Options {" +
                                    "  public static native WireMockConfiguration options();" +
                                    "  public native WireMockConfiguration port(int portNumber);" +
                                    "  public native WireMockConfiguration dynamicPort();" +
                                    "  public native WireMockConfiguration httpsPort(Integer httpsPort);" +
                                    "  public native WireMockConfiguration dynamicHttpsPort();" +
                                    "}",
                                    //language=java
                                    "" +
                                    "package com.github.tomakehurst.wiremock.core;" +
                                    "public interface Options {}")
                            .build();

                    if (arg instanceof J.Empty) {
                        String newWiremockExtension = "WireMockExtension.newInstance().build()";
                        return n.withTemplate(JavaTemplate.builder(this::getCursor, newWiremockExtension)
                                        .imports("com.github.tomakehurst.wiremock.junit5.WireMockExtension")
                                        .javaParser(wiremockParser)
                                        .build(),
                                n.getCoordinates().replace()
                        );
                    } else {
                        JavaType.Class optsType = JavaType.ShallowClass.build("com.github.tomakehurst.wiremock.core.Options");
                        if (TypeUtils.isAssignableTo(optsType, arg.getType())) {
                            String newWiremockExtension = "WireMockExtension.newInstance()" +
                                                          ".options(#{any(com.github.tomakehurst.wiremock.core.Options)})";
                            if (n.getArguments().size() > 1) {
                                newWiremockExtension += ".failOnUnmatchedRequests(#{any(boolean)})";
                                return n.withTemplate(JavaTemplate.builder(this::getCursor, newWiremockExtension + ".build()")
                                                .imports("com.github.tomakehurst.wiremock.junit5.WireMockExtension")
                                                .javaParser(wiremockParser)
                                                .build(),
                                        n.getCoordinates().replace(),
                                        arg,
                                        n.getArguments().get(1)
                                );
                            } else {
                                return n.withTemplate(JavaTemplate.builder(this::getCursor, newWiremockExtension + ".build()")
                                                .imports("com.github.tomakehurst.wiremock.junit5.WireMockExtension")
                                                .javaParser(wiremockParser)
                                                .build(),
                                        n.getCoordinates().replace(),
                                        arg
                                );
                            }
                        } else {
                            maybeAddImport("com.github.tomakehurst.wiremock.core.WireMockConfiguration");

                            String newWiremockExtension = "WireMockExtension.newInstance().options(WireMockConfiguration.options().port(#{any(int)})";
                            if (n.getArguments().size() > 1) {
                                newWiremockExtension += ".httpsPort(#{any(java.lang.Integer)})";
                                return n.withTemplate(JavaTemplate.builder(this::getCursor, newWiremockExtension + ").build()")
                                                .imports("com.github.tomakehurst.wiremock.core.WireMockConfiguration")
                                                .imports("com.github.tomakehurst.wiremock.junit5.WireMockExtension")
                                                .javaParser(wiremockParser)
                                                .build(),
                                        n.getCoordinates().replace(),
                                        arg,
                                        n.getArguments().get(1)
                                );
                            } else {
                                return n.withTemplate(JavaTemplate.builder(this::getCursor, newWiremockExtension + ").build()")
                                                .imports("com.github.tomakehurst.wiremock.core.WireMockConfiguration")
                                                .imports("com.github.tomakehurst.wiremock.junit5.WireMockExtension")
                                                .javaParser(wiremockParser)
                                                .build(),
                                        n.getCoordinates().replace(),
                                        arg
                                );
                            }
                        }
                    }
                }

                return n;
            }
        };
    }
}
