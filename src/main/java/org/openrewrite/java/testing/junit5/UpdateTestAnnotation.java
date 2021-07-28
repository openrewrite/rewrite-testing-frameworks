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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UpdateTestAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate JUnit 4 `@Test` annotations to JUnit5";
    }

    @Override
    public String getDescription() {
        return "Update usages of JUnit 4's `@org.junit.Test` annotation to JUnit5's `org.junit.jupiter.api.Test` annotation.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.Test");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpdateTestAnnotationVisitor();
    }

    private static class UpdateTestAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher JUNIT4_TEST = new AnnotationMatcher("@org.junit.Test");
        private static final String JUNIT4_TEST_ANNOTATION_ARGUMENTS = "junit4TestAnnotationArguments";

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            doAfterVisit(new ChangeType("org.junit.Test", "org.junit.jupiter.api.Test"));
            return super.visitCompilationUnit(cu, ctx);
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation ann = super.visitAnnotation(annotation, ctx);
            if (JUNIT4_TEST.matches(ann)) {
                getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance).putMessage(JUNIT4_TEST_ANNOTATION_ARGUMENTS,
                        ann.getArguments());
                ann = ann.withArguments(null);
            }
            return ann;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            if (m.getLeadingAnnotations().stream().anyMatch(JUNIT4_TEST::matches)) {
                // FIXME removing public modifiers requires access to the method super type to prevent assigning weaker access privileges
                //doAfterVisit(new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(method), null));

                List<Expression> arguments = getCursor().getMessage(JUNIT4_TEST_ANNOTATION_ARGUMENTS);
                if (arguments != null) {
                    doAfterVisit(new ChangeTestMethodBodyStep(m, arguments));
                }
            }

            return m;
        }

        private static class ChangeTestMethodBodyStep extends JavaIsoVisitor<ExecutionContext> {
            private final J.MethodDeclaration scope;
            private final List<Expression> arguments;

            private final JavaTemplate assertThrows = JavaTemplate.builder(this::getCursor, "assertThrows(#{any(java.lang.Class)}, #{any(org.junit.jupiter.api.function.Executable)});")
                    .javaParser(() -> JavaParser.fromJavaVersion()
                            .logCompilationWarningsAndErrors(true)
                            .dependsOn(
                                    "package org.junit.jupiter.api.function;" +
                                            "public interface Executable {" +
                                            "    void execute() throws Throwable;" +
                                            "}",
                                    "package org.junit.jupiter.api;" +
                                            "import org.junit.jupiter.api.function.Executable;" +
                                            "public class Assertions {" +
                                            "   public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {" +
                                            "      return null;" +
                                            "   }" +
                                            "}"
                            )
                            .build())
                    .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                    .build();

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

                            if ("expected".equals(assignParamName)) {
                                assert e instanceof J.FieldAccess;

                                m = m.withTemplate(JavaTemplate.builder(this::getCursor, "Object o = () -> #{}").build(),
                                        m.getCoordinates().replaceBody(),
                                        m.getBody());

                                assert m.getBody() != null;
                                J.Lambda lambda = (J.Lambda) ((J.VariableDeclarations) m.getBody().getStatements().get(0))
                                        .getVariables().get(0).getInitializer();

                                assert lambda != null;
                                lambda = lambda.withType(JavaType.Class.build("org.junit.jupiter.api.function.Executable"));

                                m = m.withTemplate(assertThrows,
                                        m.getCoordinates().replaceBody(),
                                        e, lambda);

                                maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");
                            } else if ("timeout".equals(assignParamName)) {
                                doAfterVisit(new AddTimeoutAnnotationStep(m, e));
                            }
                        }
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
                J.MethodDeclaration m = method;

                if (m.isScope(this.scope)) {
                    m = m.withTemplate(
                            JavaTemplate.builder(this::getCursor, "@Timeout(#{any(long)})")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(Collections.singletonList(Parser.Input.fromString(
                                                    "package org.junit.jupiter.api;\n" +
                                                            "import java.util.concurrent.TimeUnit;\n" +
                                                            "public @interface Timeout {\n" +
                                                            "    long value();\n" +
                                                            "    TimeUnit unit() default TimeUnit.SECONDS;\n" +
                                                            "}\n"
                                            )))
                                            .build())
                                    .imports("org.junit.jupiter.api.Timeout")
                                    .build(),
                            m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)),
                            expression
                    );
                    maybeAddImport("org.junit.jupiter.api.Timeout");
                }

                return m;
            }
        }
    }
}
