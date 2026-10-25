/*
 * Copyright 2024 the original author or authors.
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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.List;

import static java.util.Collections.singletonList;

public class UseWiremockExtension extends Recipe {

    private static final String WIREMOCK_RULE = "com.github.tomakehurst.wiremock.junit.WireMockRule";
    private static final String WIREMOCK_EXTENSION = "com.github.tomakehurst.wiremock.junit5.WireMockExtension";
    private static final String REGISTER_EXTENSION = "org.junit.jupiter.api.extension.RegisterExtension";
    private static final MethodMatcher NEW_WIREMOCK_RULE = new MethodMatcher(WIREMOCK_RULE + " <constructor>(..)");
    private static final AnnotationMatcher RULE = new AnnotationMatcher("@org.junit.Rule");

    @Getter
    final String displayName = "Use wiremock extension";

    @Getter
    final String description = "As of 2.31.0, wiremock [supports JUnit 5](https://wiremock.org/docs/junit-jupiter/) via an extension.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new ChangeType(WIREMOCK_RULE, WIREMOCK_EXTENSION, true));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(WIREMOCK_RULE, false), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                if (multiVariable.getVariables().stream().noneMatch(v -> TypeUtils.isOfClassType(v.getType(), WIREMOCK_RULE))) {
                    return vd;
                }
                maybeRemoveImport("org.junit.Rule");
                maybeAddImport(REGISTER_EXTENSION);
                return vd.withLeadingAnnotations(ListUtils.map(vd.getLeadingAnnotations(), a -> RULE.matches(a) ?
                        a.withAnnotationType(TypeTree.<J.Identifier>build("RegisterExtension").withType(JavaType.ShallowClass.build(REGISTER_EXTENSION)).withPrefix(a.getAnnotationType().getPrefix())) :
                        a));
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                String getter = "port".equals(mi.getSimpleName()) ? "getPort" : "httpsPort".equals(mi.getSimpleName()) ? "getHttpsPort" : null;
                if (getter != null && mi.getSelect() != null && mi.getMethodType() != null && TypeUtils.isOfClassType(mi.getSelect().getType(), WIREMOCK_RULE)) {
                    return mi.withName(mi.getName().withSimpleName(getter))
                            .withMethodType(mi.getMethodType().withName(getter).withDeclaringType(JavaType.ShallowClass.build(WIREMOCK_EXTENSION)));
                }
                return mi;
            }

            @SuppressWarnings("ConcatenationWithEmptyString")
            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (NEW_WIREMOCK_RULE.matches(n)) {
                    maybeAddImport(WIREMOCK_EXTENSION);

                    Expression arg = n.getArguments().get(0);

                    JavaParser.Builder<?, ?> wiremockParser = JavaParser.fromJavaVersion()
                            .dependsOn(
                                    //language=java
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
                                    "package com.github.tomakehurst.wiremock.core;" +
                                            "public class WireMockConfiguration implements Options {" +
                                            "  public static native WireMockConfiguration options();" +
                                            "  public native WireMockConfiguration port(int portNumber);" +
                                            "  public native WireMockConfiguration dynamicPort();" +
                                            "  public native WireMockConfiguration httpsPort(Integer httpsPort);" +
                                            "  public native WireMockConfiguration dynamicHttpsPort();" +
                                            "}",
                                    //language=java
                                    "package com.github.tomakehurst.wiremock.core;" +
                                            "public interface Options {}");

                    if (arg instanceof J.Empty) {
                        String newWiremockExtension = "WireMockExtension.newInstance().build()";
                        return JavaTemplate.builder(newWiremockExtension)
                                .imports(WIREMOCK_EXTENSION)
                                .javaParser(wiremockParser)
                                .build()
                                .apply(getCursor(), n.getCoordinates().replace());
                    }
                    JavaType.Class optsType = JavaType.ShallowClass.build("com.github.tomakehurst.wiremock.core.Options");
                    if (TypeUtils.isAssignableTo(optsType, arg.getType())) {
                        String newWiremockExtension = "WireMockExtension.newInstance()" +
                                ".options(#{any(com.github.tomakehurst.wiremock.core.Options)})";
                        if (n.getArguments().size() > 1) {
                            newWiremockExtension += ".failOnUnmatchedRequests(#{any(boolean)})";
                            return JavaTemplate.builder(newWiremockExtension + ".build()")
                                    .imports(WIREMOCK_EXTENSION)
                                    .javaParser(wiremockParser)
                                    .build()
                                    .apply(
                                            updateCursor(n),
                                            n.getCoordinates().replace(),
                                            arg,
                                            n.getArguments().get(1)
                                    );
                        }
                        return JavaTemplate.builder(newWiremockExtension + ".build()")
                                .imports(WIREMOCK_EXTENSION)
                                .javaParser(wiremockParser)
                                .build()
                                .apply(updateCursor(n), n.getCoordinates().replace(), arg);
                    }
                    maybeAddImport("com.github.tomakehurst.wiremock.core.WireMockConfiguration");

                    String newWiremockExtension = "WireMockExtension.newInstance().options(WireMockConfiguration.options().port(#{any(int)})";
                    if (n.getArguments().size() > 1) {
                        newWiremockExtension += ".httpsPort(#{any(java.lang.Integer)})";
                        return JavaTemplate.builder(newWiremockExtension + ").build()")
                                .imports("com.github.tomakehurst.wiremock.core.WireMockConfiguration")
                                .imports(WIREMOCK_EXTENSION)
                                .javaParser(wiremockParser)
                                .build()
                                .apply(
                                        updateCursor(n),
                                        n.getCoordinates().replace(),
                                        arg,
                                        n.getArguments().get(1)
                                );
                    }
                    return JavaTemplate.builder(newWiremockExtension + ").build()")
                            .imports("com.github.tomakehurst.wiremock.core.WireMockConfiguration")
                            .imports(WIREMOCK_EXTENSION)
                            .javaParser(wiremockParser)
                            .build()
                            .apply(updateCursor(n), n.getCoordinates().replace(), arg);
                }

                return n;
            }
        });
    }
}
