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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PowerMockitoMockStaticToMockito extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace `PowerMock.mockStatic()` with `Mockito.mockStatic()`";
    }

    @Override
    public String getDescription() {
        return "Replaces `PowerMockito.mockStatic()` by `Mockito.mockStatic()`. Removes " +
               "the `@PrepareForTest` annotation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PowerMockitoToMockitoVisitor();
    }

    private static class PowerMockitoToMockitoVisitor extends JavaVisitor<ExecutionContext> {
        private static final String AFTER_EACH = "org.junit.jupiter.api.AfterEach";
        private static final String MOCKED_STATIC = "org.mockito.MockedStatic";
        private static final MethodMatcher MOCKED_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
        private static final MethodMatcher MOCKED_STATIC_CLOSE_MATCHER = new MethodMatcher("org.mockito.ScopedMock close(..)", true);
        private final AnnotationMatcher tearDownAnnotationMatcher = new AnnotationMatcher("@" + AFTER_EACH);
        private static final AnnotationMatcher PREPARE_FOR_TEST_MATCHER =
                new AnnotationMatcher("@org.powermock.core.classloader.annotations.PrepareForTest");

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            // Add the classes of the arguments in the annotation @PrepareForTest as fields
            // e.g. `@PrepareForTest(Calendar.class)`
            // becomes
            // `private MockStatic mockedCalendar;`
            List<J.Annotation> prepareForTestAnnotations = new ArrayList<>();
            for (J.Annotation annotation : classDecl.getAllAnnotations()) {
                if (PREPARE_FOR_TEST_MATCHER.matches(annotation)) {
                    prepareForTestAnnotations.add(annotation);
                }
            }

            if (!prepareForTestAnnotations.isEmpty()) {
                List<Expression> mockedTypes = getMockedTypes(prepareForTestAnnotations);
                doAfterVisit(new RemoveAnnotationVisitor(PREPARE_FOR_TEST_MATCHER));

                // If there are mocked types, add an empty tearDown() method if not yet present
                if (!mockedTypes.isEmpty() && !hasTearDownMethod(classDecl)) {
                    J.MethodDeclaration firstTestMethod = getFirstTestMethod(classDecl.getBody().getStatements().stream().filter(statement -> statement instanceof J.MethodDeclaration)
                            .map(J.MethodDeclaration.class::cast).collect(Collectors.toList()));

                    // Tear down only makes sen
                    JavaCoordinates tearDownCoordinates = (firstTestMethod != null) ?
                            firstTestMethod.getCoordinates().before() :
                            classDecl.getBody().getCoordinates().lastStatement();
                    classDecl = classDecl.withBody(classDecl.getBody()
                            .withTemplate(
                                    JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                                                    "@AfterEach void tearDown() {}")
                                            .javaParser(() -> JavaParser.fromJavaVersion()
                                                    .classpathFromResources(ctx, "junit-jupiter-api-5.9.2")
                                                    .build())
                                            .imports(AFTER_EACH)
                                            .build(),
                                    tearDownCoordinates));
                    maybeAddImport(AFTER_EACH);
                }

                // Add field declarations of mocked types
                List<J.Identifier> mockedTypesFields = new ArrayList<>(mockedTypes.size());

                nextMockedType:
                for (Expression mockedType : mockedTypes) {
                    JavaType.Parameterized classType = TypeUtils.asParameterized(mockedType.getType());
                    if (classType == null) {
                        continue;
                    }
                    JavaType.FullyQualified fullyQualifiedMockedType = TypeUtils.asFullyQualified(classType.getTypeParameters().get(0));
                    if (fullyQualifiedMockedType == null) {
                        continue;
                    }
                    String classlessTypeName = fullyQualifiedMockedType.getClassName();
                    String mockedTypedFieldName = "mocked" + classlessTypeName;
                    for (Statement statement : classDecl.getBody().getStatements()) {
                        if (statement instanceof J.VariableDeclarations) {
                            for (J.VariableDeclarations.NamedVariable namedVariable : ((J.VariableDeclarations) statement).getVariables()) {
                                if (namedVariable.getSimpleName().equals(mockedTypedFieldName)) {
                                    continue nextMockedType;
                                }
                            }
                        }
                    }
                    classDecl = classDecl.withBody(classDecl.getBody()
                            .withTemplate(
                                    JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                                                    "private MockedStatic<#{}> mocked#{};")
                                            .javaParser(() -> JavaParser.fromJavaVersion()
                                                    .classpathFromResources(ctx, "mockito-core-3.12.4")
                                                    .build())
                                            .staticImports("org.mockito.Mockito.mockStatic")
                                            .imports(MOCKED_STATIC)
                                            .build(),
                                    classDecl.getBody().getCoordinates().firstStatement(),
                                    classlessTypeName,
                                    classlessTypeName
                            )

                    );

                    J.VariableDeclarations mockField = (J.VariableDeclarations) classDecl.getBody().getStatements().get(0);
                    mockedTypesFields.add(mockField.getVariables().get(0).getName());
                }
                getCursor().putMessage("mockedTypesFields", mockedTypesFields);

                maybeAutoFormat(classDecl, classDecl.withPrefix(classDecl.getPrefix().
                        withWhitespace("")), classDecl.getName(), ctx, getCursor());
                maybeAddImport(MOCKED_STATIC);
                maybeAddImport("org.mockito.Mockito", "mockStatic");
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }

        @Nullable
        private J.MethodDeclaration getFirstTestMethod(List<J.MethodDeclaration> methods) {
            for (J.MethodDeclaration methodDeclaration : methods) {
                for (J.Annotation annotation : methodDeclaration.getLeadingAnnotations()) {
                    if (annotation.getSimpleName().equals("Test")) {
                        return methodDeclaration;
                    }
                }
            }
            return null;
        }

        private boolean hasTearDownMethod(J.ClassDeclaration classDecl) {
            for (Statement statement : classDecl.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) statement;
                    if (methodDeclaration.getAllAnnotations().stream()
                            .anyMatch(tearDownAnnotationMatcher::matches)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static List<Expression> getMockedTypes(List<J.Annotation> prepareForTestAnnotations) {
            List<Expression> mockedTypes = new ArrayList<>();
            for (J.Annotation prepareForTest : prepareForTestAnnotations) {
                if (prepareForTest != null && prepareForTest.getArguments() != null) {
                    mockedTypes.addAll(ListUtils.flatMap(prepareForTest.getArguments(), a -> {
                        if (a instanceof J.NewArray && ((J.NewArray) a).getInitializer() != null) {
                            // case `@PrepareForTest( {Object1.class, Object2.class ...} )`
                            return ((J.NewArray) a).getInitializer();
                        } else if (a instanceof J.Assignment && ((J.NewArray) ((J.Assignment) a).getAssignment()).getInitializer() != null) {
                            // case `@PrepareForTest( value = {Object1.class, Object2.class ...} }`
                            return ((J.NewArray) ((J.Assignment) a).getAssignment()).getInitializer();
                        }
                        return null;
                    }));
                }
            }
            return mockedTypes;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);

            // Only the @AfterEach or @AfterMethod is relevant
            for (J.Annotation annotation : m.getAllAnnotations()) {
                if (tearDownAnnotationMatcher.matches(annotation)) {
                    List<J.Identifier> mockedTypesFields = getCursor().pollNearestMessage("mockedTypesFields");
                    if (mockedTypesFields != null) {
                        nextMockedTypeField:
                        for (J.Identifier mockedTypesField : mockedTypesFields) {
                            // Only add close method invocation if not already exists
                            if (m.getBody() != null) {
                                for (Statement statement : m.getBody().getStatements()) {
                                    if (statement instanceof J.MethodInvocation) {
                                        J.MethodInvocation mStatement = (J.MethodInvocation) statement;
                                        if (MOCKED_STATIC_CLOSE_MATCHER.matches(mStatement)) {
                                            if(mStatement.getSelect() instanceof J.Identifier &&
                                               ((J.Identifier) mStatement.getSelect()).getSimpleName().equals(mockedTypesField.getSimpleName())) {
                                                continue nextMockedTypeField;
                                            }
                                        }
                                    }
                                }
                                m = m.withBody(m.getBody().withTemplate(
                                        JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                                                        "#{any(org.mockito.MockedStatic)}.close();")
                                                .javaParser(() -> JavaParser.fromJavaVersion()
                                                        .classpathFromResources(ctx, "mockito-core-3.*")
                                                        .build())
                                                .build(),
                                        m.getBody().getCoordinates().lastStatement(),
                                        mockedTypesField
                                ));
                            }
                        }
                    }
                    return m;
                }
            }
            return m;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            if (MOCKED_STATIC_MATCHER.matches(method) && !(getCursor().getParentTreeCursor().getValue() instanceof J.Assignment)) {
                Cursor declaringScope = getCursor().dropParentUntil(it -> it instanceof J.ClassDeclaration
                                                                          || it instanceof J.MethodDeclaration || it == Cursor.ROOT_VALUE);
                if (declaringScope.getValue() instanceof J.MethodDeclaration) {
                    List<Expression> methodArguments = method.getArguments();
                    if (methodArguments.size() == 1 && !(methodArguments.get(0) instanceof J.Empty) &&
                        method.getMethodType() != null) {
                        J.FieldAccess fieldAccess = (J.FieldAccess) methodArguments.get(0);
                        String className = fieldAccess.getTarget().toString();
                        J.Assignment assignment = method.withTemplate(
                                JavaTemplate.builder(this::getCursor, "mocked#{} = #{any(" +
                                                                      method.getMethodType().getReturnType() + ")};").build(),
                                method.getCoordinates().replace(),
                                className,
                                method
                        );
                        return assignment.withVariable(assignment.getVariable().withType(fieldAccess.getType()));
                    }
                }
            }
            return super.visitMethodInvocation(method, executionContext);
        }
    }
}
