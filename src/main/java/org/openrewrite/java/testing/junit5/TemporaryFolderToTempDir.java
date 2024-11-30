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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return Preconditions.check(new UsesType<>("org.junit.rules.TemporaryFolder", false), new JavaVisitor<ExecutionContext>() {

            final AnnotationMatcher classRule = new AnnotationMatcher("@org.junit.ClassRule");
            final AnnotationMatcher rule = new AnnotationMatcher("@org.junit.Rule");


            private JavaParser.@Nullable Builder<?, ?> javaParser;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9");
                }
                return javaParser;

            }

            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = (J.CompilationUnit) super.visitCompilationUnit(cu, ctx);
                if (c != cu) {
                    c = (J.CompilationUnit) new ChangeType(
                            "org.junit.rules.TemporaryFolder", "java.io.File", true).getVisitor()
                            .visit(c, ctx);
                    maybeAddImport("java.io.File");
                    maybeAddImport("org.junit.jupiter.api.io.TempDir");
                    maybeRemoveImport("org.junit.ClassRule");
                    maybeRemoveImport("org.junit.Rule");
                    maybeRemoveImport("org.junit.rules.TemporaryFolder");
                }
                return c;
            }

            @Override
            public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations mv = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                if (!isRuleAnnotatedTemporaryFolder(mv)) {
                    return mv;
                }
                String fieldVars = mv.getVariables().stream()
                        .map(fv -> fv.withInitializer(null))
                        .map(it -> it.print(getCursor()))
                        .collect(Collectors.joining(","));
                String modifiers = mv.getModifiers().stream().map(it -> it.getType().name().toLowerCase()).collect(Collectors.joining(" "));
                mv = JavaTemplate.builder("@TempDir\n#{} File#{};")
                        .contextSensitive()
                        .imports("java.io.File", "org.junit.jupiter.api.io.TempDir")
                        .javaParser(javaParser(ctx))
                        .build()
                        .apply(
                                updateCursor(mv),
                                mv.getCoordinates().replace(),
                                modifiers,
                                fieldVars
                        );
                return mv;
            }

            private boolean isRuleAnnotatedTemporaryFolder(J.VariableDeclarations vd) {
                return TypeUtils.isOfClassType(vd.getTypeAsFullyQualified(), "org.junit.rules.TemporaryFolder") &&
                       vd.getLeadingAnnotations().stream().anyMatch(anno -> classRule.matches(anno) || rule.matches(anno));
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
                            doAfterVisit(new AddNewFolderMethod(mi));
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

            private J convertToNewFile(J.MethodInvocation mi, ExecutionContext ctx) {
                if (mi.getSelect() == null) {
                    return mi;
                }
                J tempDir = mi.getSelect().withType(JavaType.ShallowClass.build("java.io.File"));
                List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(Collectors.toList());
                if (args.isEmpty()) {
                    return JavaTemplate.builder("File.createTempFile(\"junit\", null, #{any(java.io.File)})")
                            .imports("java.io.File")
                            .javaParser(javaParser(ctx))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), tempDir);
                } else {
                    return JavaTemplate.builder("File.createTempFile(#{any(java.lang.String)}, null, #{any(java.io.File)})")
                            .imports("java.io.File")
                            .javaParser(javaParser(ctx))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), args.get(0), tempDir);
                }
            }
        });
    }

    private static class AddNewFolderMethod extends JavaIsoVisitor<ExecutionContext> {
        private final J.MethodInvocation methodInvocation;


        private JavaParser.@Nullable Builder<?, ?> javaParser;

        private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
            if (javaParser == null) {
                javaParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "junit-jupiter-api-5.9");
            }
            return javaParser;

        }

        public AddNewFolderMethod(J.MethodInvocation methodInvocation) {
            this.methodInvocation = methodInvocation;
        }

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

            Stream<J.MethodDeclaration> methods = cd.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast);

            JavaType.Method newFolderMethodDeclaration = methods
                    .filter(m -> {
                        List<Statement> params = m.getParameters();
                        return "newFolder".equals(m.getSimpleName()) &&
                               params.size() == 2 &&
                               hasClassType(params.get(0), "java.io.File") &&
                               hasClassType(params.get(1), "java.lang.String");
                    }).map(J.MethodDeclaration::getMethodType).filter(Objects::nonNull).findAny().orElse(null);

            if (newFolderMethodDeclaration == null) {
                cd = JavaTemplate.builder(
                                "private static File newFolder(File root, String... subDirs) throws IOException {\n" +
                                "    String subFolder = String.join(\"/\", subDirs);\n" +
                                "    File result = new File(root, subFolder);\n" +
                                "    if(!result.mkdirs()) {\n" +
                                "        throw new IOException(\"Couldn't create folders \" + root);\n" +
                                "    }\n" +
                                "    return result;\n" +
                                "}")
                        .contextSensitive()
                        .imports("java.io.File", "java.io.IOException")
                        .javaParser(javaParser(ctx))
                        .build()
                        .apply(updateCursor(cd), cd.getBody().getCoordinates().lastStatement());
                newFolderMethodDeclaration = ((J.MethodDeclaration) cd.getBody().getStatements().get(cd.getBody().getStatements().size() - 1)).getMethodType();
                maybeAddImport("java.io.File");
                maybeAddImport("java.io.IOException");
            }
            assert (newFolderMethodDeclaration != null);
            doAfterVisit(new TranslateNewFolderMethodInvocation(methodInvocation, newFolderMethodDeclaration));
            return cd;
        }

        private static class TranslateNewFolderMethodInvocation extends JavaVisitor<ExecutionContext> {
            J.MethodInvocation methodScope;
            JavaType.Method newMethodType;


            private JavaParser.@Nullable Builder<?, ?> javaParser;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9");
                }
                return javaParser;

            }

            public TranslateNewFolderMethodInvocation(J.MethodInvocation method, JavaType.Method newMethodType) {
                this.methodScope = method;
                this.newMethodType = newMethodType;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!mi.isScope(methodScope)) {
                    return mi;
                }
                if (mi.getSelect() != null) {
                    J tempDir = mi.getSelect().withType(JavaType.ShallowClass.build("java.io.File"));
                    List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(Collectors.toList());
                    if (args.isEmpty()) {
                        mi = JavaTemplate.builder("newFolder(#{any(java.io.File)}, \"junit\")")
                                .imports("java.io.File")
                                .javaParser(javaParser(ctx))
                                .build()
                                .apply(updateCursor(mi), mi.getCoordinates().replace(), tempDir);
                    } else if (args.size() == 1) {
                        mi = JavaTemplate.builder("newFolder(#{any(java.io.File)}, #{any(java.lang.String)})")
                                .imports("java.io.File")
                                .javaParser(javaParser(ctx))
                                .build()
                                .apply(
                                        updateCursor(mi),
                                        mi.getCoordinates().replace(),
                                        tempDir,
                                        args.get(0)
                                );
                    } else {
                        final StringBuilder sb = new StringBuilder("newFolder(#{any(java.io.File)}");
                        args.forEach(arg -> sb.append(", #{any(java.lang.String)}"));
                        sb.append(")");
                        List<Object> templateArgs = new ArrayList<>(args);
                        templateArgs.add(0, tempDir);
                        mi = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .imports("java.io.File")
                                .javaParser(javaParser(ctx))
                                .build()
                                .apply(
                                        updateCursor(mi),
                                        mi.getCoordinates().replace(),
                                        templateArgs.toArray()
                                );
                    }
                    mi = mi.withMethodType(newMethodType);
                    J.ClassDeclaration parentClass = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).getValue();
                    mi = mi.withName(mi.getName().withType(parentClass.getType()));
                }
                return mi;
            }
        }
    }
}
