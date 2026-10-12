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

public class WireMockRuleToWireMockServer extends ScanningRecipe<Set<JavaType.Variable>> {

    private static final String WIREMOCK_RULE = "com.github.tomakehurst.wiremock.junit.WireMockRule";
    private static final String WIREMOCK_SERVER = "com.github.tomakehurst.wiremock.WireMockServer";
    private static final MethodMatcher NEW_WIREMOCK_RULE = new MethodMatcher(WIREMOCK_RULE + " <constructor>(..)");
    private static final AnnotationMatcher RULE = new AnnotationMatcher("@org.junit.Rule");
    private static final AnnotationMatcher CLASS_RULE = new AnnotationMatcher("@org.junit.ClassRule");
    private static final Collection<String> SERVER_ONLY_METHODS = Arrays.asList("addMockServiceRequestListener", "checkForUnmatchedRequests", "enableRecordMappings", "getGlobalSettingsHolder", "isHttpEnabled", "isHttpsEnabled", "isRunning", "loadMappingsUsing", "shutdown", "start", "stop");
    private static final Collection<String> LIFECYCLE_ANNOTATIONS = Arrays.asList("org.junit.Before", "org.junit.After", "org.junit.BeforeClass", "org.junit.AfterClass", "org.junit.jupiter.api.BeforeEach", "org.junit.jupiter.api.AfterEach", "org.junit.jupiter.api.BeforeAll", "org.junit.jupiter.api.AfterAll");

    @Getter
    final String displayName = "Use `WireMockServer` for a `WireMockRule` used as a server";

    @Getter
    final String description = "Replace a `WireMockRule` that is started, stopped or passed on as a `WireMockServer` with a plain `WireMockServer`, as the JUnit 5 `WireMockExtension` has no such methods. The server is started and stopped around each test, or around all tests for a `@ClassRule`.";

    @Override
    public Set<JavaType.Variable> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaType.Variable> serverVariables) {
        return Preconditions.check(new UsesType<>(WIREMOCK_RULE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                JavaType.Variable select = wireMockRuleVariable(method.getSelect());
                if (select != null && SERVER_ONLY_METHODS.contains(method.getSimpleName())) {
                    serverVariables.add(select);
                }
                addArgumentsExpectingServer(method.getMethodType(), method.getArguments());
                return super.visitMethodInvocation(method, ctx);
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
                    serverVariables.add(variable);
                }
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaType.Variable> serverVariables) {
        return Preconditions.check(new UsesType<>(WIREMOCK_RULE, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations field = (J.VariableDeclarations) statement;
                        boolean classRule = annotatedWith(field.getLeadingAnnotations(), CLASS_RULE);
                        if (classRule || annotatedWith(field.getLeadingAnnotations(), RULE)) {
                            for (J.VariableDeclarations.NamedVariable variable : field.getVariables()) {
                                if (serverVariables.contains(variable.getVariableType())) {
                                    J.Identifier server = variable.getName().withPrefix(Space.EMPTY).withType(serverType(variable.getType()));
                                    cd = callInLifecycleMethod(cd, server, "start", classRule ? "org.junit.BeforeClass" : "org.junit.Before", classRule ? "org.junit.jupiter.api.BeforeAll" : "org.junit.jupiter.api.BeforeEach", classRule, ctx);
                                    cd = callInLifecycleMethod(cd, server, "stop", classRule ? "org.junit.AfterClass" : "org.junit.After", classRule ? "org.junit.jupiter.api.AfterAll" : "org.junit.jupiter.api.AfterEach", classRule, ctx);
                                }
                            }
                        }
                    }
                }
                return cd;
            }

            private J.ClassDeclaration callInLifecycleMethod(J.ClassDeclaration cd, J.Identifier server, String serverMethod, String junit4Annotation, String jupiterAnnotation, boolean isStatic, ExecutionContext ctx) {
                String call = "#{any(" + WIREMOCK_SERVER + ")}." + serverMethod + "();";
                JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "wiremock-jre8-2.35");
                Statement anchor = cd.getBody().getStatements().get(0);
                for (Statement statement : cd.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration && ((J.MethodDeclaration) statement).getBody() != null) {
                        J.MethodDeclaration md = (J.MethodDeclaration) statement;
                        if (annotatedWith(md.getLeadingAnnotations(), Arrays.asList(junit4Annotation, jupiterAnnotation))) {
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
                return JavaTemplate.builder("@" + annotation + "\n" + (isStatic ? "static " : "") + "void " + serverMethod + "WireMock() {\n" + call + "\n}")
                        .imports(jupiterAnnotation)
                        .javaParser(parser)
                        .build()
                        .apply(updateCursor(cd), anchor.getCoordinates().after(), server);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                if (vd.getTypeExpression() == null || vd.getVariables().stream().noneMatch(v -> serverVariables.contains(v.getVariableType()))) {
                    return vd;
                }
                JavaType.FullyQualified server = serverType(multiVariable.getType());
                vd = (J.VariableDeclarations) new RemoveAnnotationVisitor(RULE).visitNonNull(vd, ctx, getCursor().getParentOrThrow());
                vd = (J.VariableDeclarations) new RemoveAnnotationVisitor(CLASS_RULE).visitNonNull(vd, ctx, getCursor().getParentOrThrow());
                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.junit.ClassRule");
                maybeRemoveImport(WIREMOCK_RULE);
                maybeAddImport(WIREMOCK_SERVER);
                return vd
                        .withTypeExpression(TypeTree.build(server.getClassName()).withType(server).withPrefix(vd.getTypeExpression().getPrefix()))
                        .withVariables(ListUtils.map(vd.getVariables(), v -> v.getVariableType() == null ? v : v.withVariableType(v.getVariableType().withType(server))));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (!NEW_WIREMOCK_RULE.matches(n) || !(parent instanceof J.VariableDeclarations.NamedVariable) || !serverVariables.contains(((J.VariableDeclarations.NamedVariable) parent).getVariableType())) {
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
                JavaType.Variable variable = i.getFieldType();
                if (variable != null && serverVariables.contains(variable)) {
                    JavaType.FullyQualified server = serverType(variable.getType());
                    return i.withType(server).withFieldType(variable.withType(server));
                }
                return i;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess fa = super.visitFieldAccess(fieldAccess, ctx);
                return serverVariables.contains(fa.getName().getFieldType()) ? fa.withType(fa.getName().getType()) : fa;
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

    private static JavaType.@Nullable Variable wireMockRuleVariable(@Nullable Expression expression) {
        JavaType.Variable variable = expression instanceof J.Identifier ? ((J.Identifier) expression).getFieldType() :
                expression instanceof J.FieldAccess ? ((J.FieldAccess) expression).getName().getFieldType() : null;
        return variable != null && TypeUtils.isOfClassType(variable.getType(), WIREMOCK_RULE) ? variable : null;
    }

    private static boolean annotatedWith(List<J.Annotation> annotations, AnnotationMatcher matcher) {
        return annotations.stream().anyMatch(matcher::matches);
    }

    private static boolean annotatedWith(List<J.Annotation> annotations, Collection<String> fullyQualifiedNames) {
        return annotations.stream().anyMatch(a -> fullyQualifiedNames.stream().anyMatch(fqn -> TypeUtils.isOfClassType(a.getType(), fqn)));
    }
}
