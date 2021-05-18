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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UpdateTestAnnotation extends Recipe {

    private static List<Parser.Input> assertThrowsDependsOn(Expression e) {
        List<Parser.Input> dependsOn = new ArrayList<>(3);

        dependsOn.add(Parser.Input.fromString("package org.junit.jupiter.api.function;\n" +
                "public interface Executable {\n" +
                "    void execute() throws Throwable;\n" +
                "}"));

        dependsOn.add(Parser.Input.fromString("package org.junit.jupiter.api;\n" +
                "import org.junit.jupiter.api.function.Executable;\n" +
                "public class Assertions {\n" +
                "    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {\n" +
                "        return null;\n" +
                "    }\n" +
                "}"));

        if (e instanceof J.FieldAccess) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(((J.FieldAccess) e).getTarget().getType());
            if (type != null) {
                String source = (type.getPackageName().isEmpty() ? "" : "package " + type.getPackageName() + ";\n") +
                        "public class " + type.getClassName() + " extends Exception {}";
                dependsOn.add(Parser.Input.fromString(source));
            }
        }

        return dependsOn;
    }

    @Override
    public String getDisplayName() {
        return "Migrate JUnit4 `@Test` annotations to JUnit5";
    }

    @Override
    public String getDescription() {
        return "Update usages of JUnit4's `@org.junit.Test` annotation to JUnit5's `org.junit.jupiter.api.Test` annotation.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>("org.junit.Test");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpdateTestAnnotationVisitor();
    }

    private static class UpdateTestAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher JUNIT_4_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");
        private static final String JUNIT_4_TEST_ANNOTATION_ARGUMENTS = "junit4TestAnnotationArguments";

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            doAfterVisit(new ChangeType("org.junit.Test", "org.junit.jupiter.api.Test"));
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);
            if (JUNIT_4_TEST_ANNOTATION_MATCHER.matches(ann)) {
                getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance).putMessage(JUNIT_4_TEST_ANNOTATION_ARGUMENTS, ann.getArguments());
                ann = ann.withArguments(null);
            }
            return ann;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            if (m.getLeadingAnnotations().stream().anyMatch(JUNIT_4_TEST_ANNOTATION_MATCHER::matches)) {
                doAfterVisit(new ChangeTestAccessVisibilityStep(m));

                List<Expression> arguments = getCursor().getMessage(JUNIT_4_TEST_ANNOTATION_ARGUMENTS);
                if (arguments != null) {
                    doAfterVisit(new ChangeTestMethodBodyStep(m, arguments));
                }
            }

            return m;
        }

        private static class ChangeTestMethodBodyStep extends JavaIsoVisitor<ExecutionContext> {
            private final J.MethodDeclaration scope;
            private final List<Expression> arguments;

            public ChangeTestMethodBodyStep(J.MethodDeclaration scope, List<Expression> arguments) {
                this.scope = scope;
                this.arguments = arguments;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (m.isScope(scope) && m.getBody() != null) {
                    for (Expression arg : arguments) {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assign = (J.Assignment) arg;
                            String assignParamName = ((J.Identifier) assign.getVariable()).getSimpleName();
                            Expression e = assign.getAssignment();
                            if (assignParamName.equals("expected")) {
                                assert e instanceof J.FieldAccess;

                                List<Statement> statements = m.getBody() == null ? Collections.emptyList() : m.getBody().getStatements();
                                String strStatements = statements.stream().map(Tree::print).collect(Collectors.joining(";", "", ";"));

                                m = m.withTemplate(
                                        template("{ assertThrows(#{}, () -> { #{} }); }")
                                                .javaParser(JavaParser.fromJavaVersion()
                                                        .dependsOn(assertThrowsDependsOn(e))
                                                        .build())
                                                .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                                                .build(),
                                        m.getCoordinates().replaceBody(),
                                        e,
                                        strStatements
                                );
                                maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");
                            } else if (assignParamName.equals("timeout")) {
                                doAfterVisit(new AddTimeoutAnnotationStep(m, e));
                            }
                        }
                    }
                }
                return m;
            }
        }

        private static class ChangeTestAccessVisibilityStep extends JavaIsoVisitor<ExecutionContext> {
            private static final Predicate<J.Modifier> HAS_ACCESS_MODIFIER = mod -> mod.getType() == J.Modifier.Type.Private || mod.getType() == J.Modifier.Type.Public || mod.getType() == J.Modifier.Type.Protected;
            private final J.MethodDeclaration scope;

            public ChangeTestAccessVisibilityStep(J.MethodDeclaration scope) {
                this.scope = scope;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (m.isScope(scope)) {
                    // If we found the annotation, we change the visibility of the method to package (no access modifiers) and copy any comments to the method.
                    // Also need to format the method declaration because the previous visibility likely had formatting that is removed.
                    final List<Comment> modifierComments = new ArrayList<>();
                    List<J.Modifier> modifiers = ListUtils.map(m.getModifiers(), modifier -> {
                        if (HAS_ACCESS_MODIFIER.test(modifier)) {
                            modifierComments.addAll(modifier.getComments());
                            return null;
                        } else {
                            return modifier;
                        }
                    });
                    if (!modifierComments.isEmpty()) {
                        m = m.withComments(ListUtils.concatAll(m.getComments(), modifierComments));
                    }
                    if (m.getModifiers() != modifiers) {
                        m = maybeAutoFormat(m, m.withModifiers(modifiers), ctx, getCursor().dropParentUntil(J.class::isInstance));
                    }
                }
                return m;
            }
        }

        private static class AddTimeoutAnnotationStep extends JavaIsoVisitor<ExecutionContext> {
            private final J.MethodDeclaration scope;
            private final Expression expression;

            public AddTimeoutAnnotationStep(J.MethodDeclaration scope, Expression expression) {
                this.scope = scope;
                this.expression = expression;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.isScope(this.scope)) {
                    method = method.withTemplate(
                            template("@Timeout(#{})")
                                    .imports("org.junit.jupiter.api.Timeout")
                                    .build(),
                            method.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)),
                            expression
                    );
                    maybeAddImport("org.junit.jupiter.api.Timeout");
                }
                return method;
            }
        }

    }


}

