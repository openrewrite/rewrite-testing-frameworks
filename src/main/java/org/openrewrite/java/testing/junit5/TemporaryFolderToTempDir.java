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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TemporaryFolderToTempDir extends Recipe {
    private static final JavaType.Class FILE_TYPE = JavaType.Class.build("java.io.File");
    private static final JavaType.Class STRING_TYPE = JavaType.Class.build("java.lang.String");
    private static final ThreadLocal<JavaParser> TEMPDIR_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(Collections.singletonList(
                    Parser.Input.fromString("" +
                            "package org.junit.jupiter.api.io;\n" +
                            "public @interface TempDir {}")
            )).build()
    );

    @Override
    public String getDisplayName() {
        return "Use JUnit Jupiter `@TempDir`";
    }

    @Override
    public String getDescription() {
        return "Translates JUnit4's `org.junit.rules.TemporaryFolder` into JUnit 5's `org.junit.jupiter.api.io.TempDir`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.rules.TemporaryFolder");
    }

    @Override
    protected TemporaryFolderToTempDirVisitor getVisitor() {
        return new TemporaryFolderToTempDirVisitor();
    }

    private static class TemporaryFolderToTempDirVisitor extends JavaVisitor<ExecutionContext> {
        private static final AnnotationMatcher JUPITER_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@junit.jupiter.api.Test");
        private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");
        private static final AnnotationMatcher TEMPDIR_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.io.TempDir");

        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
            J.CompilationUnit c = (J.CompilationUnit) super.visitCompilationUnit(cu, executionContext);
            if (c != cu) {
                doAfterVisit(new ChangeType("org.junit.rules.TemporaryFolder", "java.io.File"));
                maybeAddImport("java.io.File");
                maybeAddImport("org.junit.jupiter.api.io.TempDir");
                maybeRemoveImport("org.junit.ClassRule");
                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.junit.rules.TemporaryFolder");
            }
            return c;
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
            J.VariableDeclarations m = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, executionContext);
            if (m.getTypeAsFullyQualified() == null ||
                    !m.getTypeAsFullyQualified().getFullyQualifiedName()
                            .equals("org.junit.rules.TemporaryFolder")) {
                return m;
            }
            String fieldVars = m.getVariables().stream()
                    .map(v -> v.withInitializer(null))
                    .map(J::print).collect(Collectors.joining(","));
            String modifiers = m.getModifiers().stream().map(it -> it.getType().name().toLowerCase()).collect(Collectors.joining(" "));
            m = m.withTemplate(
                    template("@TempDir\n#{} File#{};")
                            .imports("java.io.File", "org.junit.jupiter.api.io.TempDir")
                            .javaParser(TEMPDIR_PARSER::get)
                            .build(),
                    m.getCoordinates().replace(),
                    modifiers,
                    fieldVars);
            return m;
        }

        @Override
        public J visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
            J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, executionContext);
            JavaType.FullyQualified fq = TypeUtils.asFullyQualified(nc.getType());
            if (fq != null && "org.junit.rules.TemporaryFolder".equals(fq.getFullyQualifiedName())) {
                return null;
            }
            return nc;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);
            String declaringType = mi.getType() != null ? mi.getType().getDeclaringType().getFullyQualifiedName() : null;
            if ("org.junit.rules.TemporaryFolder".equals(declaringType) && mi.getSelect() != null) {
                switch (mi.getSimpleName()) {
                    case "newFile":
                        return convertToNewFile(mi);
                    case "newFolder":
                        doAfterVisit(new AddNewFolderMethod(mi));
                        break;
                    case "create":
                        return null;
                    default:
                        return mi.getSelect().withPrefix(Space.format(" "));
                }
            }
            return mi;
        }

        private J convertToNewFile(J.MethodInvocation mi) {
            if (mi.getSelect() == null) {
                return mi;
            }
            J tempDir = mi.getSelect().withType(FILE_TYPE);
            List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(Collectors.toList());
            if (args.isEmpty()) {
                return mi.withTemplate(template("new File(#{any(java.io.File)}, \"junit\")")
                        .imports("java.io.File").javaParser(TEMPDIR_PARSER::get).build(), mi.getCoordinates().replace(), tempDir);
            } else if (args.size() == 1) {
                return mi.withTemplate(template("new File(#{any(java.io.File)}, #{any(java.lang.String)})")
                                .imports("java.io.File").javaParser(TEMPDIR_PARSER::get).build(),
                        mi.getCoordinates().replace(), tempDir, args.get(0));
            } else {
                String subDirs = args.stream()
                        .map(Expression::printTrimmed).map(exp -> exp.replace("\"", "")).collect(Collectors.joining("/"));
                return mi.withTemplate(template("new File(#{any(java.io.File)}, #{})")
                                .imports("java.io.File").javaParser(TEMPDIR_PARSER::get).build(),
                        mi.getCoordinates().replace(), tempDir, "\"" + subDirs + "\"");
            }
        }

        /**
         * Replace TemporaryFolder variables defined within MethodDeclaration bodies with @TempDir method parameters.
         * @param method
         * @param executionContext
         * @return
         */
        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, executionContext);
            if (md.getLeadingAnnotations().stream().noneMatch(annotation -> JUNIT_TEST_ANNOTATION_MATCHER.matches(annotation) || JUPITER_TEST_ANNOTATION_MATCHER.matches(annotation))
                    || md.getBody() == null) {
                return md;
            }
            List<Statement> parameters = new ArrayList<>();
            J.Block body = md.getBody();
            List<Statement> statements = (ListUtils.map(md.getBody().getStatements(), st -> {
                if (st instanceof J.VariableDeclarations) {
                    J.VariableDeclarations v = (J.VariableDeclarations) st;
                    if (v.getTypeAsFullyQualified() != null
                            && "java.io.File".equals(v.getTypeAsFullyQualified().getFullyQualifiedName())
                            && v.getLeadingAnnotations().stream().anyMatch(TEMPDIR_ANNOTATION_MATCHER::matches)) {
                        v = v.withPrefix(Space.EMPTY)
                                .withLeadingAnnotations(ListUtils.map(v.getLeadingAnnotations(), annotation -> annotation.withPrefix(Space.EMPTY)));
                        if (v.getTypeExpression() != null) {
                            v = v.withTypeExpression(v.getTypeExpression().withPrefix(Space.format(" ")));
                        }
                        if (parameters.isEmpty()) {
                            parameters.add(v);
                        } else {
                            parameters.add(v.withPrefix(Space.format(" ")));
                        }
                        return null;
                    }
                }
                return st;
            }));
            if (!parameters.isEmpty()) {
                if (md.getParameters().iterator().next() instanceof J.Empty) {
                    md = md.withParameters(parameters);
                } else {
                    md = md.withParameters(ListUtils.concatAll(md.getParameters(), parameters));
                }
                body = maybeAutoFormat(body, body.withStatements(statements), executionContext);
                md = md.withBody(body);
            }
            return md;
        }
    }

    private static class AddNewFolderMethod extends JavaIsoVisitor<ExecutionContext> {
        private final J.MethodInvocation methodInvocation;

        public AddNewFolderMethod(J.MethodInvocation methodInvocation) {
            this.methodInvocation = methodInvocation;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Stream<J.MethodDeclaration> methods = cd.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast);
            boolean methodAlreadyExists = methods
                    .anyMatch(m -> {
                        List<Statement> params = m.getParameters();
                        return m.getSimpleName().equals("newFolder")
                                && params.size() == 2
                                && params.get(0).hasClassType(FILE_TYPE)
                                && params.get(1).hasClassType(STRING_TYPE);
                    });
            if (!methodAlreadyExists) {
                cd = cd.withTemplate(template(
                        "private static File newFolder(File root, String name) throws IOException {\n" +
                                "    File result = new File(root, name);\n" +
                                "    if(!result.mkdirs()) {\n" +
                                "        throw new IOException(\"Couldn't create folders \" + root);\n" +
                                "    }\n" +
                                "    return result;\n" +
                                "}"
                ).imports("java.io.File", "java.io.IOException").javaParser(TEMPDIR_PARSER::get).build(), cd.getBody().getCoordinates().lastStatement());
                maybeAddImport("java.io.File");
                maybeAddImport("java.io.IOException");
            }
            doAfterVisit(new TranslateNewFolderMethodInvocation(methodInvocation));
            return cd;
        }

        private static class TranslateNewFolderMethodInvocation extends JavaVisitor<ExecutionContext> {
            J.MethodInvocation methodScope;

            public TranslateNewFolderMethodInvocation(J.MethodInvocation method) {
                this.methodScope = method;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (!method.isScope(methodScope)) {
                    return method;
                }
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);
                if (mi.getSelect() != null) {
                    J tempDir = mi.getSelect().withType(FILE_TYPE);
                    List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(Collectors.toList());
                    if (args.isEmpty()) {
                        return mi.withTemplate(template("newFolder(#{any(java.io.File)}, \"junit\")")
                                .imports("java.io.File").javaParser(TEMPDIR_PARSER::get).build(), mi.getCoordinates().replace(), tempDir);
                    } else if (args.size() == 1) {
                        return mi.withTemplate(template("newFolder(#{any(java.io.File)}, #{any(java.lang.String)})")
                                        .imports("java.io.File").javaParser(TEMPDIR_PARSER::get).build(),
                                mi.getCoordinates().replace(), tempDir, args.get(0));
                    } else {
                        String subDirs = args.stream()
                                .map(Expression::printTrimmed).map(exp -> exp.replace("\"", "")).collect(Collectors.joining("/"));
                        return mi.withTemplate(template("newFolder(#{any(java.io.File)}, #{})")
                                        .imports("java.io.File").javaParser(TEMPDIR_PARSER::get).build(),
                                mi.getCoordinates().replace(), tempDir, "\"" + subDirs + "\"");
                    }
                }
                return mi;
            }
        }
    }
}
