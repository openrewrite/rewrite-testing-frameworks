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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.*;

public class WireMockRuleToWireMockServer extends ScanningRecipe<Set<String>> {

    private static final String WIREMOCK_RULE = "com.github.tomakehurst.wiremock.junit.WireMockRule";
    private static final String WIREMOCK_SERVER = "com.github.tomakehurst.wiremock.WireMockServer";
    private static final MethodMatcher NEW_WIREMOCK_RULE = new MethodMatcher(WIREMOCK_RULE + " <constructor>(..)");
    private static final AnnotationMatcher RULE = new AnnotationMatcher("@org.junit.*Rule");
    private static final AnnotationMatcher CLASS_RULE = new AnnotationMatcher("@org.junit.ClassRule");
    private static final Collection<String> SERVER_ONLY_METHODS = Arrays.asList("addMockServiceRequestListener", "checkForUnmatchedRequests", "enableRecordMappings", "getGlobalSettingsHolder", "isHttpEnabled", "isHttpsEnabled", "isRunning", "loadMappingsUsing", "shutdown", "start", "stop");
    private static final Collection<String> LIFECYCLE_ANNOTATIONS = Arrays.asList("org.junit.Before", "org.junit.After", "org.junit.BeforeClass", "org.junit.AfterClass", "org.junit.jupiter.api.BeforeEach", "org.junit.jupiter.api.AfterEach", "org.junit.jupiter.api.BeforeAll", "org.junit.jupiter.api.AfterAll");

    @Getter
    final String displayName = "Use `WireMockServer` for a `WireMockRule` used as a server";

    @Getter
    final String description = "Replace a `WireMockRule` that is started, stopped or passed on as a `WireMockServer` with a plain `WireMockServer`, as the JUnit 5 `WireMockExtension` has no such methods. The server is started and stopped around each test, or around all tests for a `@ClassRule`.";

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> serverVariables) {
        return Preconditions.check(new UsesType<>(WIREMOCK_RULE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                JavaType.Variable select = wireMockRuleVariable(method.getSelect());
                if (select != null && SERVER_ONLY_METHODS.contains(method.getSimpleName())) {
                    serverVariables.add(key(select));
                }
                addArgumentsExpectingServer(method.getMethodType(), method.getArguments());
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                JavaType.Variable containing = wireMockRuleVariable(memberRef.getContaining());
                if (containing != null && SERVER_ONLY_METHODS.contains(memberRef.getReference().getSimpleName())) {
                    serverVariables.add(key(containing));
                }
                return super.visitMemberReference(memberRef, ctx);
            }

            @Override
            public J.Return visitReturn(J.Return aReturn, ExecutionContext ctx) {
                if (wireMockRuleVariable(aReturn.getExpression()) != null) {
                    Object enclosing = getCursor().dropParentUntil(p -> p instanceof J.MethodDeclaration || p instanceof J.Lambda || p == Cursor.ROOT_VALUE).getValue();
                    JavaType.Method methodType = enclosing instanceof J.MethodDeclaration ? ((J.MethodDeclaration) enclosing).getMethodType() : null;
                    addIfExpectingServer(methodType == null ? null : methodType.getReturnType(), aReturn.getExpression());
                }
                return super.visitReturn(aReturn, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                addArgumentsExpectingServer(newClass.getConstructorType(), newClass.getArguments());
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                addIfExpectingServer(variable.getType(), variable.getInitializer());
                return super.visitVariable(variable, ctx);
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                addIfExpectingServer(assignment.getVariable().getType(), assignment.getAssignment());
                return super.visitAssignment(assignment, ctx);
            }

            private void addArgumentsExpectingServer(JavaType.@Nullable Method methodType, List<Expression> arguments) {
                if (methodType != null && !methodType.getParameterTypes().isEmpty()) {
                    List<JavaType> parameterTypes = methodType.getParameterTypes();
                    for (int i = 0; i < arguments.size(); i++) {
                        addIfExpectingServer(parameterTypes.get(Math.min(i, parameterTypes.size() - 1)), arguments.get(i));
                    }
                }
            }

            private void addIfExpectingServer(@Nullable JavaType expectedType, @Nullable Expression expression) {
                JavaType.Variable variable = wireMockRuleVariable(expression);
                if (variable != null && (TypeUtils.isOfClassType(expectedType, WIREMOCK_SERVER) || TypeUtils.isOfClassType(expectedType, "com.github.tomakehurst.wiremock.core.Container"))) {
                    serverVariables.add(key(variable));
                }
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> serverVariables) {
        if (serverVariables.isEmpty()) {
            return TreeVisitor.noop();
        }
        return Preconditions.check(new UsesType<>(WIREMOCK_RULE, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (!(statement instanceof J.VariableDeclarations) || !annotatedWith(((J.VariableDeclarations) statement).getLeadingAnnotations(), RULE)) {
                        continue;
                    }
                    J.VariableDeclarations field = (J.VariableDeclarations) statement;
                    boolean classRule = annotatedWith(field.getLeadingAnnotations(), CLASS_RULE);
                    for (J.VariableDeclarations.NamedVariable variable : field.getVariables()) {
                        if (serverVariables.contains(key(variable.getVariableType()))) {
                            J.Identifier server = asServer(variable.getName().withPrefix(Space.EMPTY));
                            JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "junit-platform-commons-1", "wiremock-jre8-2.35");
                            cd = callInLifecycleMethod(cd, server, "start",
                                    classRule ? "org.junit.BeforeClass" : "org.junit.Before",
                                    classRule ? "org.junit.jupiter.api.BeforeAll" : "org.junit.jupiter.api.BeforeEach",
                                    classRule, parser);
                            cd = callInLifecycleMethod(cd, server, "stop",
                                    classRule ? "org.junit.AfterClass" : "org.junit.After",
                                    classRule ? "org.junit.jupiter.api.AfterAll" : "org.junit.jupiter.api.AfterEach",
                                    classRule, parser);
                        }
                    }
                }
                return cd;
            }

            private J.ClassDeclaration callInLifecycleMethod(J.ClassDeclaration cd, J.Identifier server, String serverMethod, String junit4Annotation, String jupiterAnnotation, boolean classRule, JavaParser.Builder<?, ?> parser) {
                List<String> lifecycleAnnotations = Arrays.asList(junit4Annotation, jupiterAnnotation);
                String call = "#{any(" + WIREMOCK_SERVER + ")}." + serverMethod + "();";
                Statement anchor = cd.getBody().getStatements().get(0);
                for (Statement statement : cd.getBody().getStatements()) {
                    J.MethodDeclaration md = statement instanceof J.MethodDeclaration ? (J.MethodDeclaration) statement : null;
                    if (md != null && md.getBody() != null) {
                        if (annotatedWith(md.getLeadingAnnotations(), lifecycleAnnotations)) {
                            J.Block body = md.getBody();
                            J.MethodDeclaration withCall = JavaTemplate.builder(call)
                                    .javaParser(parser)
                                    .build()
                                    .apply(new Cursor(new Cursor(updateCursor(cd), cd.getBody()), md), "start".equals(serverMethod) ? body.getCoordinates().firstStatement() : body.getCoordinates().lastStatement(), server);
                            return cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), s -> s == md ? withCall : s)));
                        }
                        if (annotatedWith(md.getLeadingAnnotations(), LIFECYCLE_ANNOTATIONS)) {
                            anchor = statement;
                        }
                    } else if (statement instanceof J.VariableDeclarations) {
                        anchor = statement;
                    }
                }
                maybeAddImport(jupiterAnnotation);
                String annotation = jupiterAnnotation.substring(jupiterAnnotation.lastIndexOf('.') + 1);
                return JavaTemplate.builder("@" + annotation + "\n" + (classRule ? "static " : "") + "void " + serverMethod + "WireMock() {\n" + call + "\n}")
                        .imports(jupiterAnnotation)
                        .javaParser(parser)
                        .build()
                        .apply(updateCursor(cd), anchor.getCoordinates().after(), server);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                if (vd.getTypeExpression() == null || !TypeUtils.isOfClassType(multiVariable.getType(), WIREMOCK_RULE) || vd.getVariables().stream().noneMatch(v -> serverVariables.contains(key(v.getVariableType())))) {
                    return vd;
                }
                JavaType.FullyQualified server = serverType(multiVariable.getType());
                vd = (J.VariableDeclarations) new RemoveAnnotationVisitor(RULE).visitNonNull(vd, ctx, getCursor().getParentOrThrow());
                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.junit.ClassRule");
                maybeRemoveImport(WIREMOCK_RULE);
                maybeAddImport(WIREMOCK_SERVER);
                return vd
                        .withTypeExpression(TypeTree.build(server.getClassName()).withType(server).withPrefix(vd.getTypeExpression().getPrefix()))
                        .withVariables(ListUtils.map(vd.getVariables(), v -> v.withType(server)));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (!NEW_WIREMOCK_RULE.matches(n)) {
                    return n;
                }
                Object parent = getCursor().getParentTreeCursor().getValue();
                JavaType.Variable target = null;
                if (parent instanceof J.VariableDeclarations.NamedVariable) {
                    target = ((J.VariableDeclarations.NamedVariable) parent).getVariableType();
                } else if (parent instanceof J.Assignment) {
                    target = SystemRules.referencedField(((J.Assignment) parent).getVariable());
                }
                if (!serverVariables.contains(key(target))) {
                    return n;
                }
                JavaType.FullyQualified server = serverType(n.getType());
                List<Expression> arguments = n.getArguments();
                JavaType.Method constructor = n.getConstructorType();
                if (arguments.size() == 2 && TypeUtils.isAssignableTo("com.github.tomakehurst.wiremock.core.Options", arguments.get(0).getType())) {
                    arguments = arguments.subList(0, 1);
                    if (constructor != null) {
                        constructor = constructor.withParameterTypes(constructor.getParameterTypes().subList(0, 1)).withParameterNames(constructor.getParameterNames().subList(0, 1));
                    }
                }
                return n.withClazz(TypeTree.build(server.getClassName()).withType(server).withPrefix(n.getClazz() == null ? Space.SINGLE_SPACE : n.getClazz().getPrefix()))
                        .withArguments(arguments)
                        .withConstructorType(constructor == null ? null : constructor.withDeclaringType(server).withReturnType(server));
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                return isServerVariable(identifier) ? asServer(i) : i;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment a = super.visitAssignment(assignment, ctx);
                return isServerVariable(assignment.getVariable()) ? a.withType(a.getVariable().getType()) : a;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                return isServerVariable(fieldAccess) ? fa.withType(fa.getName().getType()) : fa;
            }

            private boolean isServerVariable(Expression expression) {
                JavaType.Variable variable = wireMockRuleVariable(expression);
                return variable != null && serverVariables.contains(key(variable));
            }
        });
    }

    private static JavaType.FullyQualified serverType(@Nullable JavaType wireMockRule) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(wireMockRule);
        if (type != null && TypeUtils.isOfClassType(type, WIREMOCK_RULE) && type.getSupertype() != null) {
            return type.getSupertype();
        }
        return type != null && TypeUtils.isOfClassType(type, WIREMOCK_SERVER) ? type : JavaType.ShallowClass.build(WIREMOCK_SERVER);
    }

    private static J.Identifier asServer(J.Identifier identifier) {
        JavaType.Variable variable = identifier.getFieldType();
        JavaType.FullyQualified server = serverType(variable == null ? identifier.getType() : variable.getType());
        return identifier.withType(server).withFieldType(variable == null ? null : variable.withType(server));
    }

    private static @Nullable String key(JavaType.@Nullable Variable variable) {
        return variable == null ? null : variable.getOwner() + "#" + variable.getName();
    }

    private static JavaType.@Nullable Variable wireMockRuleVariable(@Nullable Expression expression) {
        JavaType.Variable variable = SystemRules.referencedField(expression);
        return variable != null && TypeUtils.isOfClassType(variable.getType(), WIREMOCK_RULE) ? variable : null;
    }

    private static boolean annotatedWith(List<J.Annotation> annotations, AnnotationMatcher matcher) {
        return annotations.stream().anyMatch(matcher::matches);
    }

    private static boolean annotatedWith(List<J.Annotation> annotations, Collection<String> fullyQualifiedNames) {
        return annotations.stream().anyMatch(a -> fullyQualifiedNames.stream().anyMatch(fqn -> TypeUtils.isOfClassType(a.getType(), fqn)));
    }
}
