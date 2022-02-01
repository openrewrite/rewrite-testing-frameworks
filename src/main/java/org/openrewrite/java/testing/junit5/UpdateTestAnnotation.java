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
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.Comparator;

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

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            ChangeTestAnnotation cta = new ChangeTestAnnotation();
            J.MethodDeclaration m = (J.MethodDeclaration) cta.visitNonNull(method, ctx, getCursor().getParentOrThrow());
            if (m != method) {
                if (Boolean.FALSE.equals(TypeUtils.isOverride(m.getMethodType()))) {
                    m = (J.MethodDeclaration) new ChangeMethodAccessLevelVisitor<ExecutionContext>(new MethodMatcher(m), null)
                            .visitNonNull(m, ctx, getCursor().getParentOrThrow());
                }
                if (cta.expectedException != null) {
                    m = m.withTemplate(JavaTemplate.builder(this::getCursor, "Object o = () -> #{}").build(),
                            m.getCoordinates().replaceBody(),
                            m.getBody());

                    assert m.getBody() != null;
                    J.Lambda lambda = (J.Lambda) ((J.VariableDeclarations) m.getBody().getStatements().get(0))
                            .getVariables().get(0).getInitializer();

                    assert lambda != null;
                    lambda = lambda.withType(JavaType.Class.build("org.junit.jupiter.api.function.Executable"));

                    m = m.withTemplate(JavaTemplate.builder(this::getCursor,
                                    "assertThrows(#{any(java.lang.Class)}, #{any(org.junit.jupiter.api.function.Executable)});")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
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
                                    .build(),
                            m.getCoordinates().replaceBody(),
                            cta.expectedException, lambda);
                    maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");
                }
                if (cta.timeout != null) {
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
                            cta.timeout);
                    maybeAddImport("org.junit.jupiter.api.Timeout");
                }
                maybeAddImport("org.junit.jupiter.api.Test");
                maybeRemoveImport("org.junit.Test");
            }

            return super.visitMethodDeclaration(m, ctx);
        }

        private static class ChangeTestAnnotation extends JavaIsoVisitor<ExecutionContext> {
            @Nullable
            Expression expectedException;

            @Nullable
            Expression timeout;

            boolean found = false;

            @Override
            public J.Annotation visitAnnotation(J.Annotation a, ExecutionContext context) {
                if (!found && JUNIT4_TEST.matches(a)) {
                    // While unlikely, it's possible that a method has an inner class/lambda/etc. with methods that have test annotations
                    // Avoid considering any but the first test annotation found
                    found = true;
                    if (a.getArguments() != null) {
                        for (Expression arg : a.getArguments()) {
                            if (!(arg instanceof J.Assignment)) {
                                continue;
                            }
                            J.Assignment assign = (J.Assignment) arg;
                            String assignParamName = ((J.Identifier) assign.getVariable()).getSimpleName();
                            Expression e = assign.getAssignment();
                            if ("expected".equals(assignParamName)) {
                                expectedException = e;
                            } else if ("timeout".equals(assignParamName)) {
                                timeout = e;
                            }

                        }
                    }
                    a = a.withArguments(null)
                            .withType(JavaType.Class.build("org.junit.jupiter.api.Test"));
                }
                return a;
            }
        }
    }
}
