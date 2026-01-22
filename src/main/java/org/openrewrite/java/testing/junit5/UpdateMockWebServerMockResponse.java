/*
 * Copyright 2025 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String OLD_MOCKRESPONSE_FQN = "okhttp3.mockwebserver.MockResponse";
    private static final String NEW_MOCKRESPONSE_FQN = "mockwebserver3.MockResponse";
    private static final String NEW_MOCKRESPONSE_FQN_BUILDER = NEW_MOCKRESPONSE_FQN + "$Builder";

    private static final JavaType.FullyQualified newMockResponseBuilderType =
            (JavaType.FullyQualified) JavaType.buildType(NEW_MOCKRESPONSE_FQN_BUILDER);
    private static final JavaType.FullyQualified newMockResponseType =
            (JavaType.FullyQualified) JavaType.buildType(NEW_MOCKRESPONSE_FQN);

    @Getter
    final String displayName = "OkHttp `MockWebServer` `MockResponse` to 5.x `MockWebServer3` `MockResponse`";

    @Getter
    final String description = "Replace usages of OkHttp MockWebServer `MockResponse` with 5.x MockWebServer3 `MockResponse` and it's `Builder`.";
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(OLD_MOCKRESPONSE_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            private final Map<UUID, List<Integer>> methodInvocationsToAdjust = new HashMap<>();

            @Override
            public @Nullable J preVisit(J tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                J j = tree;
                j = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
                        List<Integer> indexes = IntStream.range(0, mi.getArguments().size())
                                .filter(x -> TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, mi.getArguments().get(x).getType()))
                                .boxed().collect(toList());
                        if (!indexes.isEmpty()) {
                            methodInvocationsToAdjust.putIfAbsent(mi.getId(), indexes);
                        }
                        return mi;
                    }

                }.visit(j, ctx);
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        List<Integer> indices = methodInvocationsToAdjust.remove(mi.getId());
                        if (indices == null || indices.isEmpty()) {
                            return mi;
                        }

                        // Wrap MockResponse.Builder arguments with .build()
                        Cursor methodCursor = getCursor();
                        return mi.withArguments(ListUtils.map(mi.getArguments(), (index, arg) -> {
                            if (indices.contains(index) && TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, arg.getType())) {
                                Cursor argCursor = new Cursor(methodCursor, arg);
                                boolean isChainedCall = arg instanceof J.MethodInvocation;
                                String nl = isChainedCall ? "\n" : "";
                                J.MethodInvocation transformed = JavaTemplate
                                        .builder("#{any(mockwebserver3.MockResponse$Builder)}" + nl + ".build()")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockwebserver3"))
                                        .imports("mockwebserver3.MockResponse", "mockwebserver3.MockResponse.Builder")
                                        .build()
                                        .apply(argCursor, arg.getCoordinates().replace(), arg);

                                if (isChainedCall) {
                                    transformed = transformed.withPrefix(arg.getPrefix());
                                }
                                return patchBuilderBuildReturnTypeAndName(transformed);
                            }
                            return arg;
                        }));
                    }

                    private J.MethodInvocation patchBuilderBuildReturnTypeAndName(J.MethodInvocation builder) {
                        JavaType.Method javaMethodType = new JavaType.Method(
                                null,
                                Flag.Public.getBitMask() | Flag.Final.getBitMask(),
                                newMockResponseBuilderType,
                                "build",
                                newMockResponseType,
                                (List<String>) null,
                                null,
                                null,
                                null,
                                null,
                                null
                        );
                        J.MethodInvocation updated = builder.withMethodType(
                                javaMethodType.withDeclaringType(newMockResponseBuilderType)
                                        .withReturnType(newMockResponseType)
                                        .withName("build")
                        );
                        return updated.withName(
                                updated.getName()
                                        .withSimpleName("build")
                                        .withType(updated.getMethodType())
                        );
                    }

                }.visit(j, ctx);
            }
        });
    }

    private static final Map<String, String> REPLACEMENTS = new HashMap<String, String>() {{
        put("setBody(*)", "body");
        put("setBodyDelay(long, java.util.concurrent.TimeUnit)", "bodyDelay");
        put("setChunkedBody(*, int)", "chunkedBody");
        put("setErrorCode(int)", "code");
        put("setHeaders(okhttp3.Headers)", "headers");
        put("setHeadersDelay(long, java.util.concurrent.TimeUnit)", "headersDelay");
        put("setHttp2ErrorCode(int)", "code");
        put("setResponseCode(int)", "code");
        put("setStatus(java.lang.String)", "status");
        put("setThrottleBody(long, long, java.util.concurrent.TimeUnit)", "throttleBody");
        put("setTrailers(okhttp3.Headers)", "trailers");
        put("withPush(okhttp3.mockwebserver.PushPromise)", "addPush");
        put("withSettings(okhttp3.internal.http2.Settings)", "settings");
        put("withWebSocketUpgrade(okhttp3.WebSocketListener)", "webSocketUpgrade");
    }};

    @Override
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        for (Map.Entry<String, String> rep : REPLACEMENTS.entrySet()) {
            recipes.add(new ChangeMethodName(
                    OLD_MOCKRESPONSE_FQN.replace("$", ".") + "#" + rep.getKey(),
                    rep.getValue(),
                    true,
                    false));
        }
        recipes.add(new ChangeType(OLD_MOCKRESPONSE_FQN, NEW_MOCKRESPONSE_FQN_BUILDER, true));
        recipes.add(new ChangePackage("okhttp3.mockwebserver", "mockwebserver3", false));
        return recipes;
    }
}
