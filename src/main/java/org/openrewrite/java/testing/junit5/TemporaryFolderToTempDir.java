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

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Tree.randomId;

/**
 * Translates JUnit4's org.junit.rules.TemporaryFolder into JUnit 5's org.junit.jupiter.api.io.TempDir
 */
@AutoConfigure
public class TemporaryFolderToTempDir extends JavaIsoRefactorVisitor {

    private static final String RuleFqn = "org.junit.Rule";
    private static final String TemporaryFolderFqn = "org.junit.rules.TemporaryFolder";
    private static final JavaType.Class TempDirType = JavaType.Class.build("org.junit.jupiter.api.io.TempDir");
    private static final J.Ident TempDirIdent = J.Ident.build(randomId(), "TempDir", TempDirType, Formatting.EMPTY);
    private static final JavaType.Class FileType = JavaType.Class.build("java.io.File");
    private static final J.Ident FileIdent = J.Ident.build(randomId(), "File", FileType, Formatting.EMPTY);
    private static final JavaType.Class PathType = JavaType.Class.build("java.nio.file.Path");
    private static final JavaType.Class FilesType = JavaType.Class.build("java.nio.file.Files");
    private static final String IOExceptionFqn = "java.io.IOException";
    private static final JavaType.Class IOExceptionType = JavaType.Class.build(IOExceptionFqn);
    private static final JavaType.Class StringType = JavaType.Class.build("java.lang.String");

    @Override
    public J.ClassDecl visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl cd = super.visitClassDecl(classDecl);

        if (cd.getFields().stream().anyMatch(it -> TypeUtils.hasElementType(it.getTypeAsClass(), TemporaryFolderFqn))) {
            List<J> newStatements = cd.getBody().getStatements().stream()
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
    private J convertTempFolderField(J statement) {
        if (!(statement instanceof J.VariableDecls)) {
            return statement;
        }
        J.VariableDecls field = (J.VariableDecls) statement;
        if (field.getTypeAsClass() == null || !field.getTypeAsClass().getFullyQualifiedName().equals(TemporaryFolderFqn)) {
            return field;
        }
        // filter out the @Rule annotation and add the @TempDir annotation

        List<J.Annotation> newAnnotations = Stream.concat(
                Stream.of(new J.Annotation(randomId(), TempDirIdent, null, Formatting.EMPTY)),
                field.getAnnotations().stream()
                        .filter(it -> !TypeUtils.hasElementType(it.getType(), RuleFqn)))
                .collect(Collectors.toList());
        field = field.withAnnotations(newAnnotations);

        // Remove the initializing expression for "new TemporaryFolder()"
        List<J.VariableDecls.NamedVar> newVars = field.getVars().stream()
                .map(it -> it.withInitializer(null))
                // Remove the space from the suffix of the name that used to proceed the `=` bit of the assignment
                .map(it -> it.withName(it.getName().withFormatting(Formatting.EMPTY)))
                .collect(Collectors.toList());
        field = field.withVars(newVars);

        // Transfer the formatting from the old field Ident onto the new field Ident
        Formatting originalTypeFormatting = (field.getTypeExpr() == null) ? Formatting.EMPTY : field.getTypeExpr().getFormatting();
        field = field.withTypeExpr(FileIdent.withFormatting(originalTypeFormatting));

        maybeAddImport(FileType);
        maybeAddImport(TempDirType);
        maybeRemoveImport(RuleFqn);
        maybeRemoveImport(TemporaryFolderFqn);
        newVars.forEach(fieldVar -> andThen(new ReplaceTemporaryFolderMethods(fieldVar.getSimpleName())));
        andThen(new AutoFormat(field));

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
    private static class ReplaceTemporaryFolderMethods extends JavaRefactorVisitor {
        private final String fieldName;

        ReplaceTemporaryFolderMethods(String fieldName) {
            this.fieldName = fieldName;
            setCursoringOn();
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method) {
            J.MethodInvocation m = refactor(method, super::visitMethodInvocation);
            if (!(m.getSelect() instanceof J.Ident)) {
                return m;
            }
            J.Ident receiver = (J.Ident) m.getSelect();
            if (receiver.getSimpleName().equals(fieldName) &&
                    m.getType() != null &&
                    TypeUtils.hasElementType(m.getType().getDeclaringType(), TemporaryFolderFqn)
            ) {
                assert getCursor().getParent() != null;
                List<Expression> args = m.getArgs().getArgs();
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
                            andThen(new AddNewFileFunction());
                            m = treeBuilder.buildSnippet(
                                    getCursor().getParent(),
                                    "newFile(" + fieldName + ", " + args.get(0).printTrimmed() + ");",
                                    FileType, args.get(0).getType()
                            ).get(0).withFormatting(Formatting.format(" "));
                        }
                        break;
                    case "getRoot":
                        return J.Ident.build(randomId(), fieldName, FileType, m.getFormatting());
                    case "newFolder":
                        if (args.size() == 1 && args.get(0) instanceof J.Empty) {
                            m = treeBuilder.buildSnippet(
                                    getCursor().getParent(),
                                    "Files.createTempDirectory(" + fieldName + ".toPath(), \"junit\").toFile();",
                                    FileType, FilesType, PathType
                            ).get(0).withFormatting(Formatting.format(" "));
                            maybeAddImport(FilesType);
                        } else {
                            andThen(new AddNewFolderFunction());
                            String argsString = printArgs(m.getArgs().getArgs());
                            m = treeBuilder.buildSnippet(
                                    getCursor().getParent(),
                                    "newFolder(" + fieldName + ", " + argsString + ");",
                                    FileType
                            ).get(0).withFormatting(Formatting.format(" "));
                        }
                        break;
                }

            }
            return m;
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
    private static class AddNewFileFunction extends JavaIsoRefactorVisitor {
        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl cd) {
            boolean methodAlreadyExists = cd.getMethods().stream()
                    .anyMatch(m -> {
                        List<Statement> params = m.getParams().getParams();

                        return m.getSimpleName().equals("newFile")
                                && params.size() == 2
                                && params.get(0).hasClassType(FileType)
                                && params.get(1).hasClassType(StringType);
                    });
            if (!methodAlreadyExists) {
                List<J> statements = new ArrayList<>(cd.getBody().getStatements());
                J.MethodDecl newFileMethod = treeBuilder.buildMethodDeclaration(
                        cd,
                        "private static File newFile(File root, String fileName) throws IOException {\n" +
                                "    File file = new File(root, fileName);\n" +
                                "    file.createNewFile();\n" +
                                "    return file;\n" +
                                "}\n",
                        FileType,
                        IOExceptionType);
                statements.add(newFileMethod);
                maybeAddImport(FileType);
                maybeAddImport(IOExceptionType);
                cd = cd.withBody(cd.getBody().withStatements(statements));
                andThen(new AutoFormat(newFileMethod));
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
    private static class AddNewFolderFunction extends JavaIsoRefactorVisitor {
        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl cd) {
            boolean methodAlreadyExists = cd.getMethods().stream()
                    .anyMatch(m -> {
                        List<Statement> params = m.getParams().getParams();

                        return m.getSimpleName().equals("newFolder")
                                && params.size() == 2
                                && params.get(0).hasClassType(FileType)
                                && params.get(1).hasClassType(StringType)
                                && params.get(1) instanceof J.VariableDecls
                                && ((J.VariableDecls) params.get(1)).getVarargs() != null;
                    });
            if (!methodAlreadyExists) {
                List<J> statements = new ArrayList<>(cd.getBody().getStatements());
                J.MethodDecl newFolderMethod = treeBuilder.buildMethodDeclaration(
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
                statements.add(newFolderMethod);
                maybeAddImport(FileType);
                maybeAddImport(IOExceptionType);
                cd = cd.withBody(cd.getBody().withStatements(statements));
                andThen(new AutoFormat(newFolderMethod));
            }
            return cd;
        }
    }
}
