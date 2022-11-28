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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
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
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.rules.TemporaryFolder");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        AnnotationMatcher classRule = new AnnotationMatcher("@org.junit.ClassRule");
        AnnotationMatcher rule = new AnnotationMatcher("@org.junit.Rule");

        Supplier<JavaParser> tempdirParser = () ->
                JavaParser.fromJavaVersion().dependsOn(Collections.singletonList(
                        Parser.Input.fromString("" +
                                                "package org.junit.jupiter.api.io;\n" +
                                                "public @interface TempDir {}")
                )).build();

        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                J.CompilationUnit c = (J.CompilationUnit) super.visitCompilationUnit(cu, executionContext);
                if (c != cu) {
                    doAfterVisit(new ChangeType("org.junit.rules.TemporaryFolder", "java.io.File", true));
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
                J.VariableDeclarations mv = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, executionContext);
                if (!isRuleAnnotatedTemporaryFolder(mv)) {
                    return mv;
                }
                String fieldVars = mv.getVariables().stream()
                        .map(fv -> fv.withInitializer(null))
                        .map(J::print).collect(Collectors.joining(","));
                String modifiers = mv.getModifiers().stream().map(it -> it.getType().name().toLowerCase()).collect(Collectors.joining(" "));
                mv = mv.withTemplate(
                        JavaTemplate.builder(this::getCursor, "@TempDir\n#{} File#{};")
                                .imports("java.io.File", "org.junit.jupiter.api.io.TempDir")
                                .javaParser(tempdirParser)
                                .build(),
                        mv.getCoordinates().replace(),
                        modifiers,
                        fieldVars);
                return mv;
            }

            private boolean isRuleAnnotatedTemporaryFolder(J.VariableDeclarations vd) {
                return TypeUtils.isOfClassType(vd.getTypeAsFullyQualified(), "org.junit.rules.TemporaryFolder")
                       && vd.getLeadingAnnotations().stream().anyMatch(anno -> classRule.matches(anno) || rule.matches(anno));
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);
                if (mi.getSelect() != null && mi.getMethodType() != null
                    && TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), "org.junit.rules.TemporaryFolder")) {
                    switch (mi.getSimpleName()) {
                        case "newFile":
                            return convertToNewFile(mi);
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

            private J convertToNewFile(J.MethodInvocation mi) {
                if (mi.getSelect() == null) {
                    return mi;
                }
                J tempDir = mi.getSelect().withType(JavaType.ShallowClass.build("java.io.File"));
                List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(Collectors.toList());
                if (args.isEmpty()) {
                    return mi.withTemplate(JavaTemplate.builder(this::getCursor, "File.createTempFile(\"junit\", null, #{any(java.io.File)})")
                            .imports("java.io.File").javaParser(tempdirParser).build(), mi.getCoordinates().replace(), tempDir);
                } else {
                    return mi.withTemplate(JavaTemplate.builder(this::getCursor, "File.createTempFile(#{any(java.lang.String)}, null, #{any(java.io.File)})")
                                    .imports("java.io.File").javaParser(tempdirParser).build(),
                            mi.getCoordinates().replace(), args.get(0), tempDir);
                }
            }
        };
    }

    private static class AddNewFolderMethod extends JavaIsoVisitor<ExecutionContext> {
        private final J.MethodInvocation methodInvocation;

        private final Supplier<JavaParser> tempdirParser = () -> JavaParser.fromJavaVersion().dependsOn(Collections.singletonList(
                Parser.Input.fromString("" +
                                        "package org.junit.jupiter.api.io;\n" +
                                        "public @interface TempDir {}")
        )).build();

        public AddNewFolderMethod(J.MethodInvocation methodInvocation) {
            this.methodInvocation = methodInvocation;
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
                        return "newFolder".equals(m.getSimpleName())
                               && params.size() == 2
                               && params.get(0).hasClassType(JavaType.ShallowClass.build("java.io.File"))
                               && params.get(1).hasClassType(JavaType.ShallowClass.build("java.lang.String"));
                    }).map(J.MethodDeclaration::getMethodType).filter(Objects::nonNull).findAny().orElse(null);

            if (newFolderMethodDeclaration == null) {
                cd = cd.withTemplate(JavaTemplate.builder(this::getCursor,
                        "private static File newFolder(File root, String... subDirs) throws IOException {\n" +
                        "    String subFolder = String.join(\"/\", subDirs);\n" +
                        "    File result = new File(root, subFolder);\n" +
                        "    if(!result.mkdirs()) {\n" +
                        "        throw new IOException(\"Couldn't create folders \" + root);\n" +
                        "    }\n" +
                        "    return result;\n" +
                        "}"
                ).imports("java.io.File", "java.io.IOException").javaParser(tempdirParser).build(), cd.getBody().getCoordinates().lastStatement());
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

            private final Supplier<JavaParser> tempdirParser = () -> JavaParser.fromJavaVersion().dependsOn(Collections.singletonList(
                    Parser.Input.fromString("" +
                                            "package org.junit.jupiter.api.io;\n" +
                                            "public @interface TempDir {}")
            )).build();

            public TranslateNewFolderMethodInvocation(J.MethodInvocation method, JavaType.Method newMethodType) {
                this.methodScope = method;
                this.newMethodType = newMethodType;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (!method.isScope(methodScope)) {
                    return method;
                }
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);
                if (mi.getSelect() != null) {
                    J tempDir = mi.getSelect().withType(JavaType.ShallowClass.build("java.io.File"));
                    List<Expression> args = mi.getArguments().stream().filter(arg -> !(arg instanceof J.Empty)).collect(Collectors.toList());
                    if (args.isEmpty()) {
                        mi = mi.withTemplate(JavaTemplate.builder(this::getCursor, "newFolder(#{any(java.io.File)}, \"junit\")")
                                .imports("java.io.File").javaParser(tempdirParser).build(), mi.getCoordinates().replace(), tempDir);
                    } else if (args.size() == 1) {
                        mi = mi.withTemplate(JavaTemplate.builder(this::getCursor, "newFolder(#{any(java.io.File)}, #{any(java.lang.String)})")
                                        .imports("java.io.File").javaParser(tempdirParser).build(),
                                mi.getCoordinates().replace(), tempDir, args.get(0));
                    } else {
                        final StringBuilder sb = new StringBuilder("newFolder(#{any(java.io.File)}");
                        args.forEach(arg -> sb.append(", #{any(java.lang.String)}"));
                        sb.append(")");
                        List<Object> templateArgs = new ArrayList<>(args);
                        templateArgs.add(0, tempDir);
                        mi = mi.withTemplate(JavaTemplate.builder(this::getCursor, sb.toString())
                                        .imports("java.io.File").javaParser(tempdirParser).build(),
                                mi.getCoordinates().replace(), templateArgs.toArray());
                    }
                    mi = mi.withMethodType(newMethodType);
                    J.ClassDeclaration parentClass = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).getValue();
                    mi = mi.withName(mi.getName().withType(parentClass.getType()));
                }
                return mi;
            }
        }
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }
}
