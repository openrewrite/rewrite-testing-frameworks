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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                return mi.withArguments(ListUtils.map(mi.getArguments(),
                        arg -> TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, arg.getType()) ?
                                // Wrap MockResponse.Builder arguments with .build()
                                appendBuildInvocation(ctx, arg) : arg));
            }

            private J.MethodInvocation appendBuildInvocation(ExecutionContext ctx, Expression arg) {
                boolean isChainedCall = arg instanceof J.MethodInvocation;
                String nl = isChainedCall ? "\n" : "";
                J.MethodInvocation builder = JavaTemplate
                        .builder("#{any(" + NEW_MOCKRESPONSE_FQN_BUILDER + ")}" + nl + ".build()")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockwebserver3"))
                        .imports("mockwebserver3.MockResponse", "mockwebserver3.MockResponse.Builder")
                        .build()
                        .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace(), arg);
                return patchBuilderBuildReturnTypeAndName(builder.withPrefix(arg.getPrefix()));
            }

            private J.MethodInvocation patchBuilderBuildReturnTypeAndName(J.MethodInvocation builder) {
                JavaType.Method buildMethodType = new JavaType.Method(
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
                J.MethodInvocation updated = builder.withMethodType(buildMethodType);
                return updated.withName(updated.getName().withType(updated.getMethodType())
                );
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
            String methodPattern = OLD_MOCKRESPONSE_FQN.replace("$", ".") + "#" + rep.getKey();
            recipes.add(new ChangeMethodName(methodPattern, rep.getValue(), true, false));
            recipes.add(new ChangeMethodInvocationReturnType(methodPattern, NEW_MOCKRESPONSE_FQN_BUILDER));
        }
        recipes.add(new ChangeType(OLD_MOCKRESPONSE_FQN, NEW_MOCKRESPONSE_FQN_BUILDER, true));
        recipes.add(new ChangePackage("okhttp3.mockwebserver", "mockwebserver3", false));
        return recipes;
    }
}
