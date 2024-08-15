/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/**
 * Recipe for converting JUnit 4 okhttp3 MockWebServer Rules with their JUnit 5 equivalent.
 * Note this recipe upgrades okhttp3 to version 4.x there are a few backwards incompatible changes:
 * https://square.github.io/okhttp/upgrading_to_okhttp_4/#backwards-incompatible-changes
 * <p>
 * - If MockWebServer Rule exists remove the Rule annotation and update okhttp3 to version 4.x
 * - If AfterEach method exists insert a close statement for the MockWebServer and throws for IOException
 * - If AfterEach does not exist then insert new afterEachTest method closing MockWebServer
 */
@SuppressWarnings({"JavadocLinkAsPlainText"})
public class UpdateMockWebServer extends Recipe {
    private static final AnnotationMatcher RULE_MATCHER = new AnnotationMatcher("@org.junit.Rule");
    private static final AnnotationMatcher AFTER_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
    private static final String AFTER_EACH_FQN = "org.junit.jupiter.api.AfterEach";
    private static final String MOCK_WEB_SERVER_FQN = "okhttp3.mockwebserver.MockWebServer";
    private static final String IO_EXCEPTION_FQN = "java.io.IOException";
    private static final String MOCK_WEBSERVER_VARIABLE = "mock-web-server-variable";
    private static final String AFTER_EACH_METHOD = "after-each-method";

    @Override
    public String getDisplayName() {
        return "OkHttp 3.x `MockWebServer` `@Rule` To 4.x `MockWebServer`";
    }

    @Override
    public String getDescription() {
        return "Replace usages of okhttp3 3.x `@Rule` MockWebServer with 4.x `MockWebServer`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("org.junit.Rule", false),
                        new UsesType<>("okhttp3.mockwebserver.MockWebServer", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Nullable
                    private JavaParser.Builder<?, ?> javaParser;

                    private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                        if (javaParser == null) {
                            javaParser = JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "junit-4.13", "junit-jupiter-api-5.9", "apiguardian-api-1.1",
                                            "mockwebserver-3.14");
                        }
                        return javaParser;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        final J.Identifier mockWebServerVariable = getCursor().pollMessage(MOCK_WEBSERVER_VARIABLE);
                        final J.MethodDeclaration afterEachMethod = getCursor().pollMessage(AFTER_EACH_METHOD);
                        if (mockWebServerVariable != null) {
                            if (afterEachMethod == null) {
                                cd = JavaTemplate.builder("@AfterEach\nvoid afterEachTest() throws IOException {#{any(okhttp3.mockwebserver.MockWebServer)}.close();\n}")
                                        .contextSensitive()
                                        .imports(AFTER_EACH_FQN, MOCK_WEB_SERVER_FQN, IO_EXCEPTION_FQN)
                                        .javaParser(javaParser(ctx))
                                        .build()
                                        .apply(
                                                updateCursor(cd),
                                                cd.getBody().getCoordinates().lastStatement(),
                                                mockWebServerVariable
                                        );
                                maybeAddImport(AFTER_EACH_FQN);
                                maybeAddImport(IO_EXCEPTION_FQN);
                            } else {
                                for (Statement statement : cd.getBody().getStatements()) {
                                    if (statement == afterEachMethod) {
                                        cd = JavaTemplate.builder("#{any(okhttp3.mockwebserver.MockWebServer)}.close();")
                                                .contextSensitive()
                                                .imports(AFTER_EACH_FQN, MOCK_WEB_SERVER_FQN, IO_EXCEPTION_FQN)
                                                .javaParser(javaParser(ctx))
                                                .build()
                                                .apply(
                                                        updateCursor(cd),
                                                        requireNonNull(afterEachMethod.getBody()).getCoordinates().lastStatement(),
                                                        mockWebServerVariable
                                                );
                                    }
                                }

                                cd = autoFormat(cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                                    if (statement.isScope(afterEachMethod)) {
                                        J.MethodDeclaration method = (J.MethodDeclaration) statement;
                                        if (method.getBody() != null) {
                                            if (method.getThrows() == null || method.getThrows().stream()
                                                    .noneMatch(n -> TypeUtils.isOfClassType(n.getType(), IO_EXCEPTION_FQN))) {
                                                J.Identifier ioExceptionIdent = new J.Identifier(Tree.randomId(),
                                                        Space.format(" "),
                                                        Markers.EMPTY,
                                                        emptyList(),
                                                        "IOException",
                                                        JavaType.ShallowClass.build(IO_EXCEPTION_FQN),
                                                        null);
                                                method = method.withThrows(ListUtils.concat(method.getThrows(), ioExceptionIdent));
                                                maybeAddImport(IO_EXCEPTION_FQN);
                                            }
                                        }
                                        statement = method;
                                    }
                                    return statement;
                                }))), ctx);
                            }

                            maybeRemoveImport("org.junit.Rule");
                        }
                        return cd;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, ctx);
                        JavaType.FullyQualified fieldType = variableDeclarations.getTypeAsFullyQualified();
                        if (TypeUtils.isOfClassType(fieldType, "okhttp3.mockwebserver.MockWebServer")) {
                            variableDeclarations = variableDeclarations.withLeadingAnnotations(ListUtils.map(variableDeclarations.getLeadingAnnotations(), annotation -> {
                                if (RULE_MATCHER.matches(annotation)) {
                                    return null;
                                }
                                return annotation;
                            }));
                            if (variableDeclarations.getLeadingAnnotations().isEmpty()) {
                                variableDeclarations = variableDeclarations.withModifiers(Space.formatFirstPrefix(
                                        variableDeclarations.getModifiers(), Space.EMPTY));
                            }
                        }
                        if (multiVariable != variableDeclarations) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, MOCK_WEBSERVER_VARIABLE, variableDeclarations.getVariables().get(0).getName());
                        }
                        return variableDeclarations;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                        for (J.Annotation annotation : md.getLeadingAnnotations()) {
                            if (AFTER_EACH_MATCHER.matches(annotation)) {
                                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, AFTER_EACH_METHOD, md);
                                break;
                            }
                        }
                        return md;
                    }
                });
    }

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new UpgradeDependencyVersion("com.squareup.okhttp3", "mockwebserver", "4.X",
                null, false, emptyList()));
    }
}
