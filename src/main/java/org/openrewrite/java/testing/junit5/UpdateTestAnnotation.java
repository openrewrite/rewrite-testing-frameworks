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
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class UpdateTestAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Update Test Annotation";
    }

    @Override
    public String getDescription() {
        return "Update JUnit4's org.junit.Test to JUnit5's org.junit.jupiter.api.Test annotation.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpdateTestAnnotationVisitor();
    }

    private static class UpdateTestAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            doAfterVisit(new ChangeType("org.junit.Test", "org.junit.jupiter.api.Test"));
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            boolean changed = false;
            List<J.Annotation> annotations = new ArrayList<>(m.getAllAnnotations());
            for (int i = 0, annotationsSize = annotations.size(); i < annotationsSize; i++) {

                J.Annotation a = annotations.get(i);
                if (TypeUtils.isOfClassType(a.getType(), "org.junit.Test")) {
                    //If we found the annotation, we change the visibility of the method to package, any associated modifier comments are copied to the method
                    final List<Comment> modifierComments = new ArrayList<>();
                    List<J.Modifier> modifiers = ListUtils.map(m.getModifiers(), mod -> {
                        J.Modifier.Type modifierType = mod.getType();
                        if (modifierType == J.Modifier.Type.Protected || modifierType == J.Modifier.Type.Private ||
                                modifierType == J.Modifier.Type.Public) {
                            modifierComments.addAll(mod.getComments());
                            return null;
                        }
                        return mod;
                    });
                    if (!modifierComments.isEmpty()) {
                        m = m.withComments(ListUtils.concatAll(m.getComments(), modifierComments));
                    }
                    if (m.getModifiers() != modifiers) {
                        m = maybeAutoFormat(m, m.withModifiers(modifiers), ctx, getCursor().dropParentUntil(J.class::isInstance));
                    }

                    annotations.set(i, a.withArguments(null));
                    if (a.getArguments() == null) {
                        continue;
                    }
                    List<Expression> args = a.getArguments();
                    for (Expression arg : args) {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assign = (J.Assignment) arg;
                            String assignParamName = ((J.Identifier) assign.getVariable()).getSimpleName();
                            Expression e = assign.getAssignment();
                            if (m.getBody() == null) {
                                continue;
                            }
                            if (assignParamName.equals("expected")) {
                                assert e instanceof J.FieldAccess;

                                List<Statement> statements = m.getBody().getStatements();
                                String strStatements = statements.stream().map(Statement::print)
                                        .collect(Collectors.joining(";", "", ";"));
                                m = m.withTemplate(
                                        template("{ assertThrows(#{}, () -> {#{}}); }")
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
                                doAfterVisit(new AddTimeoutAnnotation(m, e));
                            }
                        }
                        changed = true;
                    }
                }
            }

            if (changed) {
                m = m.withLeadingAnnotations(annotations);
            }
            return m;
        }

        private static class AddTimeoutAnnotation extends JavaIsoVisitor<ExecutionContext> {
            private final J.MethodDeclaration methodDeclaration;
            private final Expression expression;

            public AddTimeoutAnnotation(J.MethodDeclaration methodDeclaration, Expression expression) {
                this.methodDeclaration = methodDeclaration;
                this.expression = expression;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (!method.isScope(this.methodDeclaration)) {
                    return method;
                }
                method = method.withTemplate(
                        template("@Timeout(#{})")
                                .imports("org.junit.jupiter.api.Timeout")
                                .build(),
                        method.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)),
                        expression
                );
                maybeAddImport("org.junit.jupiter.api.Timeout");
                return method;
            }
        }
    }

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
            JavaType.Class type = TypeUtils.asClass(((J.FieldAccess) e).getTarget().getType());
            if (type != null) {
                String source = (type.getPackageName().isEmpty() ? "" : "package " + type.getPackageName() + ";\n") +
                        "public class " + type.getClassName() + " extends Exception {}";
                dependsOn.add(Parser.Input.fromString(source));
            }
        }

        return dependsOn;
    }
}
