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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String OLD_PACKAGE_NAME = "okhttp3.mockwebserver";
    private static final String NEW_PACKAGE_NAME = "mockwebserver3";
    private static final String OLD_MOCKRESPONSE_FQN = OLD_PACKAGE_NAME + ".MockResponse";
    private static final String OLD_MOCKRESPONSE_CONSTRUCTOR = OLD_MOCKRESPONSE_FQN + " <constructor>()";
    private static final String NEW_MOCKRESPONSE_FQN = NEW_PACKAGE_NAME + ".MockResponse";
    private static final String NEW_MOCKRESPONSE_FQN_BUILDER = NEW_MOCKRESPONSE_FQN + "$Builder";

    private static final JavaType.FullyQualified newMockResponseBuilderType = (JavaType.FullyQualified) JavaType.buildType(NEW_MOCKRESPONSE_FQN_BUILDER);

    private static class MethodInvocationReplacement {
        private final MethodMatcher methodMatcher;
        private final String newName;
        private MethodInvocationReplacement(String oldMethodPattern, String newName) {
            this.methodMatcher = new MethodMatcher(NEW_MOCKRESPONSE_FQN_BUILDER + "#" + oldMethodPattern);
            this.newName = newName;
        }

        private boolean matches(J.MethodInvocation methodInvocation) {
            return methodMatcher.matches(methodInvocation);
        }

        private J.MethodInvocation patchReturnTypeAndName(J.MethodInvocation method) {
            assert method.getMethodType() != null;
            J.MethodInvocation updated = method.withMethodType(
                    method.getMethodType()
                            .withDeclaringType(UpdateMockWebServerMockResponse.newMockResponseBuilderType)
                            .withReturnType(UpdateMockWebServerMockResponse.newMockResponseBuilderType)
                            .withName(newName)
            );
            return updated.withName(
                    updated.getName()
                            .withSimpleName(newName)
                            .withType(updated.getMethodType())
            );
        }
    }

    private static final List<MethodInvocationReplacement> methodInvocationReplacements = Arrays.asList(
            new MethodInvocationReplacement("addHeader(java.lang.String)", "addHeader"),
            new MethodInvocationReplacement("addHeader(java.lang.String, ..)", "addHeader"),
            new MethodInvocationReplacement("addHeaderLenient(java.lang.String, ..)", "addHeaderLenient"),
            new MethodInvocationReplacement("setBody(okio.Buffer)", "body"),
            new MethodInvocationReplacement("setBody(java.lang.String)", "body"),
            new MethodInvocationReplacement("setBodyDelay(java.lang.Long, java.util.concurrent.TimeUnit)", "bodyDelay"),
            new MethodInvocationReplacement("setChunkedBody(java.lang.String, java.lang.Integer)", "chunkedBody"),
            new MethodInvocationReplacement("setChunkedBody(okio.Buffer, java.lang.Integer)", "chunkedBody"),
            new MethodInvocationReplacement("setErrorCode(java.lang.Integer)", "code"),
            new MethodInvocationReplacement("setHeader(java.lang.String, ..)", "header"),
            new MethodInvocationReplacement("setHeaders(okhttp3.Headers)", "headers"),
            new MethodInvocationReplacement("setHeadersDelay(java.lang.Long, java.util.concurrent.TimeUnit)", "headersDelay"),
            new MethodInvocationReplacement("setResponseCode(java.lang.Integer)", "code"),
            new MethodInvocationReplacement("setStatus(java.lang.String)", "status"),
            new MethodInvocationReplacement("setThrottleBody(java.lang.Long, java.lang.Long, java.util.concurrent.TimeUnit)", "throttleBody"),
            new MethodInvocationReplacement("setTrailers(okhttp3.Headers)", "trailers")
    );

    @Override
    public String getDisplayName() {
        return "OkHttp `MockWebServer` `MockResponse` to 5.x `MockWebServer3` `MockResponse`";
    }

    @Override
    public String getDescription() {
        return "Replace usages of OkHttp MockWebServer `MockResponse` with 5.x MockWebServer3 `MockResponse` and it's `Builder`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(OLD_MOCKRESPONSE_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            private final Map<UUID, List<Integer>> methodInvocationsToAdjust = new HashMap<>();
            private final Set<UUID> newClassesToAdjust = new HashSet<>();
            private final Set<UUID> varDeclsToAdjust = new HashSet<>();
            private final Set<UUID> namedVarsToAdjust = new HashSet<>();
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                J j = (J) tree;
                j = new JavaIsoVisitor<ExecutionContext>() {
                    private final MethodMatcher constructorMatcher = new MethodMatcher(OLD_MOCKRESPONSE_CONSTRUCTOR);

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        if (constructorMatcher.matches(nc)) {
                            newClassesToAdjust.add(nc.getId());
                        }
                        return nc;
                    }

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

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                        J.VariableDeclarations.NamedVariable nv = super.visitVariable(variable, ctx);
                        if (TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, nv.getType())) {
                            namedVarsToAdjust.add(nv.getId());
                        }
                        return nv;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                        if (TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, mv.getType())) {
                            varDeclsToAdjust.add(mv.getId());
                        }
                        return mv;
                    }
                }.visit(j, ctx);
                j = (J) new ChangeType(
                        OLD_MOCKRESPONSE_FQN,
                        NEW_MOCKRESPONSE_FQN_BUILDER,
                        true
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangePackage(
                        OLD_PACKAGE_NAME,
                        NEW_PACKAGE_NAME,
                        false
                ).getVisitor().visit(j, ctx);
                return new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        final J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        // Handle method name replacements
                        J.MethodInvocation replacement = methodInvocationReplacements.stream()
                                .filter(invocationReplacement -> invocationReplacement.matches(mi))
                                .findFirst()
                                .map(invocationReplacement -> invocationReplacement.patchReturnTypeAndName(mi))
                                .orElse(mi);

                        if (replacement != mi) {
                            return replacement;
                        }

                        List<Integer> indices = methodInvocationsToAdjust.remove(mi.getId());
                        if (indices == null || indices.isEmpty()) {
                            return mi;
                        }

                        // Wrap MockResponse.Builder arguments with .build()
                        Cursor methodCursor = getCursor();
                        return mi.withArguments(ListUtils.map(mi.getArguments(), (index, arg) -> {
                            if (indices.contains(index) && TypeUtils.isAssignableTo(NEW_MOCKRESPONSE_FQN_BUILDER, arg.getType())) {
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
                                return transformed;
                            }
                            return arg;
                        }));
                    }
                }.visit(j, ctx);
            }
        });
    }
}
