package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
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

import java.util.Collections;
import java.util.UUID;

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

    private static final ThreadLocal<JavaParser> OKHTTP3_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(Collections.singletonList(
                    Parser.Input.fromString("package okhttp3.mockwebserver;" +
                            "public final class MockWebServer extends java.io.Closeable {}"
                    )
            )).build());

    @Override
    public String getDisplayName() {
        return "okhttp3 3.x MockWebserver @Rule To 4.x MockWebServer";
    }

    @Override
    public String getDescription() {
        return "Replace usages of okhttp3 3.x @Rule MockWebServer with 4.x MockWebServer.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, executionContext);
                if (!FindTypes.find(cu, "org.junit.Rule").isEmpty()
                        && !FindTypes.find(cu, "okhttp3.mockwebserver.MockWebServer").isEmpty()) {
                    c = c.withMarker(new RecipeSearchResult(UpdateMockWebServer.this));
                }
                return c;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final static String MOCK_WEBSERVER_RULE = "mock-web-server-rule";
            private final static String AFTER_EACH_METHOD = "after-each-method";

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                String mockWebServerVarableName = getCursor().pollMessage(MOCK_WEBSERVER_RULE);
                final J.MethodDeclaration afterEachMethod = getCursor().pollMessage(AFTER_EACH_METHOD);
                if (mockWebServerVarableName != null) {
                    if (afterEachMethod != null) {
                        cd = maybeAutoFormat(cd, cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                            if (statement == afterEachMethod) {
                                statement = statement.withTemplate(template(mockWebServerVarableName + ".close();")
                                        .javaParser(OKHTTP3_PARSER.get()).build(), ((J.MethodDeclaration) statement).getBody()
                                        .getCoordinates().lastStatement());

                                if (((J.MethodDeclaration) statement).getThrows() == null || ((J.MethodDeclaration) statement).getThrows().stream()
                                        .noneMatch(n -> TypeUtils.isOfClassType(n.getType(), "java.io.IOException"))) {
                                    J.Identifier ioExceptionIdent = J.Identifier.build(UUID.randomUUID(),
                                            Space.format(" "),
                                            Markers.EMPTY,
                                            "IOException",
                                            JavaType.Class.build("java.io.IOException"));
                                    statement = ((J.MethodDeclaration) statement).withThrows(ListUtils.concat(((J.MethodDeclaration) statement).getThrows(), ioExceptionIdent));
                                    maybeAddImport("java.io.IOException");
                                }

                            }
                            return statement;
                        }))), executionContext);
                    } else {
                        String closeMethod = "@AfterEach void afterEachTest() throws IOException {" + mockWebServerVarableName + ".close();}";
                        cd = maybeAutoFormat(cd, cd.withBody(cd.getBody().withTemplate(template(closeMethod)
                                .imports("org.junit.jupiter.api.AfterEach", "okhttp3.mockwebserver", "java.io.IOException")
                                .javaParser(OKHTTP3_PARSER.get()).build(), cd.getBody().getCoordinates().lastStatement())), executionContext);
                        maybeAddImport("org.junit.jupiter.api.AfterEach");
                        maybeAddImport("java.io.IOException");
                    }
                    maybeRemoveImport("org.junit.Rule");
                    doAfterVisit(new UpgradeDependencyVersion("com.squareup.okhttp3", "mockwebserver", "4.X", null));
                }
                return cd;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
                J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, executionContext);
                JavaType.Class fieldType = variableDeclarations.getTypeAsClass();
                if (TypeUtils.isOfClassType(fieldType, "okhttp3.mockwebserver.MockWebServer")) {
                    variableDeclarations = variableDeclarations.withLeadingAnnotations(ListUtils.map(variableDeclarations.getLeadingAnnotations(), annotation -> {
                        if (RULE_MATCHER.matches(annotation)) {
                            return null;
                        }
                        return annotation;
                    }));
                }
                if (multiVariable != variableDeclarations) {
                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, MOCK_WEBSERVER_RULE, variableDeclarations.getVariables().get(0).getSimpleName());
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
