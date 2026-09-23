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
package org.openrewrite.java.testing.wiremock;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class ReplaceRemovedConstructors extends Recipe {

    private static final String STUB_MAPPING = "com.github.tomakehurst.wiremock.stubbing.StubMapping";
    private static final String RESPONSE_DEFINITION = "com.github.tomakehurst.wiremock.http.ResponseDefinition";
    private static final String REQUEST_PATTERN = "com.github.tomakehurst.wiremock.matching.RequestPattern";
    private static final String WIRE_MOCK = "com.github.tomakehurst.wiremock.client.WireMock";
    private static final String HTTP_ADMIN_CLIENT = "com.github.tomakehurst.wiremock.client.HttpAdminClient";
    private static final String ADMIN = "com.github.tomakehurst.wiremock.core.Admin";
    private static final String AUTHENTICATOR = "com.github.tomakehurst.wiremock.security.ClientAuthenticator";

    /**
     * Enough of the WireMock 4 API for the templates below to parse and type check, plus the WireMock 3 types
     * that appear as arguments. The source being migrated still compiles against WireMock 3, where none of the
     * builders exist, so without these the generated calls come back without method types.
     */
    //language=java
    private static final String[] STUBS = {
            "package com.github.tomakehurst.wiremock.http;\n" +
                    "public class Request {}\n",
            "package com.github.tomakehurst.wiremock.matching;\n" +
                    "public interface ValueMatcher<T> {}\n",
            "package com.github.tomakehurst.wiremock.matching;\n" +
                    "public class CustomMatcherDefinition {}\n",
            "package com.github.tomakehurst.wiremock.security;\n" +
                    "public interface ClientAuthenticator {}\n",
            "package com.github.tomakehurst.wiremock.matching;\n" +
                    "import com.github.tomakehurst.wiremock.http.Request;\n" +
                    "public class RequestPattern {\n" +
                    "  public static final RequestPattern ANYTHING = null;\n" +
                    "  public static class Builder {\n" +
                    "    public Builder() {}\n" +
                    "    public native Builder setInlineCustomMatcher(ValueMatcher<Request> value);\n" +
                    "    public native Builder setCustomMatcherDefinition(CustomMatcherDefinition value);\n" +
                    "    public native RequestPattern build();\n" +
                    "  }\n" +
                    "}\n",
            "package com.github.tomakehurst.wiremock.http;\n" +
                    "public class ResponseDefinition {\n" +
                    "  public static class Builder {\n" +
                    "    public Builder() {}\n" +
                    "    public native Builder setStatus(int value);\n" +
                    "    public native Builder setBody(String value);\n" +
                    "    public native Builder setBody(byte[] value);\n" +
                    "    public native ResponseDefinition build();\n" +
                    "  }\n" +
                    "}\n",
            "package com.github.tomakehurst.wiremock.stubbing;\n" +
                    "import com.github.tomakehurst.wiremock.http.ResponseDefinition;\n" +
                    "import com.github.tomakehurst.wiremock.matching.RequestPattern;\n" +
                    "public class StubMapping {\n" +
                    "  public static native Builder builder();\n" +
                    "  public static class Builder {\n" +
                    "    public native Builder setRequest(RequestPattern value);\n" +
                    "    public native Builder setResponse(ResponseDefinition value);\n" +
                    "    public native StubMapping build();\n" +
                    "  }\n" +
                    "}\n",
            "package com.github.tomakehurst.wiremock.client;\n" +
                    "import com.github.tomakehurst.wiremock.security.ClientAuthenticator;\n" +
                    "public class WireMockBuilder {\n" +
                    "  public native WireMockBuilder scheme(String value);\n" +
                    "  public native WireMockBuilder host(String value);\n" +
                    "  public native WireMockBuilder port(int value);\n" +
                    "  public native WireMockBuilder urlPathPrefix(String value);\n" +
                    "  public native WireMockBuilder hostHeader(String value);\n" +
                    "  public native WireMockBuilder proxyHost(String value);\n" +
                    "  public native WireMockBuilder proxyPort(int value);\n" +
                    "  public native WireMockBuilder authenticator(ClientAuthenticator value);\n" +
                    "  public native WireMock build();\n" +
                    "}\n",
            "package com.github.tomakehurst.wiremock.client;\n" +
                    "public class WireMock {\n" +
                    "  public static native WireMockBuilder create();\n" +
                    "}\n"};

    /**
     * WireMock 4 kept only the canonical all arguments constructor on each of these types, so the convenience
     * overloads have to go through a builder instead. `WireMock(String scheme, String host)` is deliberately
     * absent: it left the port undefined, whereas `WireMockBuilder` defaults it to 8080, so rewriting it would
     * quietly change which port the client talks to.
     */
    private static final List<Replacement> REPLACEMENTS = asList(
            new Replacement(STUB_MAPPING,
                    STUB_MAPPING + " <constructor>()",
                    "StubMapping.builder().build()"),
            new Replacement(STUB_MAPPING,
                    STUB_MAPPING + " <constructor>(" + REQUEST_PATTERN + ", " + RESPONSE_DEFINITION + ")",
                    "StubMapping.builder().setRequest(#{any()}).setResponse(#{any()}).build()"),

            new Replacement(RESPONSE_DEFINITION,
                    RESPONSE_DEFINITION + " <constructor>()",
                    "new ResponseDefinition.Builder().build()"),
            new Replacement(RESPONSE_DEFINITION,
                    RESPONSE_DEFINITION + " <constructor>(int, java.lang.String)",
                    "new ResponseDefinition.Builder().setStatus(#{any()}).setBody(#{any()}).build()"),
            new Replacement(RESPONSE_DEFINITION,
                    RESPONSE_DEFINITION + " <constructor>(int, byte[])",
                    "new ResponseDefinition.Builder().setStatus(#{any()}).setBody(#{any()}).build()"),

            new Replacement(REQUEST_PATTERN,
                    REQUEST_PATTERN + " <constructor>(com.github.tomakehurst.wiremock.matching.ValueMatcher)",
                    "new RequestPattern.Builder().setInlineCustomMatcher(#{any()}).build()"),
            new Replacement(REQUEST_PATTERN,
                    REQUEST_PATTERN + " <constructor>(com.github.tomakehurst.wiremock.matching.CustomMatcherDefinition)",
                    "new RequestPattern.Builder().setCustomMatcherDefinition(#{any()}).build()"),

            new Replacement(WIRE_MOCK,
                    WIRE_MOCK + " <constructor>()",
                    "WireMock.create().build()"),
            new Replacement(WIRE_MOCK,
                    WIRE_MOCK + " <constructor>(java.lang.String, int)",
                    "WireMock.create().host(#{any()}).port(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    WIRE_MOCK + " <constructor>(java.lang.String, int, java.lang.String)",
                    "WireMock.create().host(#{any()}).port(#{any()}).urlPathPrefix(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    WIRE_MOCK + " <constructor>(java.lang.String, java.lang.String, int)",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    WIRE_MOCK + " <constructor>(java.lang.String, java.lang.String, int, java.lang.String)",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).urlPathPrefix(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    WIRE_MOCK + " <constructor>(java.lang.String, java.lang.String, int, java.lang.String, " +
                            "java.lang.String, java.lang.String, int, " +
                            AUTHENTICATOR + ")",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).urlPathPrefix(#{any()})" +
                            ".hostHeader(#{any()}).proxyHost(#{any()}).proxyPort(#{any()})" +
                            ".authenticator(#{any()}).build()"));

    private static final MethodMatcher WIRE_MOCK_FROM_ADMIN =
            new MethodMatcher(WIRE_MOCK + " <constructor>(" + ADMIN + ")");

    /**
     * `HttpAdminClient` kept only its all arguments constructor, and the `HttpClient` that constructor now
     * demands cannot be built without `wiremock-httpclient-apache5`, which `org.wiremock:wiremock` pulls in at
     * runtime scope only. Where the client is immediately wrapped in a `WireMock`, though, `WireMockBuilder`
     * does the same job through public API, so fold the pair into it. Its defaults line up with what the v3
     * convenience constructors filled in: `http` scheme, empty url path prefix, no host header, no proxy and
     * `noClientAuthenticator()`.
     */
    private static final List<Replacement> ADMIN_CLIENT_FOLDS = asList(
            new Replacement(WIRE_MOCK,
                    HTTP_ADMIN_CLIENT + " <constructor>(java.lang.String, int)",
                    "WireMock.create().host(#{any()}).port(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    HTTP_ADMIN_CLIENT + " <constructor>(java.lang.String, int, java.lang.String)",
                    "WireMock.create().host(#{any()}).port(#{any()}).urlPathPrefix(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    HTTP_ADMIN_CLIENT + " <constructor>(java.lang.String, java.lang.String, int)",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    HTTP_ADMIN_CLIENT + " <constructor>(java.lang.String, java.lang.String, int, java.lang.String)",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).urlPathPrefix(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    HTTP_ADMIN_CLIENT + " <constructor>(java.lang.String, java.lang.String, int, java.lang.String, " +
                            "java.lang.String)",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).urlPathPrefix(#{any()})" +
                            ".hostHeader(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    HTTP_ADMIN_CLIENT + " <constructor>(java.lang.String, java.lang.String, int, java.lang.String, " +
                            "java.lang.String, java.lang.String, int)",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).urlPathPrefix(#{any()})" +
                            ".hostHeader(#{any()}).proxyHost(#{any()}).proxyPort(#{any()}).build()"),
            new Replacement(WIRE_MOCK,
                    HTTP_ADMIN_CLIENT + " <constructor>(java.lang.String, java.lang.String, int, java.lang.String, " +
                            "java.lang.String, java.lang.String, int, " + AUTHENTICATOR + ")",
                    "WireMock.create().scheme(#{any()}).host(#{any()}).port(#{any()}).urlPathPrefix(#{any()})" +
                            ".hostHeader(#{any()}).proxyHost(#{any()}).proxyPort(#{any()})" +
                            ".authenticator(#{any()}).build()"));

    private static final MethodMatcher REQUEST_PATTERN_EVERYTHING =
            new MethodMatcher(REQUEST_PATTERN + " everything()");

    @Getter
    final String displayName = "Replace WireMock constructors removed in 4.x";

    @Getter
    final String description = "WireMock 4 made its primary domain classes immutable, leaving only the canonical " +
            "all arguments constructor on each and moving everything else behind a builder. Replace the removed " +
            "convenience constructors of `StubMapping`, `ResponseDefinition`, `RequestPattern` and " +
            "`WireMock` with the equivalent builder call, and `RequestPattern.everything()` with " +
            "`RequestPattern.ANYTHING`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(STUB_MAPPING, true),
                        new UsesType<>(RESPONSE_DEFINITION, true),
                        new UsesType<>(REQUEST_PATTERN, true),
                        new UsesType<>(WIRE_MOCK, true)),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = (J.NewClass) super.visitNewClass(newClass, ctx);
                        if (n.getBody() != null) {
                            // An anonymous subclass cannot become a builder call
                            return n;
                        }
                        J folded = foldAdminClient(n);
                        if (folded != null) {
                            return folded;
                        }
                        for (Replacement replacement : REPLACEMENTS) {
                            if (replacement.getMatcher().matches(n)) {
                                maybeAddImport(replacement.getOwner());
                                return replacement.getTemplate()
                                        .apply(getCursor(), n.getCoordinates().replace(),
                                                arguments(n.getArguments()));
                            }
                        }
                        return n;
                    }

                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                        if (REQUEST_PATTERN_EVERYTHING.matches(m)) {
                            maybeAddImport(REQUEST_PATTERN);
                            return JavaTemplate.builder("RequestPattern.ANYTHING")
                                    .imports(REQUEST_PATTERN)
                                    .javaParser(JavaParser.fromJavaVersion().dependsOn(STUBS))
                                    .build()
                                    .apply(getCursor(), m.getCoordinates().replace());
                        }
                        return m;
                    }

                    /**
                     * `new WireMock(new HttpAdminClient(..))` collapses to a single `WireMockBuilder` chain.
                     */
                    private @Nullable J foldAdminClient(J.NewClass wireMock) {
                        if (!WIRE_MOCK_FROM_ADMIN.matches(wireMock) || wireMock.getArguments().size() != 1 ||
                                !(wireMock.getArguments().get(0) instanceof J.NewClass)) {
                            return null;
                        }
                        J.NewClass adminClient = (J.NewClass) wireMock.getArguments().get(0);
                        if (adminClient.getBody() != null) {
                            return null;
                        }
                        for (Replacement fold : ADMIN_CLIENT_FOLDS) {
                            if (fold.getMatcher().matches(adminClient)) {
                                maybeAddImport(WIRE_MOCK);
                                maybeRemoveImport(HTTP_ADMIN_CLIENT);
                                return fold.getTemplate().apply(getCursor(), wireMock.getCoordinates().replace(),
                                        arguments(adminClient.getArguments()));
                            }
                        }
                        return null;
                    }

                    /**
                     * An argument list of a single `null` arrives as one empty-marker expression for a no-arg
                     * constructor, which carries no argument at all.
                     */
                    private Object[] arguments(List<Expression> arguments) {
                        List<Object> real = new ArrayList<>(arguments.size());
                        for (Expression argument : arguments) {
                            if (!(argument instanceof J.Empty)) {
                                real.add(argument);
                            }
                        }
                        return real.toArray();
                    }
                });
    }

    @Getter
    private static class Replacement {
        private final String owner;
        private final MethodMatcher matcher;
        private final JavaTemplate template;

        Replacement(String owner, String pattern, String code) {
            this.owner = owner;
            this.matcher = new MethodMatcher(pattern);
            this.template = JavaTemplate.builder(code)
                    .imports(owner)
                    .javaParser(JavaParser.fromJavaVersion().dependsOn(STUBS))
                    .build();
        }
    }
}
