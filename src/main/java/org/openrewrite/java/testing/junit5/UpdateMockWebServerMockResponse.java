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
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.tree.JavaType.ShallowClass.build;

public class UpdateMockWebServerMockResponse extends Recipe {
    private static final String OLD_MOCKRESPONSE_FQN = "okhttp3.mockwebserver.MockResponse";
    private static final String OLD_MOCKRESPONSE_CONSTRUCTOR = OLD_MOCKRESPONSE_FQN + " <constructor>()";
    private static final String OLD_MOCKRESPONSE_STATUS = OLD_MOCKRESPONSE_FQN + " status(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_SETSTATUS = OLD_MOCKRESPONSE_FQN + " setStatus(java.lang.String)";
    private static final String OLD_MOCKRESPONSE_HEADERS = OLD_MOCKRESPONSE_FQN + " headers(okhttp3.Headers)";
    private static final String OLD_MOCKRESPONSE_SETHEADERS = OLD_MOCKRESPONSE_FQN + " setHeaders(okhttp3.Headers)";
    private static final String NEW_MOCKRESPONSE_FQN = "mockwebserver3.MockResponse";
    // TODO: Rename this given this actually includes '$'
    private static final String NEW_MOCKRESPONSE_FQN_BUILDER = NEW_MOCKRESPONSE_FQN + "$Builder";
    private static final String NEW_MOCKRESPONSE_BUILDER_FQN = NEW_MOCKRESPONSE_FQN + ".Builder";
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
//                j = (J) new ChangeType(
//                        OLD_MOCKRESPONSE_FQN,
//                        NEW_MOCKRESPONSE_FQN,
//                        null
//                ).getVisitor().visit(j, ctx);
                j = new JavaIsoVisitor<ExecutionContext>() {
//                    private static final String OLD_MOCKRESPONSE_FQN = "okhttp3.mockwebserver.MockResponse";
//                    private static final String OLD_MOCKRESPONSE_CONSTRUCTOR = OLD_MOCKRESPONSE_FQN + " <constructor>()";
                    private static final String TO_ADJUST_MOCKRESPONSE_STATUS = NEW_MOCKRESPONSE_FQN + " status(java.lang.String)";
                    private static final String TO_ADJUST_MOCKRESPONSE_SETSTATUS = NEW_MOCKRESPONSE_FQN + " setStatus(java.lang.String)";
                    private static final String TO_ADJUST_MOCKRESPONSE_HEADERS = NEW_MOCKRESPONSE_FQN + " headers(okhttp3.Headers)";
                    private static final String TO_ADJUST_MOCKRESPONSE_SETHEADERS = NEW_MOCKRESPONSE_FQN + " setHeaders(okhttp3.Headers)";
//                    private static final String NEW_MOCKRESPONSE_FQN = "mockwebserver3.MockResponse";
//                    private static final String NEW_MOCKRESPONSE_BUILDER_FQN = NEW_MOCKRESPONSE_FQN + "$Builder";
//                    private static final String OLD_MOCKWEBSERVER_FQN = "okhttp3.mockwebserver.MockWebServer";
//                    private static final String NEW_MOCKWEBSERVER_FQN = "mockwebserver3.MockWebServer";
                    private final JavaType.FullyQualified newMockResponseBuilderType = (JavaType.FullyQualified) JavaType.buildType(NEW_MOCKRESPONSE_FQN_BUILDER);
                    private final MethodMatcher constructorMatcher = new MethodMatcher(OLD_MOCKRESPONSE_CONSTRUCTOR);
                    private final MethodMatcher statusMatcher = new MethodMatcher(OLD_MOCKRESPONSE_STATUS);
                    private final MethodMatcher setStatusMatcher = new MethodMatcher(OLD_MOCKRESPONSE_SETSTATUS);
                    private final MethodMatcher headersMatcher = new MethodMatcher(OLD_MOCKRESPONSE_HEADERS);
                    private final MethodMatcher setHeadersMatcher = new MethodMatcher(OLD_MOCKRESPONSE_SETHEADERS);
                    // TODO: Functionality of `ChangeMethodInvocationReturnType`, but we also change the new MockResponse() to new MockResponse.Builder() and update the types appropriately

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass nc = super.visitNewClass(newClass, ctx);
                        if (newClassesToAdjust.remove(nc.getId())) {
                            String builder = "new MockResponse.Builder()";
                            if (nc.getClazz() instanceof J.FieldAccess) {
                                builder = "new mockwebserver3.MockResponse.Builder()";
                            }
                            maybeRemoveImport(OLD_MOCKRESPONSE_FQN);
                            maybeAddImport(NEW_MOCKRESPONSE_BUILDER_FQN);
                            nc = ((J.NewClass) JavaTemplate
                                    .builder(builder)
                                    .imports(NEW_MOCKRESPONSE_BUILDER_FQN)
                                    .build()
                                    .apply(getCursor(), nc.getCoordinates().replace()))
                                    .withConstructorType(requireNonNull(nc.getConstructorType())
                                            .withDeclaringType(newMockResponseBuilderType)
                                            .withReturnType(newMockResponseBuilderType)
                                    );
                        }
//                        if (constructorMatcher.matches(nc)) {
//
//                        }
                        return nc;
                    }

                    private J.MethodInvocation updateInvocationTypeAndName(J.MethodInvocation orig, JavaType.FullyQualified newDeclaringType, JavaType newReturnType, String newName) {
                        // TODO: Revisit - Not method we can detect
                        if (orig.getMethodType() == null) {
                            return orig;
                        }
                        J.MethodInvocation updated = orig.withMethodType(
                                orig.getMethodType()
                                        .withDeclaringType(newDeclaringType)
                                        .withReturnType(newReturnType)
                                        .withName(newName)
                                );
                        return updated.withName(
                                updated.getName()
                                        .withSimpleName(newName)
                                        .withType(updated.getMethodType())
                        );
//                        return orig
//                                .withName(
//                                    orig.getName()
//                                        .withSimpleName(newName)
//                                        .withType(nameType
//                                            .withDeclaringType(newDeclaringType)
//                                            .
//                                        )
//                                )
//                        J.MethodInvocation updated = orig;
//                        if (newName != null) {
                            // Note: Also changes method type's name
//                            updated = orig.withName(orig.getName().withSimpleName(newName));
//                        }
//                        JavaType.Method nameType = (JavaType.Method) updated.getName().getType();
//                        return updated.withName(
//                                updated.getName()
//                                        .withType(nameType
//                                                .withDeclaringType(newDeclaringType)
//                                                .withReturnType(newReturnType)
//                                        )
//                        );
//                                .withMethodType(updated.getMethodType()
//                                        .withDeclaringType(newDeclaringType)
//                                        .withReturnType(newReturnType)
//                                );
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        JavaType.Method methodType = mi.getMethodType();
                        if (setStatusMatcher.matches(mi)) {
                            mi = updateInvocationTypeAndName(mi, newMockResponseBuilderType, newMockResponseBuilderType, "status");
                        } else if (setHeadersMatcher.matches(mi)) {
                            mi =  updateInvocationTypeAndName(mi, newMockResponseBuilderType, newMockResponseBuilderType, "headers");
                        } else if (
                            statusMatcher.matches(mi) ||
                            headersMatcher.matches(mi) ||
                            (
                                methodType != null &&
                                TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, methodType.getDeclaringType()) &&
                                TypeUtils.isAssignableTo(OLD_MOCKRESPONSE_FQN, methodType.getReturnType())
                            )
                        ) {
                            mi = updateInvocationTypeAndName(mi, newMockResponseBuilderType, newMockResponseBuilderType, mi.getSimpleName());
                        }
                        return mi;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                        J.VariableDeclarations.NamedVariable nv = super.visitVariable(variable, ctx);
                        if (namedVarsToAdjust.remove(nv.getId())) {
                            nv = nv
                                    .withName(nv.getName()
                                            .withType(newMockResponseBuilderType)
                                            .withFieldType(nv.getName().getFieldType()
                                                    .withType(newMockResponseBuilderType)
                                            )
                                    )
                                    .withType(newMockResponseBuilderType);
                        }
                        return nv;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
                        if (varDeclsToAdjust.remove(mv.getId())) {
                            String builder = "MockResponse.Builder";
                            JavaCoordinates coordinates;
                            if (mv.getTypeExpression() instanceof J.Identifier) {
                                coordinates = ((J.Identifier) mv.getTypeExpression()).getCoordinates().replace();
                            } else if (mv.getTypeExpression() instanceof J.FieldAccess) {
                                builder = "mockwebserver3.MockResponse.Builder";
                                coordinates = ((J.FieldAccess) mv.getTypeExpression()).getCoordinates().replace();
                            } else {
                                return mv;
                            }
                            // TODO: Figure out why new import not being added.
                            maybeRemoveImport(OLD_MOCKRESPONSE_FQN);
                            maybeAddImport(NEW_MOCKRESPONSE_BUILDER_FQN);
                            mv = ((J.VariableDeclarations) JavaTemplate
                                    .builder(builder)
                                    .imports(NEW_MOCKRESPONSE_BUILDER_FQN)
                                    .build()
                                    .apply(updateCursor(mv), coordinates))
                                    .withType(newMockResponseBuilderType);
                        }
                        return mv;
                    }
                }.visit(j, ctx);
                // TODO: Change void methods on `okhttp3.mockwebserver.MockResponse` that have side effects to instead chain, returning `okhttp3.mockwebserver`
                // TODO: Rename chained methods on `okhttp3.mockwebserver.MockResponse` to drop `set` at beginning and normalize case
                // TODO: Move instantiation of `okhttp3.mockwebserver.MockResponse` instead to instantiation of `mockwebserver3.MockResponse.Builder` followed eventually by `.build()`, ensuring correct types for chained methods

                // TODO: one pass to change the void methods and set* methods to return the new builder type,
                //  and adjust the set* methods' names. Probably need to adjust field types as well `ChangeFieldType`?
                // TODO: another pass to change the instantiation? Or same pass?
//                j = (J) new ChangeMethodInvocationReturnType(
//                        OLD_MOCKRESPONSE_STATUS,
//                        OLD_MOCKRESPONSE_FQN
//                ).getVisitor().visit(j, ctx);
//                j = (J) new ChangeMethodName(
//                        OLD_MOCKRESPONSE_SETSTATUS,
//                        "status",
//                        null,
//                        null
//                ).getVisitor().visit(j, ctx);
//                j = (J) new ChangeMethodInvocationReturnType(
//                        OLD_MOCKRESPONSE_HEADERS,
//                        OLD_MOCKRESPONSE_FQN
//                ).getVisitor().visit(j, ctx);
//                j = (J) new ChangeMethodName(
//                        OLD_MOCKRESPONSE_SETHEADERS,
//                        "headers",
//                        null,
//                        null
//                ).getVisitor().visit(j, ctx);
//                j = (J) new ChangeType(
//                        OLD_MOCKRESPONSE_FQN,
//                        NEW_MOCKRESPONSE_BUILDER_FQN,
//                        null
//                ).getVisitor().visit(j, ctx);
//                j = (J) new ChangeType(
//                        OLD_MOCKRESPONSE_FQN,
//                        NEW_MOCKRESPONSE_FQN,
//                        null
//                ).getVisitor().visit(j, ctx);
                // Get anything that was missed
                j = (J) new ChangeType(
                        OLD_MOCKRESPONSE_FQN,
                        NEW_MOCKRESPONSE_FQN,
                        null
                ).getVisitor().visit(j, ctx);
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
