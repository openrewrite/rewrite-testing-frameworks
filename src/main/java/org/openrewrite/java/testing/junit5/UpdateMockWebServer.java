/*
 * Copyright 2020 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.RecipeSearchResult;
import org.openrewrite.maven.UpgradeDependencyVersion;

import java.util.Arrays;
import java.util.UUID;

import static org.openrewrite.Tree.randomId;

/**
 * Recipe for converting JUnit4 okhttp3 MockWebServer Rules with their JUnit5 equivalent.
 * Note this recipe upgrades okhttp3 to version 4.x there are a few backwards incompatible changes: https://square.github.io/okhttp/upgrading_to_okhttp_4/#backwards-incompatible-changes
 * - If MockWebServer Rule exists remove the Rule annotation and update okhttp3 to version 4.x
 * - If AfterEach method exists insert a close statement for the MockWebServer and throws for IOException
 * - If AfterEach does not exist then insert new afterEachTest method closing MockWebServer
 */
public class UpdateMockWebServer extends Recipe {
    private static final AnnotationMatcher RULE_MATCHER = new AnnotationMatcher("@org.junit.Rule");
    private static final AnnotationMatcher AFTER_EACH_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.AfterEach");
    private static final String AFTER_EACH_FQN = "org.junit.jupiter.api.AfterEach";
    private static final String MOCK_WEB_SERVER_FQN = "okhttp3.mockwebserver.MockWebServer";
    private static final String IO_EXCEPTION_FQN = "java.io.IOException";
    private static final String MOCK_WEBSERVER_VARIABLE = "mock-web-server-variable";
    private static final String AFTER_EACH_METHOD = "after-each-method";

    private static final ThreadLocal<JavaParser> OKHTTP3_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(Arrays.asList(
                    Parser.Input.fromString("package okhttp3.mockwebserver;" +
                            "public final class MockWebServer  extends ExternalResource implements Closeable {" +
                            "   @Override" +
                            "   public void close() throws IOException {}" +
                            "}"
                    ),
                    Parser.Input.fromString("package org.junit.jupiter.api;\n" +
                            "public @interface AfterEach {}")
            )).build());

    UUID id = randomId();

    @Override
    public String getDisplayName() {
        return "okhttp3 3.x MockWebserver @Rule To 4.x MockWebServer";
    }

    @Override
    public String getDescription() {
        return "Replace usages of okhttp3 3.x @Rule MockWebServer with 4.x MockWebServer.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, executionContext);
                if (!FindTypes.find(cu, "org.junit.Rule").isEmpty()
                        && !FindTypes.find(cu, "okhttp3.mockwebserver.MockWebServer").isEmpty()) {
                    c = c.withMarkers(c.getMarkers().addIfAbsent(new RecipeSearchResult(id, UpdateMockWebServer.this)));
                }
                return c;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                final J.Identifier mockWebServerVariable = getCursor().pollMessage(MOCK_WEBSERVER_VARIABLE);
                final J.MethodDeclaration afterEachMethod = getCursor().pollMessage(AFTER_EACH_METHOD);
                if (mockWebServerVariable != null) {
                    if (afterEachMethod == null) {
                        final String closeMethod = "@AfterEach\nvoid afterEachTest() throws IOException {#{any(okhttp3.mockwebserver.MockWebServer)}.close();\n}";
                        J.Block body = cd.getBody();
                        body = maybeAutoFormat(body, body.withTemplate(template(closeMethod)
                                .imports(AFTER_EACH_FQN, MOCK_WEB_SERVER_FQN, IO_EXCEPTION_FQN)
                                .javaParser(OKHTTP3_PARSER::get).build(), body.getCoordinates().lastStatement(), mockWebServerVariable), executionContext);
                        cd = cd.withBody(body);
                        maybeAddImport(AFTER_EACH_FQN);
                        maybeAddImport(IO_EXCEPTION_FQN);
                    } else {
                        J.Block body = cd.getBody();
                        body = maybeAutoFormat(body, body.withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                            if (statement == afterEachMethod) {
                                J.MethodDeclaration method = (J.MethodDeclaration) statement;
                                if (method.getBody() != null) {
                                    method = method.withTemplate(template("#{any(okhttp3.mockwebserver.MockWebServer)}.close();")
                                            .javaParser(OKHTTP3_PARSER::get).build(), method.getBody()
                                            .getCoordinates().lastStatement(), mockWebServerVariable);

                                    if (method.getThrows() == null || method.getThrows().stream()
                                            .noneMatch(n -> TypeUtils.isOfClassType(n.getType(), IO_EXCEPTION_FQN))) {
                                        J.Identifier ioExceptionIdent = J.Identifier.build(UUID.randomUUID(),
                                                Space.format(" "),
                                                Markers.EMPTY,
                                                "IOException",
                                                JavaType.Class.build(IO_EXCEPTION_FQN));
                                        method = method.withThrows(ListUtils.concat(method.getThrows(), ioExceptionIdent));
                                        maybeAddImport(IO_EXCEPTION_FQN);
                                    }
                                }
                                statement = method;
                            }
                            return statement;
                        })), executionContext);
                        cd = cd.withBody(body);
                    }
                    maybeRemoveImport("org.junit.Rule");
                    doAfterVisit(new UpgradeDependencyVersion("com.squareup.okhttp3", "mockwebserver", "4.X", null, false));
                }
                return cd;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, executionContext);
                JavaType.FullyQualified fieldType = variableDeclarations.getTypeAsFullyQualified();
                if (TypeUtils.isOfClassType(fieldType, "okhttp3.mockwebserver.MockWebServer")) {
                    variableDeclarations = variableDeclarations.withLeadingAnnotations(ListUtils.map(variableDeclarations.getLeadingAnnotations(), annotation -> {
                        if (RULE_MATCHER.matches(annotation)) {
                            return null;
                        }
                        return annotation;
                    }));
                }
                if (multiVariable != variableDeclarations) {
                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, MOCK_WEBSERVER_VARIABLE, variableDeclarations.getVariables().get(0).getName());
                }
                return variableDeclarations;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
                if (md.getLeadingAnnotations().stream().anyMatch(AFTER_EACH_MATCHER::matches)) {
                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, AFTER_EACH_METHOD, md);
                }
                return md;
            }
        };


    }
}
