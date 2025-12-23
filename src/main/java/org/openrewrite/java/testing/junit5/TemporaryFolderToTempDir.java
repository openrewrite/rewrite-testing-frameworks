/*
 * Copyright 2024 the original author or authors.
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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class TemporaryFolderToTempDir extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use JUnit Jupiter `@TempDir`";
    }

    @Override
    public String getDescription() {
        return "Translates JUnit 4's `org.junit.rules.TemporaryFolder` into JUnit 5's `org.junit.jupiter.api.io.TempDir`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.junit.rules.TemporaryFolder", false),
                new TemporaryFolderToTempDirVisitor());
    }
}

class TemporaryFolderToTempDirVisitor extends JavaVisitor<ExecutionContext> {

    private static final String TEMPORARY_FOLDER = "org.junit.rules.TemporaryFolder";
    private static final String TEMP_DIR = "org.junit.jupiter.api.io.TempDir";
    private static final AnnotationMatcher CLASS_RULE = new AnnotationMatcher("@org.junit.ClassRule");
    private static final AnnotationMatcher RULE = new AnnotationMatcher("@org.junit.Rule");
    private static final MethodMatcher NEW_TEMPORARY_FOLDER = new MethodMatcher(TEMPORARY_FOLDER + " <init>()");
    private static final MethodMatcher NEW_TEMPORARY_FOLDER_WITH_ARG = new MethodMatcher(TEMPORARY_FOLDER + " <init>(java.io.File)");

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        J.CompilationUnit c = (J.CompilationUnit) super.visitCompilationUnit(cu, ctx);
        if (c != cu) {
            c = (J.CompilationUnit) new ChangeType(
                    "org.junit.rules.TemporaryFolder", "java.io.File", true).getVisitor()
                    .visit(c, ctx);
            maybeRemoveImport("org.junit.ClassRule");
            maybeAddImport("java.io.File");
            maybeRemoveImport("org.junit.Rule");
            maybeAddImport("java.nio.file.Files");
            maybeRemoveImport("org.junit.rules.TemporaryFolder");
            maybeAddImport("org.junit.jupiter.api.io.TempDir");
        }
        return c;
    }

    @Override
    public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
        J.VariableDeclarations mv = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
        if (!TypeUtils.isOfClassType(multiVariable.getTypeAsFullyQualified(), TEMPORARY_FOLDER)) {
            return mv;
        }
        mv = mv.withTypeExpression(toFileIdentifier(mv.getTypeExpression()));
        return (J.VariableDeclarations) new Annotated.Matcher("@org.junit.*Rule")
                .asVisitor(a -> JavaTemplate.builder("@TempDir")
                        .imports(TEMP_DIR)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                        .build()
                        .apply(a.getCursor(), a.getTree().getCoordinates().replace()))
                .visitNonNull(mv, ctx, getCursor().getParentOrThrow());
    }

    @Override
    public @Nullable J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        if (NEW_TEMPORARY_FOLDER.matches(newClass)) {
            return hasRuleAnnotation() ? null : JavaTemplate.builder("Files.createTempDirectory(\"junit\").toFile()")
                    .imports("java.nio.file.Files")
                    .build()
                    .apply(getCursor(), newClass.getCoordinates().replace());
        }
        if (NEW_TEMPORARY_FOLDER_WITH_ARG.matches(newClass)) {
            return hasRuleAnnotation() ? null : JavaTemplate.builder("Files.createTempDirectory(#{any(java.io.File)}.toPath(), \"junit\").toFile()")
                    .imports("java.nio.file.Files")
                    .build()
                    .apply(getCursor(), newClass.getCoordinates().replace(), newClass.getArguments().get(0));
        }
        return super.visitNewClass(newClass, ctx);
    }

    @Override
    public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
        updateCursor(mi);
        if (mi.getSelect() != null && mi.getMethodType() != null &&
                TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), "org.junit.rules.TemporaryFolder")) {
            switch (mi.getSimpleName()) {
                case "newFile":
                    return convertToNewFile(mi, ctx);
                case "newFolder":
                    doAfterVisit(new AddNewFolderOrFileMethod(mi, FileOrFolder.FOLDER, getCursor().firstEnclosing(J.ClassDeclaration.class)));
                    break;
                case "create":
                    //noinspection ConstantConditions
                    return null;
                case "getRoot":
                    return mi.getSelect().withPrefix(mi.getPrefix());
                default:
                    return mi;
            }
        }
        return mi;
    }

    private J.Identifier toFileIdentifier(TypeTree typeTree) {
        JavaType.ShallowClass fileType = JavaType.ShallowClass.build("java.io.File");
        return new J.Identifier(randomId(), typeTree.getPrefix(), Markers.EMPTY, emptyList(), fileType.getClassName(), fileType, null);
    }

    private boolean hasRuleAnnotation() {
        J.VariableDeclarations vd = getCursor().firstEnclosing(J.VariableDeclarations.class);
        if (vd == null) {
            return false;
        }
        return vd.getLeadingAnnotations().stream().anyMatch(anno -> CLASS_RULE.matches(anno) || RULE.matches(anno));
    }

    private J convertToNewFile(J.MethodInvocation mi, ExecutionContext ctx) {
        if (mi.getSelect() == null) {
            return mi;
        }
        List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(toList());
        if (args.isEmpty()) {
            J tempDir = mi.getSelect().withType(JavaType.ShallowClass.build("java.io.File"));
            return JavaTemplate.builder("File.createTempFile(\"junit\", null, #{any(java.io.File)})")
                    .imports("java.io.File")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), tempDir);
        }
        doAfterVisit(new AddNewFolderOrFileMethod(mi, FileOrFolder.FILE, getCursor().firstEnclosing(J.ClassDeclaration.class)));
        return mi;
    }
}

@RequiredArgsConstructor
class AddNewFolderOrFileMethod extends JavaIsoVisitor<ExecutionContext> {
    private final J.MethodInvocation methodInvocation;
    private final FileOrFolder fileOrFolder;
    private final J.ClassDeclaration enclosingClass;

    private boolean hasClassType(Statement j, @Nullable String classType) {
        if (classType == null) {
            return false;
        }

        if (!(j instanceof J.VariableDeclarations)) {
            return false;
        }

        J.VariableDeclarations variable = (J.VariableDeclarations) j;

        if (variable.getTypeExpression() == null) {
            return false;
        }

        return TypeUtils.isOfClassType(variable.getTypeExpression().getType(), classType);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
        if (!cd.isScope(enclosingClass)) {
            return cd;
        }
        JavaType.Method newMethodDeclaration = getMethodDeclaration(cd, fileOrFolder).orElse(null);

        if (newMethodDeclaration == null) {
            cd = JavaTemplate.builder(fileOrFolder.template)
                    .contextSensitive()
                    .imports("java.io.File", "java.io.IOException")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5"))
                    .build()
                    .apply(updateCursor(cd), cd.getBody().getCoordinates().lastStatement());
            newMethodDeclaration = ((J.MethodDeclaration) cd.getBody().getStatements().get(cd.getBody().getStatements().size() - 1)).getMethodType();
            maybeAddImport("java.io.File");
            maybeAddImport("java.io.IOException");
        }
        assert (newMethodDeclaration != null);
        doAfterVisit(new TranslateNewFolderOrFileMethodInvocation(methodInvocation, newMethodDeclaration, fileOrFolder));
        return cd;
    }

    private Optional<JavaType.Method> getMethodDeclaration(J.ClassDeclaration cd, FileOrFolder fileOrFolder) {
        return cd.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .filter(m -> {
                    List<Statement> params = m.getParameters();
                    return fileOrFolder.methodName.equals(m.getSimpleName()) &&
                            params.size() == 2 &&
                            hasClassType(params.get(0), "java.io.File") &&
                            hasClassType(params.get(1), "java.lang.String");
                }).map(J.MethodDeclaration::getMethodType).filter(Objects::nonNull).findAny();
    }
}

@RequiredArgsConstructor
class TranslateNewFolderOrFileMethodInvocation extends JavaVisitor<ExecutionContext> {
    final J.MethodInvocation methodScope;
    final JavaType.Method newMethodType;
    final FileOrFolder fileOrFolder;

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
        if (!mi.isScope(methodScope)) {
            return mi;
        }
        if (mi.getSelect() != null) {
            mi = fileOrFolder == FileOrFolder.FOLDER ? toNewFolder(mi, ctx) : toNewFile(mi, ctx);
            mi = mi.withMethodType(newMethodType);
            J.ClassDeclaration parentClass = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).getValue();
            mi = mi.withName(mi.getName().withType(parentClass.getType()));
        }
        return mi;
    }

    private J.MethodInvocation toNewFolder(J.MethodInvocation mi, ExecutionContext ctx) {
        J tempDir = mi.getSelect().withType(JavaType.ShallowClass.build("java.io.File"));
        List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(toList());
        if (args.isEmpty()) {
            return JavaTemplate.builder("newFolder(#{any(java.io.File)}, \"junit\")")
                    .imports("java.io.File")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                    .build()
                    .apply(updateCursor(mi), mi.getCoordinates().replace(), tempDir);
        }

        if (args.size() == 1) {
            return JavaTemplate.builder("newFolder(#{any(java.io.File)}, #{any(java.lang.String)})")
                    .imports("java.io.File")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                    .build()
                    .apply(
                            updateCursor(mi),
                            mi.getCoordinates().replace(),
                            tempDir,
                            args.get(0)
                    );
        }

        StringBuilder sb = new StringBuilder("newFolder(#{any(java.io.File)}");
        args.forEach(arg -> sb.append(", #{any(java.lang.String)}"));
        sb.append(")");
        List<Object> templateArgs = new ArrayList<>(args);
        templateArgs.add(0, tempDir);
        return JavaTemplate.builder(sb.toString())
                .contextSensitive()
                .imports("java.io.File")
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                .build()
                .apply(
                        updateCursor(mi),
                        mi.getCoordinates().replace(),
                        templateArgs.toArray()
                );
    }

    private J.MethodInvocation toNewFile(J.MethodInvocation mi, ExecutionContext ctx) {
        J tempDir = mi.getSelect().withType(JavaType.ShallowClass.build("java.io.File"));
        List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(toList());
        if (args.size() != 1) {
            return mi; // unexpected
        }
        return JavaTemplate.builder("newFile(#{any(java.io.File)}, #{any(java.lang.String)})")
                .imports("java.io.File")
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                .build()
                .apply(updateCursor(mi), mi.getCoordinates().replace(), tempDir, args.get(0));
    }
}

@RequiredArgsConstructor
enum FileOrFolder {
    FILE("newFile", "private static File newFile(File parent, String child) throws IOException {\n" +
            "    File result = new File(parent, child);\n" +
            "    result.createNewFile();\n" +
            "    return result;\n" +
            "}"),
    FOLDER("newFolder", "private static File newFolder(File root, String... subDirs) throws IOException {\n" +
            "    String subFolder = String.join(\"/\", subDirs);\n" +
            "    File result = new File(root, subFolder);\n" +
            "    if(!result.mkdirs()) {\n" +
            "        throw new IOException(\"Couldn't create folders \" + root);\n" +
            "    }\n" +
            "    return result;\n" +
            "}");

    final String methodName;
    final String template;
}
