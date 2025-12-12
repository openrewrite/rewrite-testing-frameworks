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
import org.openrewrite.marker.Markers;
import org.openrewrite.style.LineWrapSetting;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.style.Style;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.JavaType.ShallowClass.build;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String OLD_PACKAGE_NAME = "okhttp3.mockwebserver";
    private static final String NEW_PACKAGE_NAME = "mockwebserver3";
    private static final String OLD_MOCKRESPONSE_FQN = OLD_PACKAGE_NAME + ".MockResponse";
    private static final String OLD_MOCKRESPONSE_CONSTRUCTOR = OLD_MOCKRESPONSE_FQN + " <constructor>()";
    private static final String OLD_MOCKRESPONSE_STATUS = OLD_MOCKRESPONSE_FQN + " status(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_SETSTATUS = OLD_MOCKRESPONSE_FQN + " setStatus(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_HEADERS = OLD_MOCKRESPONSE_FQN + " headers(okhttp3.Headers)";
    private static final String OLD_MOCKRESPONSE_SETHEADERS = OLD_MOCKRESPONSE_FQN + " setHeaders(okhttp3.Headers)";
    private static final String NEW_MOCKRESPONSE_FQN = NEW_PACKAGE_NAME + ".MockResponse";
    // TODO: Rename this given this actually includes '$'
    private static final String NEW_MOCKRESPONSE_FQN_BUILDER = NEW_MOCKRESPONSE_FQN + "$Builder";
    private static final String NEW_MOCKRESPONSE_BUILDER_FQN = NEW_MOCKRESPONSE_FQN + ".Builder";
    private static final String OLD_MOCKWEBSERVER_FQN = OLD_PACKAGE_NAME + ".MockWebServer";
    private static final String NEW_MOCKWEBSERVER_FQN = NEW_PACKAGE_NAME + ".MockWebServer";

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
                j = new JavaIsoVisitor<ExecutionContext>() {
                    private final JavaType.FullyQualified newMockResponseBuilderType = (JavaType.FullyQualified) JavaType.buildType(NEW_MOCKRESPONSE_FQN_BUILDER);
                    private final JavaType.FullyQualified newMockResponseType = (JavaType.FullyQualified) JavaType.buildType(NEW_MOCKRESPONSE_FQN);
                    private final MethodMatcher TO_ADJUST_MOCKRESPONSE_BUILDER_STATUS_MATCHER = new MethodMatcher(NEW_MOCKRESPONSE_FQN_BUILDER + " status(java.lang.String)");
                    private final MethodMatcher TO_ADJUST_MOCKRESPONSE_BUILDER_SETSTATUS_MATCHER = new MethodMatcher(NEW_MOCKRESPONSE_FQN_BUILDER + " setStatus(java.lang.String)");
                    private final MethodMatcher TO_ADJUST_MOCKRESPONSE_BUILDER_HEADERS_MATCHER = new MethodMatcher(NEW_MOCKRESPONSE_FQN_BUILDER + " headers(okhttp3.Headers)");
                    private final MethodMatcher TO_ADJUST_MOCKRESPONSE_BUILDER_SETHEADERS_MATCHER = new MethodMatcher(NEW_MOCKRESPONSE_FQN_BUILDER + " setHeaders(okhttp3.Headers)");

                    private boolean returnsVoid(J.MethodInvocation method) {
                        return method.getMethodType() != null && TypeUtils.isAssignableTo(JavaType.Primitive.Void, method.getMethodType().getReturnType());
                    }

                    private J.MethodInvocation patchReturnTypeAndName(J.MethodInvocation method, JavaType.FullyQualified newDeclaringType, JavaType newReturnType, String newName) {
                        assert method.getMethodType() != null;
                        J.MethodInvocation updated = method.withMethodType(
                                method.getMethodType()
                                        .withDeclaringType(newDeclaringType)
                                        .withReturnType(newReturnType)
                                        .withName(newName)
                        );
                        return updated.withName(
                                updated.getName()
                                        .withSimpleName(newName)
                                        .withType(updated.getMethodType())
                        );
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi =  super.visitMethodInvocation(method, ctx);
                        BiFunction<J.MethodInvocation, String, J.MethodInvocation> patchFunc = (J.MethodInvocation methodInv, String newName) -> patchReturnTypeAndName(methodInv, newMockResponseBuilderType, newMockResponseBuilderType, newName);
                        if (returnsVoid(mi)) {
                            if (
                                    TO_ADJUST_MOCKRESPONSE_BUILDER_STATUS_MATCHER.matches(mi) ||
                                    TO_ADJUST_MOCKRESPONSE_BUILDER_HEADERS_MATCHER.matches(mi)
                            ) {
                                return patchFunc.apply(mi, mi.getSimpleName());
                            }
                        } else if (TO_ADJUST_MOCKRESPONSE_BUILDER_SETSTATUS_MATCHER.matches(mi)) {
                            return patchFunc.apply(mi,"status");
                        } else if (TO_ADJUST_MOCKRESPONSE_BUILDER_SETHEADERS_MATCHER.matches(mi)) {
                            return patchFunc.apply(mi, "headers");
                        }
                        List<Integer> indexes = methodInvocationsToAdjust.remove(mi.getId());
                        if (indexes != null && !indexes.isEmpty()) {
                            List<JRightPadded<Expression>> oldPaddedArgs = mi.getPadding().getArguments().getPadding().getElements();
                            assert mi.getMethodType() != null;
                            mi = mi.withArguments(ListUtils.map(mi.getArguments(), (index, expr) -> {
                                if (indexes.contains(index)) {
                                    return patchReturnTypeAndName(new J.MethodInvocation(
                                            randomId(),
                                            Space.EMPTY,
                                            Markers.EMPTY,
                                            JRightPadded.build(expr),
                                            null,
                                            new J.Identifier(
                                                    randomId(),
                                                    Space.EMPTY,
                                                    Markers.EMPTY,
                                                    emptyList(),
                                                    "build",
                                                    null,
                                                    null
                                            ),
                                            JContainer.empty(),
                                            new JavaType.Method(
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
                                            )
                                    ), newMockResponseBuilderType, newMockResponseType, "build");
                                }
                                return expr;
                            }));
                            assert mi.getMethodType() != null;
                            mi = mi.withMethodType(mi.getMethodType().withParameterTypes(ListUtils.map(mi.getMethodType().getParameterTypes(), (index, type) -> {
                                if (indexes.contains(index)) {
                                    return newMockResponseType;
                                }
                                return type;
                            })));
                        }
                        return mi;
                    }
                }.visit(j, ctx);
                // TODO: harvest padding logic from below and then get rid of rest
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInv, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(methodInv, ctx);
                        List<Integer> indexes = methodInvocationsToAdjust.remove(mi.getId());
                        if (indexes != null) {
                            StringBuilder sb = new StringBuilder();
                            List<JRightPadded<Expression>> oldArgs = mi.getPadding().getArguments().getPadding().getElements();
                            for (int i = 0; i < mi.getArguments().size(); i++) {
//                                sb.append("#{any()}");
                                if (indexes.contains(i)) {
                                    sb.append("#{any(mockwebserver3.MockResponse$Builder)}.build()");
                                } else {
                                    sb.append("#{any()}");
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
                                        randomId(),
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
                            mi3 = mi3.withName(mi3.getName().withType(mi3.getMethodType()));
                            mi3 = mi3.getPadding().withArguments(mi3.getPadding().getArguments().getPadding().withElements(ListUtils.map(mi3.getPadding().getArguments().getPadding().getElements(), (index, x) -> {
                                JRightPadded<Expression> oldArg = oldArgs.get(index);
                                if (indexes.contains(index)) {
                                    // TODO: Cleanup backpatching of parameter types for method invocations
                                    x = x.withElement(((J.MethodInvocation) x.getElement()).withMethodType(newMethodType(NEW_MOCKRESPONSE_FQN_BUILDER, NEW_MOCKRESPONSE_FQN, "build")));
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
                return j;
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
