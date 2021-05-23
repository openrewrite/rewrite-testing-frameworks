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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TemporaryFolderToTempDir extends Recipe {
    private static final ThreadLocal<JavaParser> TEMPDIR_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(Collections.singletonList(
                    Parser.Input.fromString("" +
                            "package org.junit.jupiter.api.io;\n" +
                            "public @interface TempDir {}")
            )).build()
    );

    @Override
    public String getDisplayName() {
        return "Migrate JUnit 4 `TemporaryFolder` to JUnit Jupiter `TempDir`";
    }

    @Override
    public String getDescription() {
        return "Translates JUnit4's `org.junit.rules.TemporaryFolder` into JUnit 5's `org.junit.jupiter.api.io.TempDir`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>("org.junit.rules.TemporaryFolder");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TemporaryFolderToTempDirVisitor();
    }

    private static class TemporaryFolderToTempDirVisitor extends JavaVisitor<ExecutionContext> {
        private static final JavaType.Class FILE_TYPE = JavaType.Class.build("java.io.File");
        private static final JavaType.Class STRING_TYPE = JavaType.Class.build("java.lang.String");

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

            J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);

            List<J.VariableDeclarations> fields = cd.getBody().getStatements().stream()
                    .filter(J.VariableDeclarations.class::isInstance)
                    .map(J.VariableDeclarations.class::cast)
                    .collect(Collectors.toList());

            if (fields.stream().anyMatch(it -> TypeUtils.hasElementType(it.getTypeAsFullyQualified(), "org.junit.rules.TemporaryFolder"))) {
                Set<J.VariableDeclarations> tempDirFields = new HashSet<>();
                cd = cd.withBody(
                        cd.getBody().withStatements(
                                ListUtils.map(cd.getBody().getStatements(),
                                        statement -> {
                                            if (!(statement instanceof J.VariableDeclarations)) {
                                                return statement;
                                            }
                                            J.VariableDeclarations field = (J.VariableDeclarations) statement;
                                            if (field.getTypeAsFullyQualified() == null ||
                                                    !field.getTypeAsFullyQualified().getFullyQualifiedName()
                                                            .equals("org.junit.rules.TemporaryFolder")) {
                                                return field;
                                            }
                                            maybeAddImport("java.io.File");
                                            maybeAddImport("org.junit.jupiter.api.io.TempDir");
                                            maybeRemoveImport("org.junit.Rule");
                                            maybeRemoveImport("org.junit.rules.TemporaryFolder");

                                            String fieldVars = field.getVariables().stream()
                                                    .map(v -> v.withInitializer(null))
                                                    .map(J::print).collect(Collectors.joining(","));
                                            field = field.withTemplate(
                                                    template("@TempDir\nFile#{};")
                                                            .imports("java.io.File", "org.junit.jupiter.api.io.TempDir")
                                                            .javaParser(TEMPDIR_PARSER::get)
                                                            .build(),
                                                    field.getCoordinates().replace(), fieldVars);
                                            tempDirFields.add(field);
                                            return field;
                                        }
                                )
                        )
                );

                doAfterVisit(new ReplaceTemporaryFolderMethods(tempDirFields));
            }

            return cd;
        }

        /**
         * This visitor replaces methods from TemporaryFolder with JUnit5-compatible alternatives:
         * <p>
         * File getRoot()
         * File newFile()
         * File newFile(String fileName)
         * File newFolder()
         * File newFolder(String... folderNames)
         * File newFolder(String folder)
         */
        private static class ReplaceTemporaryFolderMethods extends JavaVisitor<ExecutionContext> {

            private final Set<J.VariableDeclarations> tempDirFields;

            private ReplaceTemporaryFolderMethods(Set<J.VariableDeclarations> tempDirFields) {
                this.tempDirFields = tempDirFields;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!(m.getSelect() instanceof J.Identifier)) {
                    return m;
                }
                J.Identifier receiver = (J.Identifier) m.getSelect();
                if (receiver != null && m.getType() != null && TypeUtils.hasElementType(m.getType().getDeclaringType(), "org.junit.rules.TemporaryFolder")) {
                    for (J.VariableDeclarations tempDirField : tempDirFields) {
                        for (J.VariableDeclarations.NamedVariable tempDirFieldVar : tempDirField.getVariables()) {
                            String fieldName = tempDirFieldVar.getSimpleName();
                            if (fieldName.equals(receiver.getSimpleName())) {
                                List<Expression> args = m.getArguments();
                                // handle TemporaryFolder.newFile() and TemporaryFolder.newFile(String)
                                switch (m.getName().getSimpleName()) {
                                    case "newFile":
                                        if (args.size() == 1 && args.get(0) instanceof J.Empty) {
                                            m = m.withTemplate(
                                                    template("File.createTempFile(\"junit\", null, " + fieldName + ");").build(),
                                                    m.getCoordinates().replace()
                                            );
                                        } else {
                                            doAfterVisit(new AddNewFileMethod(fieldName, method));
                                        }
                                        break;
                                    case "getRoot":
                                        return receiver.withPrefix(m.getPrefix());
                                    case "newFolder":
                                        if (args.size() == 1 && args.get(0) instanceof J.Empty) {
                                            m = m.withTemplate(template("Files.createTempDirectory(#{}.toPath(), \"junit\").toFile();").imports("java.nio.file.Files", "java.io.File")
                                                    .build(), m.getCoordinates().replace(), fieldName);
                                            maybeAddImport("java.nio.file.Files");
                                        } else {
                                            doAfterVisit(new AddNewFolderMethod(fieldName, method));
                                        }
                                        break;
                                }
                            }
                        }
                    }
                }
                return maybeAutoFormat(method, m, ctx);
            }
        }

        private static class AddNewFileMethod extends JavaIsoVisitor<ExecutionContext> {
            private final String fieldName;
            private final J.MethodInvocation methodInvocation;

            private AddNewFileMethod(String fieldName, J.MethodInvocation methodInvocation) {
                this.fieldName = fieldName;
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

                            return m.getSimpleName().equals("newFile")
                                    && params.size() == 2
                                    && params.get(0).hasClassType(FILE_TYPE)
                                    && params.get(1).hasClassType(STRING_TYPE);
                        });
                if (!methodAlreadyExists) {
                    cd = cd.withTemplate(template("private static File newFile(File root, String fileName) throws IOException {\n" +
                            "    File file = new File(root, fileName);\n" +
                            "    file.createNewFile();\n" +
                            "    return file;\n" +
                            "}\n")
                            .imports("java.io.File", "java.io.IOException")
                            .build(), cd.getBody().getCoordinates().lastStatement());
                    maybeAddImport("java.io.File");
                    maybeAddImport("java.io.IOException");
                }
                doAfterVisit(new TranslateNewFileMethodInvocation());
                return cd;
            }

            private class TranslateNewFileMethodInvocation extends JavaIsoVisitor<ExecutionContext> {

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                    if (method.isScope(methodInvocation)) {
                        List<Expression> args = method.getArguments();
                        return method.withTemplate(template("newFile(#{}, #{});").build(), method.getCoordinates().replace(), fieldName, args.get(0));
                    }
                    return super.visitMethodInvocation(method, executionContext);
                }
            }
        }


        private static class AddNewFolderMethod extends JavaIsoVisitor<ExecutionContext> {
            private final String fieldName;
            private final J.MethodInvocation methodInvocation;

            private AddNewFolderMethod(String fieldName, J.MethodInvocation methodInvocation) {
                this.fieldName = fieldName;
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
                                    && params.get(1).hasClassType(STRING_TYPE)
                                    && params.get(1) instanceof J.VariableDeclarations
                                    && ((J.VariableDeclarations) params.get(1)).getVarargs() != null;
                        });
                if (!methodAlreadyExists) {
                    cd = cd.withTemplate(template(
                            "private static File newFolder(File root, String ... folders) throws IOException {\n" +
                                    "    File result = new File(root, String.join(\"/\", folders));\n" +
                                    "    if(!result.mkdirs()) {\n" +
                                    "        throw new IOException(\"Couldn't create folders \" + root);\n" +
                                    "    }\n" +
                                    "    return result;\n" +
                                    "}"
                    ).imports("java.io.File", "java.io.IOException").build(), cd.getBody().getCoordinates().lastStatement());
                    maybeAddImport("java.io.File");
                    maybeAddImport("java.io.IOException");
                }
                doAfterVisit(new TranslateNewFolderMethodInvocation());
                return cd;
            }

            private class TranslateNewFolderMethodInvocation extends JavaIsoVisitor<ExecutionContext> {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                    if (method.isScope(methodInvocation)) {
                        return method.withTemplate(template("newFolder(#{}, #{});")
                                .build(), method.getCoordinates().replace(), fieldName, printArgs(method.getArguments()));
                    }
                    return super.visitMethodInvocation(method, executionContext);
                }

                /**
                 * As of rewrite 5.5.0, J.MethodInvocation.Arguments.print() returns an empty String
                 * Roll our own good-enough print() method here
                 */
                private String printArgs(List<Expression> args) {
                    return args.stream().map(J::print).collect(Collectors.joining(","));
                }
            }
        }
    }
}
