package org.openrewrite.java.testing.junit5;

import org.openrewrite.Formatting;
import org.openrewrite.Incubating;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaIsoRefactorVisitor;
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
@Incubating(since = "0.1.2")
// Keeping this commented out until this class is finished
//@AutoConfigure
public class TemporaryFolderToTempDir extends JavaIsoRefactorVisitor {

    private static final String RuleFqn = "org.junit.Rule";
    private static final String TemporaryFolderFqn = "org.junit.rules.TemporaryFolder";
    private static final JavaType.Class TempDirType = JavaType.Class.build("org.junit.jupiter.api.io.TempDir");
    private static final J.Ident TempDirIdent = J.Ident.build(randomId(), "TempDir", TempDirType, Formatting.EMPTY);
    private static final JavaType.Class FileType = JavaType.Class.build("java.io.File");
    private static final J.Ident FileIdent = J.Ident.build(randomId(), "File", FileType, Formatting.EMPTY);
    private static final String IOExceptionFqn = "java.io.IOException";
    private static final JavaType.Class IOExceptionType = JavaType.Class.build(IOExceptionFqn);
    private static final JavaType.Class StringType = JavaType.Class.build("java.lang.String");

    @Override
    public J.ClassDecl visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl cd = super.visitClassDecl(classDecl);

        if(cd.getFields().stream().filter(it -> TypeUtils.hasElementType(it.getTypeAsClass(), TemporaryFolderFqn)).findAny().isPresent()) {
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
     *
     * Any parameter that doesn't match that input is returned unaltered.
     *
     * NOT a pure function. Notable side effects include:
     *      Adding removing/imports as necessary.
     *      Scheduling visitors to handle formatting
     *      Scheduling visitors to update method invocations
     */
    private J convertTempFolderField(J statement) {
        if(!(statement instanceof  J.VariableDecls)) {
            return statement;
        }
        J.VariableDecls field = (J.VariableDecls) statement;
        if(field.getTypeAsClass() == null || !field.getTypeAsClass().getFullyQualifiedName().equals(TemporaryFolderFqn)) {
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
        newVars.stream().forEach(fieldVar -> andThen(new ReplaceTemporaryFolderMethods(fieldVar.getSimpleName())));
        andThen(new AutoFormat(field));

        return field;
    }

    /**
     * This visitor replaces these methods from TemporaryFolder with a JUnit5-compatible alternative:
     *
     * File newFile()
     * File newFile(String fileName)
     *
     * When complete it will also replace these TemporaryFolder methods:
     * File getRoot()
     * File newFolder()
     * File newFolder(String... folderNames)
     * File newFolder(String folder)
     *
     */
    private static class ReplaceTemporaryFolderMethods extends JavaIsoRefactorVisitor {
        private final String fieldName;
        ReplaceTemporaryFolderMethods(String fieldName) {
            this.fieldName = fieldName;
            setCursoringOn();
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method) {
            J.MethodInvocation m = super.visitMethodInvocation(method);
            if(!(m.getSelect() instanceof J.Ident)) {
                return m;
            }
            J.Ident receiver = (J.Ident) m.getSelect();
            if(receiver.getSimpleName().equals(fieldName) &&
                    m.getType() != null &&
                    TypeUtils.hasElementType(m.getType().getDeclaringType(), TemporaryFolderFqn)
            ) {
                assert getCursor().getParent() != null;
                List<Expression> args = m.getArgs().getArgs();
                // handle TemporaryFolder.newFile() and TemporaryFolder.newFile(String)
                if(m.getName().getSimpleName().equals("newFile")) {
                    if(args.size() == 1 && args.get(0) instanceof J.Empty) {
                        m = treeBuilder.buildSnippet(
                                getCursor().getParent(),
                                "File.createTempFile(\"junit\", null, " + fieldName + ");",
                                FileType
                        ).get(0).withFormatting(Formatting.format(" "));
                    } else {
                        andThen(new AddNewFileFunction());
                        m = treeBuilder.buildSnippet(
                                getCursor().getParent(),
                                "newFile(" + fieldName + ", "+ args.get(0).printTrimmed() +")",
                                FileType, args.get(0).getType()
                        ).get(0).withFormatting(Formatting.format(" "));
                    }
                }

            }
            return m;
        }
    }

    /**
     * Adds a method like this one to the target class:
     * private File newFile(File dir, String fileName) throws IOException {
     *     File file = new File(getRoot(), fileName);
     *     file.createNewFile();
     *     return file;
     * }
     *
     * This generated method is intended to be a substitute for TemporaryFolder.newFile(String)
     */
    private static class AddNewFileFunction extends JavaIsoRefactorVisitor {
        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl cd = super.visitClassDecl(classDecl);
            boolean methodAlreadyExists = cd.getMethods().stream()
                    .filter(m -> {
                                List<Statement> params = m.getParams().getParams();

                                return m.getSimpleName().equals("newFile")
                                        && params.size() == 2
                                        && params.get(0).hasClassType(FileType)
                                        && params.get(1).hasClassType(StringType);
                            })
                    .findAny().isPresent();
            if(!methodAlreadyExists) {
                List<J> statements = new ArrayList<>(cd.getBody().getStatements());
                J.MethodDecl newFileMethod = treeBuilder.buildMethodDeclaration(
                        cd,
                        "private File newFile(File dir, String fileName) throws IOException {\n" +
                        "    File file = new File(getRoot(), fileName);\n" +
                        "    file.createNewFile();\n" +
                        "    return file;\n" +
                        "}\n",
                        FileType,
                        IOExceptionType);
                statements.add(newFileMethod);
                cd = cd.withBody(cd.getBody().withStatements(statements));
                andThen(new AutoFormat(newFileMethod));
            }
            return cd;
        }
    }
}
