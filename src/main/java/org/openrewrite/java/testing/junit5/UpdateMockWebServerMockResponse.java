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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String LIBRARY_PACKAGE = "okhttp3.mockwebserver";
    private static final String OLD_MOCKRESPONSE_FQN = LIBRARY_PACKAGE + ".MockResponse";
    private static final String NEW_MOCKRESPONSE_FQN = "mockwebserver3.MockResponse";
    private static final String NEW_MOCKRESPONSE_FQN_BUILDER = NEW_MOCKRESPONSE_FQN + "$Builder";
    private static final JavaType.Class NEW_MOCKRESPONSE_TYPE = JavaType.ShallowClass.build(NEW_MOCKRESPONSE_FQN);

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
                JavaType.Method methodType = mi.getMethodType();
                // The library's methods take a built MockResponse in 5.x. The code's own MockResponse parameters
                // become Builders through the ChangeType below, which the pinned library parameters don't match.
                if (methodType == null || !LIBRARY_PACKAGE.equals(methodType.getDeclaringType().getPackageName())) {
                    return mi;
                }
                List<JavaType> parameterTypes = methodType.getParameterTypes();
                List<JavaType> pinnedParameterTypes = ListUtils.map(parameterTypes,
                        type -> TypeUtils.isOfClassType(type, OLD_MOCKRESPONSE_FQN) ? NEW_MOCKRESPONSE_TYPE : type);
                if (pinnedParameterTypes == parameterTypes) {
                    return mi;
                }
                JavaType.Method pinned = methodType.withParameterTypes(pinnedParameterTypes);
                return mi.withArguments(ListUtils.map(mi.getArguments(), (i, arg) ->
                                i < parameterTypes.size() &&
                                TypeUtils.isOfClassType(parameterTypes.get(i), OLD_MOCKRESPONSE_FQN) &&
                                TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, arg.getType()) ?
                                        appendBuildInvocation(ctx, arg) : arg))
                        .withMethodType(pinned)
                        .withName(mi.getName().withType(pinned));
            }

            private J.MethodInvocation appendBuildInvocation(ExecutionContext ctx, Expression arg) {
                String nl = arg instanceof J.MethodInvocation ? "\n" : "";
                J.MethodInvocation builder = JavaTemplate
                        .builder("#{any(" + NEW_MOCKRESPONSE_FQN_BUILDER + ")}" + nl + ".build()")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockwebserver3"))
                        .imports("mockwebserver3.MockResponse", "mockwebserver3.MockResponse.Builder")
                        .build()
                        .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace(), arg);
                JavaType.Method buildMethodType = new JavaType.Method(
                        null,
                        Flag.Public.getBitMask() | Flag.Final.getBitMask(),
                        JavaType.ShallowClass.build(NEW_MOCKRESPONSE_FQN_BUILDER),
                        "build",
                        NEW_MOCKRESPONSE_TYPE,
                        (List<String>) null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                return builder.withPrefix(arg.getPrefix())
                        .withMethodType(buildMethodType)
                        .withName(builder.getName().withType(buildMethodType));
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
            recipes.add(new ChangeMethodInvocationReturnType(methodPattern, NEW_MOCKRESPONSE_FQN_BUILDER.replace("$", ".")));
            recipes.add(new ChangeMethodName(methodPattern, rep.getValue(), true, false));
        }
        // 4.x's property setters return void, where the 5.x Builder methods of the same name return the Builder
        for (String setter : Arrays.asList("headers(okhttp3.Headers)", "status(java.lang.String)", "trailers(okhttp3.Headers)")) {
            recipes.add(new ChangeMethodInvocationReturnType(OLD_MOCKRESPONSE_FQN + "#" + setter, NEW_MOCKRESPONSE_FQN_BUILDER.replace("$", ".")));
        }
        recipes.add(new ChangeMethodName("okhttp3.mockwebserver.RecordedRequest getPath()", "getTarget", true, false));
        recipes.add(new ChangeMethodName("okhttp3.mockwebserver.RecordedRequest getRequestUrl()", "getUrl", true, false));
        // Pin Dispatcher.dispatch() return type before the blanket ChangeType below would misconvert it to Builder.
        recipes.add(new UpdateMockWebServerDispatcher());
        recipes.add(new ChangeType(OLD_MOCKRESPONSE_FQN, NEW_MOCKRESPONSE_FQN_BUILDER, true));
        recipes.add(new ChangePackage(LIBRARY_PACKAGE, "mockwebserver3", false));
        return recipes;
    }
}
