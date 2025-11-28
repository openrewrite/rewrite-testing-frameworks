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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.WrappingAndBracesStyle;
import org.openrewrite.java.tree.*;
import org.openrewrite.style.LineWrapSetting;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.style.Style;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.tree.JavaType.ShallowClass.build;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String OLD_MOCKRESPONSE_FQN = "okhttp3.mockwebserver.MockResponse";
    private static final String OLD_MOCKRESPONSE_STATUS = OLD_MOCKRESPONSE_FQN + " status(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_SETSTATUS = OLD_MOCKRESPONSE_FQN + " setStatus(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_HEADERS = OLD_MOCKRESPONSE_FQN + " headers(okhttp3.Headers)";
    private static final String OLD_MOCKRESPONSE_SETHEADERS = OLD_MOCKRESPONSE_FQN + " setHeaders(okhttp3.Headers)";
    private static final String NEW_MOCKRESPONSE_FQN = "mockwebserver3.MockResponse";
    private static final String NEW_MOCKRESPONSE_BUILDER_FQN = NEW_MOCKRESPONSE_FQN + "$Builder";
    private static final String OLD_MOCKWEBSERVER_FQN = "okhttp3.mockwebserver.MockWebServer";
    private static final String NEW_MOCKWEBSERVER_FQN = "mockwebserver3.MockWebServer";

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
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                J j = (J) tree;
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
                j = (J) new ChangeMethodInvocationReturnType(
                        OLD_MOCKRESPONSE_STATUS,
                        OLD_MOCKRESPONSE_FQN
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeMethodName(
                        OLD_MOCKRESPONSE_SETSTATUS,
                        "status",
                        null,
                        null
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeMethodInvocationReturnType(
                        OLD_MOCKRESPONSE_HEADERS,
                        OLD_MOCKRESPONSE_FQN
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeMethodName(
                        OLD_MOCKRESPONSE_SETHEADERS,
                        "headers",
                        null,
                        null
                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeType(
                        OLD_MOCKRESPONSE_FQN,
                        NEW_MOCKRESPONSE_BUILDER_FQN,
                        null
                ).getVisitor().visit(j, ctx);
//                j = (J) new ChangeType(
//                        OLD_MOCKRESPONSE_FQN,
//                        NEW_MOCKRESPONSE_FQN,
//                        null
//                ).getVisitor().visit(j, ctx);
                j = (J) new ChangeType(
                        OLD_MOCKWEBSERVER_FQN,
                        NEW_MOCKWEBSERVER_FQN,
                        null
                ).getVisitor().visit(j, ctx);
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
                        List<Integer> indexes = methodInvocationsToAdjust.remove(mi.getId());
                        if (indexes != null) {
                            StringBuilder sb = new StringBuilder();
                            List<JRightPadded<Expression>> oldArgs = mi.getPadding().getArguments().getPadding().getElements();
                            for (int i = 0; i < mi.getArguments().size(); i++) {
                                sb.append("#{any()}");
                                if (indexes.contains(i)) {
                                    sb.append(".build()");
                                }
                                if (i < mi.getArguments().size() - 2) {
                                    sb.append(", ");
                                }
                            }
                            Style s1 = IntelliJ.tabsAndIndents().withContinuationIndent(4);
                            WrappingAndBracesStyle s2 = IntelliJ.wrappingAndBraces();
                            s2 = s2.withChainedMethodCalls(
                                s2.getChainedMethodCalls().withWrap(LineWrapSetting.WrapAlways)
                            );
                            AutoFormatVisitor<Object> formatVisitor = new AutoFormatVisitor<>(
                                    null,
                                    false,
                                    new NamedStyles(
                                        Tree.randomId(),
                                        "test",
                                        "test",
                                        "test",
                                        emptySet(),
                                        Arrays.asList(s1, s2)
                                    )
                            );
                            J.MethodInvocation mi3 = (J.MethodInvocation) formatVisitor.visit(
                                    JavaTemplate
                                            .builder(sb.toString())
                                            .contextSensitive()
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockwebserver3"))
                                            .imports("mockwebserver3.MockResponse", "mockwebserver3.MockResponse.Builder", "mockwebserver3.MockWebServer")
                                            .build()
                                            .apply(getCursor(), mi.getCoordinates().replaceArguments(), mi.getArguments().toArray()),
                                    ctx,
                                    getCursor().getParent()
                            );
                            // TODO: Cleanup backpatching of parameter types for method invocations
                            mi3 = mi3.withMethodType(mi.getMethodType().withParameterTypes(ListUtils.map(mi.getMethodType().getParameterTypes(), (index, x) -> {
                                if (indexes.contains(index)) {
                                    return JavaType.buildType(NEW_MOCKRESPONSE_FQN);
                                }
                                return x;
                            })));
                            mi3 = mi3.getPadding().withArguments(mi3.getPadding().getArguments().getPadding().withElements(ListUtils.map(mi3.getPadding().getArguments().getPadding().getElements(), (index, x) -> {
                                JRightPadded<Expression> oldArg = oldArgs.get(index);
                                if (indexes.contains(index)) {
                                    // TODO: Cleanup backpatching of parameter types for method invocations
                                    x = x.withElement(((J.MethodInvocation) x.getElement()).withMethodType(newMethodType(NEW_MOCKRESPONSE_BUILDER_FQN, NEW_MOCKRESPONSE_FQN, "build")));
                                }
                                // Below is backpatching prefixes and afters to restore formatting prior to chaining `.build()`
                                return x
                                        .withAfter(oldArg.getAfter())
                                        .withElement(x.getElement().withPrefix(oldArg.getElement().getPrefix()));
                            })));
                            // TODO: Cleanup backpatching of parameter types for method invocation's name's type
                            return mi3.withName(mi3.getName().withType(((JavaType.Method) mi3.getName().getType()).withParameterTypes(mi3.getPadding().getArguments().getElements().stream().map(z -> z.getType()).collect(toList()))));
                        }
                        return mi;
                    }
                }.visit(j, ctx);
            }
        });
    }

    // TODO: figure out a nicer way of doing this potentially. This is taken from MethodMatcherTest in openrewrite/rewrite and altered slightly
    private static JavaType.Method newMethodType(String declaringType, @Nullable String returnType, String method, String... parameterTypes) {
        List<JavaType> parameterTypeList = Stream.of(parameterTypes)
                .map(name -> {
                    JavaType.Primitive primitive = JavaType.Primitive.fromKeyword(name);
                    return primitive != null ? primitive : JavaType.ShallowClass.build(name);
                })
                .map(JavaType.class::cast)
                .collect(toList());

        return new JavaType.Method(
                null,
                1L,
                build(declaringType),
                method,
                returnType == null ? null : build(returnType),
                null,
                parameterTypeList,
                emptyList(),
                emptyList(),
                emptyList(),
                null
        );
    }
}
