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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Tree.randomId;

/**
 * Translates JUnit4's org.junit.rules.TemporaryFolder into JUnit 5's org.junit.jupiter.api.io.TempDir
 */
public class TemporaryFolderToTempDir extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TemporaryFolderToTempDirVisitor();
    }

    private static class TemporaryFolderToTempDirVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String RuleFqn = "org.junit.Rule";
        private static final String TemporaryFolderFqn = "org.junit.rules.TemporaryFolder";
        private static final JavaType.Class TempDirType = JavaType.Class.build("org.junit.jupiter.api.io.TempDir");
        private static final J.Identifier TempDirIdent = J.Identifier.build(randomId(), "TempDir", TempDirType);
        private static final JavaType.Class FileType = JavaType.Class.build("java.io.File");
        private static final J.Identifier FileIdent = J.Identifier.build(randomId(), "File", FileType);
        private static final JavaType.Class PathType = JavaType.Class.build("java.nio.file.Path");
        private static final JavaType.Class FilesType = JavaType.Class.build("java.nio.file.Files");
        private static final String IOExceptionFqn = "java.io.IOException";
        private static final JavaType.Class IOExceptionType = JavaType.Class.build(IOExceptionFqn);
        private static final JavaType.Class StringType = JavaType.Class.build("java.lang.String");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            List<J.VariableDeclarations> fields = cd.getBody().getStatements().stream()
                    .filter(J.VariableDeclarations.class::isInstance)
                    .map(J.VariableDeclarations.class::cast)
                    .collect(Collectors.toList());
            if (fields.stream().anyMatch(it -> TypeUtils.hasElementType(it.getTypeAsClass(), TemporaryFolderFqn))) {
                List<Statement> newStatements = cd.getBody().getStatements().stream()
                        .map(this::convertTempFolderField)
                        .collect(Collectors.toList());

                cd = cd.withBody(cd.getBody().withStatements(newStatements));
            }

            return cd;
        }

        /**
         * Given a J.VariableDecls that looks like:
         * <pre>
         *     @Rule
         *     public TemporaryFolder folder = new TemporaryFolder();
         * </pre>
         * Turn it into:
         * <pre>
         *     @TempDir
         *     public File folder
         * </pre>
         * <p>
         * Any parameter that doesn't match that input is returned unaltered.
         * <p>
         * NOT a pure function. Notable side effects include:
         * Adding removing/imports as necessary.
         * Scheduling visitors to handle formatting
         * Scheduling visitors to update method invocations
         */
        private Statement convertTempFolderField(Statement statement) {
            if (!(statement instanceof J.VariableDeclarations)) {
                return statement;
            }
            J.VariableDeclarations field = (J.VariableDeclarations) statement;
            if (field.getTypeAsClass() == null || !field.getTypeAsClass().getFullyQualifiedName().equals(TemporaryFolderFqn)) {
                return field;
            }
            // filter out the @Rule annotation and add the @TempDir annotation

            List<J.Annotation> newAnnotations = Stream.concat(
                    Stream.of(new J.Annotation(randomId(), Space.EMPTY, Markers.EMPTY, TempDirIdent, null)),
                    field.getAnnotations().stream()
                            .filter(it -> !TypeUtils.hasElementType(it.getType(), RuleFqn)))
                    .collect(Collectors.toList());
            field = field.withAnnotations(newAnnotations);

            // Remove the initializing expression for "new TemporaryFolder()"
            List<J.VariableDeclarations.NamedVariable> newVars = field.getVariables().stream()
                    .map(it -> it.withInitializer(null))
                    .collect(Collectors.toList());
            field = field.withVariables(newVars);

            maybeAddImport(FileType);
            maybeAddImport(TempDirType);
            maybeRemoveImport(RuleFqn);
            maybeRemoveImport(TemporaryFolderFqn);
            newVars.forEach(fieldVar -> doAfterVisit(new ReplaceTemporaryFolderMethodsVisitor(fieldVar.getSimpleName())));

            return field;
        }

        /**
         * This visitor replaces these methods from TemporaryFolder with JUnit5-compatible alternatives:
         * <p>
         * File getRoot()
         * File newFile()
         * File newFile(String fileName)
         * File newFolder()
         * File newFolder(String... folderNames)
         * File newFolder(String folder)
         */
        private static class ReplaceTemporaryFolderMethodsVisitor extends JavaVisitor<ExecutionContext> {
            private final String fieldName;

            ReplaceTemporaryFolderMethodsVisitor(String fieldName) {
                this.fieldName = fieldName;
                setCursoringOn();
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!(m.getSelect() instanceof J.Identifier)) {
                    return m;
                }
                J.Identifier receiver = (J.Identifier) m.getSelect();
                if (receiver.getSimpleName().equals(fieldName) &&
                        m.getType() != null &&
                        TypeUtils.hasElementType(m.getType().getDeclaringType(), TemporaryFolderFqn)
                ) {
                    assert getCursor().getParent() != null;
                    List<Expression> args = m.getArguments();
                    // handle TemporaryFolder.newFile() and TemporaryFolder.newFile(String)
                    switch (m.getName().getSimpleName()) {
                        case "newFile":
                            if (args.size() == 1 && args.get(0) instanceof J.Empty) {
                                m = treeBuilder.buildSnippet(
                                        getCursor().getParent(),
                                        "File.createTempFile(\"junit\", null, " + fieldName + ");",
                                        FileType
                                ).get(0).withFormatting(Formatting.format(" "));
                            } else {
                                doAfterVisit(new AddNewFileFunctionVisitor());
                                m = treeBuilder.buildSnippet(
                                        getCursor().getParent(),
                                        "newFile(" + fieldName + ", " + args.get(0).printTrimmed() + ");",
                                        FileType, args.get(0).getType()
                                ).get(0).withFormatting(Formatting.format(" "));
                            }
                            break;
                        case "getRoot":
                            return J.Identifier.build(randomId(), fieldName, FileType, m.getFormatting());
                        case "newFolder":
                            if (args.size() == 1 && args.get(0) instanceof J.Empty) {
                                m = treeBuilder.buildSnippet(
                                        getCursor().getParent(),
                                        "Files.createTempDirectory(" + fieldName + ".toPath(), \"junit\").toFile();",
                                        FileType, FilesType, PathType
                                ).get(0).withFormatting(Formatting.format(" "));
                                maybeAddImport(FilesType);
                            } else {
                                doAfterVisit(new AddNewFolderFunctionVisitor());
                                String argsString = printArgs(m.getArguments());
                                m = treeBuilder.buildSnippet(
                                        getCursor().getParent(),
                                        "newFolder(" + fieldName + ", " + argsString + ");",
                                        FileType
                                ).get(0).withFormatting(Formatting.format(" "));
                            }
                            break;
                    }

                }
                return maybeAutoFormat(method, m, ctx);
            }

            /**
             * As of rewrite 5.5.0, J.MethodInvocation.Arguments.print() returns an empty String
             * Roll our own good-enough print() method here
             */
            private static String printArgs(List<Expression> args) {
                return args.stream().map(J::print).collect(Collectors.joining(","));
            }
        }

        /**
         * Adds a method like this one to the target class:
         * private File newFile(File root, String fileName) throws IOException {
         * File file = new File(root, fileName);
         * file.createNewFile();
         * return file;
         * }
         * <p>
         * This generated method is intended to be a substitute for TemporaryFolder.newFile(String)
         */
        private static class AddNewFileFunctionVisitor extends JavaIsoVisitor<ExecutionContext> {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                Stream<J.MethodDeclaration> methods = cd.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast);
                boolean methodAlreadyExists = methods
                        .anyMatch(m -> {
                            List<Statement> params = m.getParameters();

                            return m.getSimpleName().equals("newFile")
                                    && params.size() == 2
                                    && params.get(0).hasClassType(FileType)
                                    && params.get(1).hasClassType(StringType);
                        });
                if (!methodAlreadyExists) {
                    List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                    J.MethodDeclaration newFileMethod = treeBuilder.buildMethodDeclaration(
                            cd,
                            "private static File newFile(File root, String fileName) throws IOException {\n" +
                                    "    File file = new File(root, fileName);\n" +
                                    "    file.createNewFile();\n" +
                                    "    return file;\n" +
                                    "}\n",
                            FileType,
                            IOExceptionType);
                    newFileMethod = (J.MethodDeclaration) new AutoFormatVisitor<>().visit(newFileMethod, ctx, getCursor());
                    statements.add(newFileMethod);
                    maybeAddImport(FileType);
                    maybeAddImport(IOExceptionType);
                    cd = cd.withBody(cd.getBody().withStatements(statements));
                }
                return cd;
            }
        }

        /**
         * JUnit4 TemporaryFolder has a method called newFolder which returns a new folder located within a particular root directory.
         * There is no direct JUnit5 analogue for TemporaryFolder or its newFolder method.
         * This visitor adds a function called newFolder() to the test class it visits which provides the same functionality:
         * <p>
         * private static File newFolder(File root, String ... folders) throws IOException {
         * File result = new File(root, String.join("/", folders));
         * if(!result.mkdirs()) {
         * throw new IOException("Couldn't create folders " + root);
         * }
         * return result;
         * }
         */
        private static class AddNewFolderFunctionVisitor extends JavaIsoVisitor<ExecutionContext> {
            @Override
            public J.ClassDeclaration visitClassDecl(J.ClassDeclaration cd, ExecutionContext ctx) {
                Stream<J.MethodDeclaration> methods = cd.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast);
                boolean methodAlreadyExists = methods
                        .anyMatch(m -> {
                            List<Statement> params = m.getParameters();

                            return m.getSimpleName().equals("newFolder")
                                    && params.size() == 2
                                    && params.get(0).hasClassType(FileType)
                                    && params.get(1).hasClassType(StringType)
                                    && params.get(1) instanceof J.VariableDeclarations
                                    && ((J.VariableDeclarations) params.get(1)).getVarargs() != null;
                        });
                if (!methodAlreadyExists) {
                    List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                    J.MethodDeclaration newFolderMethod = treeBuilder.buildMethodDeclaration(
                            cd,
                            "private static File newFolder(File root, String ... folders) throws IOException {\n" +
                                    "    File result = new File(root, String.join(\"/\", folders));\n" +
                                    "    if(!result.mkdirs()) {\n" +
                                    "        throw new IOException(\"Couldn't create folders \" + root);\n" +
                                    "    }\n" +
                                    "    return result;\n" +
                                    "}",
                            FileType,
                            IOExceptionType);
                    newFolderMethod = (J.MethodDeclaration) new AutoFormatVisitor<>().visit(newFolderMethod, ctx, getCursor());
                    statements.add(newFolderMethod);
                    maybeAddImport(FileType);
                    maybeAddImport(IOExceptionType);
                    cd = cd.withBody(cd.getBody().withStatements(statements));
                }
                return cd;
            }
        }
    }
}