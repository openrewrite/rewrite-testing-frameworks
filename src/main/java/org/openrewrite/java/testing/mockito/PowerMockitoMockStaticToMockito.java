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

import org.jetbrains.annotations.NotNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.*;
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
        private static final String POWER_MOCK_RUNNER = "org.powermock.modules.junit4.PowerMockRunner";
        private static final MethodMatcher MOCKED_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
        private static final MethodMatcher MOCKED_STATIC_CLOSE_MATCHER = new MethodMatcher("org.mockito.ScopedMock close(..)", true);
        private static final MethodMatcher MOCKITO_VERIFY_MATCHER = new MethodMatcher("org.mockito.Mockito verify(..)");
        private static final AnnotationMatcher PREPARE_FOR_TEST_MATCHER =
                new AnnotationMatcher("@org.powermock.core.classloader.annotations.PrepareForTest");
        private static final AnnotationMatcher RUN_WITH_POWER_MOCK_RUNNER_MATCHER =
                new AnnotationMatcher("@org.junit.runner.RunWith(" + POWER_MOCK_RUNNER + ".class)");
        private static final AnnotationMatcher tearDownAnnotationMatcher = new AnnotationMatcher("@" + AFTER_EACH);

        private static final MethodMatcher MOCKITO_WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
        private static final MethodMatcher MOCKITO_STATIC_METHOD_MATCHER = new MethodMatcher("org.mockito.Mockito *(..)");
        public static final String MOCKED_TYPES_FIELDS = "mockedTypesFields";

        private List<J.Identifier> getMockedTypesFields() {
            if (mockedTypesFields == null) {
                mockedTypesFields = new ArrayList<>();
            }
            return mockedTypesFields;
        }

        private List<J.Identifier> mockedTypesFields;

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
                    doAfterVisit(new RemoveAnnotationVisitor(PREPARE_FOR_TEST_MATCHER));
                } else if (RUN_WITH_POWER_MOCK_RUNNER_MATCHER.matches(annotation)) {
                    doAfterVisit(new RemoveAnnotationVisitor(RUN_WITH_POWER_MOCK_RUNNER_MATCHER));
                    maybeRemoveImport(POWER_MOCK_RUNNER);
                }
            }

            if (!prepareForTestAnnotations.isEmpty()) {
                List<Expression> mockedTypes = getMockedTypesFromPrepareForTestAnnotation(prepareForTestAnnotations);

                // If there are mocked types, add an empty tearDown() method if not yet present
                if (!mockedTypes.isEmpty()) {
                    classDecl = maybeAddTearDownMethodBody(classDecl, ctx);
                    classDecl = addFieldDeclarationForMockedTypes(classDecl, ctx, mockedTypes);
                }
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }

        @NotNull
        private J.ClassDeclaration addFieldDeclarationForMockedTypes(J.ClassDeclaration classDecl, ExecutionContext ctx, List<Expression> mockedTypes) {
            // Add field declarations of mocked types
            List<J.Identifier> mockedTypesIdentifiers = new ArrayList<>(mockedTypes.size());

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
                if (isFieldAlreadyDefined(classDecl.getBody(), mockedTypedFieldName)) {
                    continue;
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
                mockedTypesIdentifiers.add(mockField.getVariables().get(0).getName());
            }
            getCursor().putMessage(MOCKED_TYPES_FIELDS, mockedTypesIdentifiers);

            maybeAutoFormat(classDecl, classDecl.withPrefix(classDecl.getPrefix().
                    withWhitespace("")), classDecl.getName(), ctx, getCursor());
            maybeAddImport(MOCKED_STATIC);
            maybeAddImport("org.mockito.Mockito", "mockStatic");
            return classDecl;
        }

        private static boolean isFieldAlreadyDefined(J.Block classBody, String fieldName) {
            for (Statement statement : classBody.getStatements()) {
                if (statement instanceof J.VariableDeclarations) {
                    for (J.VariableDeclarations.NamedVariable namedVariable : ((J.VariableDeclarations) statement).getVariables()) {
                        if (namedVariable.getSimpleName().equals(fieldName)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @NotNull
        private J.ClassDeclaration maybeAddTearDownMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (hasTearDownMethod(classDecl)) {
                return classDecl;
            }
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
            return classDecl;
        }

        @Nullable
        private static J.MethodDeclaration getFirstTestMethod(List<J.MethodDeclaration> methods) {
            for (J.MethodDeclaration methodDeclaration : methods) {
                for (J.Annotation annotation : methodDeclaration.getLeadingAnnotations()) {
                    if (annotation.getSimpleName().equals("Test")) {
                        return methodDeclaration;
                    }
                }
            }
            return null;
        }

        private static boolean hasTearDownMethod(J.ClassDeclaration classDecl) {
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

        private static List<Expression> getMockedTypesFromPrepareForTestAnnotation(List<J.Annotation> prepareForTestAnnotations) {
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
            if (m.getAllAnnotations().stream().anyMatch(tearDownAnnotationMatcher::matches)) {
                List<J.Identifier> mockedTypesIdentifiers = getCursor().pollNearestMessage(MOCKED_TYPES_FIELDS);
                if (mockedTypesIdentifiers != null) {
                    for (J.Identifier mockedTypesField : mockedTypesIdentifiers) {
                        // Only add close method invocation if not already exists
                        J.Block methodBody = m.getBody();
                        if (methodBody == null || isStaticMockAlreadyClosed(mockedTypesField, methodBody)) {
                            continue;
                        }
                        m = m.withBody(methodBody.withTemplate(
                                JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                                                "#{any(org.mockito.MockedStatic)}.close();")
                                        .javaParser(() -> JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx, "mockito-core-3.*")
                                                .build())
                                        .build(),
                                methodBody.getCoordinates().lastStatement(),
                                mockedTypesField
                        ));
                    }
                    // The mocked field are needed for other visitors
                    setMockedTypesFields(mockedTypesIdentifiers);
                }
            }
            return m;
        }

        private void setMockedTypesFields(List<J.Identifier> mockedTypesFields) {
            this.mockedTypesFields = mockedTypesFields;
        }

        private static boolean isStaticMockAlreadyClosed(J.Identifier staticMock, J.Block methodBody) {
            return methodBody.getStatements().stream().filter(statement -> statement instanceof J.MethodInvocation)
                    .map(J.MethodInvocation.class::cast)
                    .filter(MOCKED_STATIC_CLOSE_MATCHER::matches)
                    .filter(methodInvocation -> methodInvocation.getSelect() instanceof J.Identifier)
                    .anyMatch(methodInvocation -> ((J.Identifier) methodInvocation.getSelect()).getSimpleName()
                            .equals(staticMock.getSimpleName()));
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (MOCKED_STATIC_MATCHER.matches(method) && isUnassignedInMethod()) {
                List<Expression> methodArguments = method.getArguments();
                JavaType.Method methodType = method.getMethodType();
                if (methodArguments.size() == 1 && !(methodArguments.get(0) instanceof J.Empty) &&
                        methodType != null) {
                    J.FieldAccess fieldAccess = (J.FieldAccess) methodArguments.get(0);
                    String className = fieldAccess.getTarget().toString();
                    J.Assignment assignment = method.withTemplate(
                            JavaTemplate.builder(this::getCursor, "mocked#{} = #{any(" +
                                    methodType.getReturnType() + ")};").build(),
                            method.getCoordinates().replace(),
                            className,
                            method
                    );
                    return assignment.withVariable(assignment.getVariable().withType(fieldAccess.getType()));
                }
            }
            if (MOCKITO_WHEN_MATCHER.matches(method)
                    || MOCKITO_VERIFY_MATCHER.matches(method)) {
                method = modifyWhenMethodInvocation(method);
            }
            return super.visitMethodInvocation(method, ctx);
        }

        @NotNull
        private J.MethodInvocation modifyWhenMethodInvocation(J.MethodInvocation whenMethod) {
            List<Expression> methodArguments = whenMethod.getArguments();
            List<J.MethodInvocation> staticMethodInvocationsInArguments = methodArguments.stream()
                    .filter(expression -> expression instanceof J.MethodInvocation).map(J.MethodInvocation.class::cast)
                    .filter(methodInvocation -> !MOCKITO_STATIC_METHOD_MATCHER.matches(methodInvocation))
                    .filter(methodInvocation -> methodInvocation.getMethodType() != null)
                    .filter(methodInvocation -> methodInvocation.getMethodType().hasFlags(Flag.Static))
                    .collect(Collectors.toList());
            if (staticMethodInvocationsInArguments.size() == 1) {
                J.MethodInvocation staticMI = staticMethodInvocationsInArguments.get(0);
                Expression lambdaInvocation;
                String declaringClassName = getDeclaringClassName(staticMI);
                if (staticMI.getArguments().stream().map(Expression::getType)
                        .noneMatch(Objects::nonNull)) {
                    // If the method invocation has no arguments
                    lambdaInvocation = staticMI.withTemplate(
                            JavaTemplate.builder(this::getCursor,
                                    declaringClassName + "::" + staticMI.getSimpleName()).build(),
                            staticMI.getCoordinates().replace()
                    );
                } else {
                    JavaType.Method methodType = staticMI.getMethodType();
                    if (methodType != null) {
                        lambdaInvocation = staticMI.withTemplate(
                                JavaTemplate.builder(this::getCursor,
                                        "() -> #{any(" + methodType.getReturnType() +
                                                ")}").build(),
                                staticMI.getCoordinates().replace(),
                                staticMI
                        );
                    } else {
                        // do nothing
                        lambdaInvocation = staticMI;
                    }
                }
                if (Collections.replaceAll(methodArguments, staticMI, lambdaInvocation)) {
                    J.Identifier mockedField = getFieldIdentifier("mocked" + declaringClassName);
                    whenMethod = whenMethod.withTemplate(
                            JavaTemplate.builder(
                                            this::getCursor,
                                            "#{any(org.mockito.MockedStatic)}.#{any(org.mockito.OngoingStubbing)}")
                                    .build(),
                            whenMethod.getCoordinates().replace(),
                            mockedField,
                            whenMethod
                    );
                    whenMethod = whenMethod.withArguments(methodArguments);
                }
            }
            return whenMethod;
        }

        @Nullable
        private String getDeclaringClassName(J.MethodInvocation mi) {
            JavaType.Method methodType = mi.getMethodType();
            if (methodType != null) {
                JavaType.FullyQualified declaringType = methodType.getDeclaringType();
                return declaringType.getClassName();
            }
            return null;
        }

        private boolean isUnassignedInMethod() {
            if (getCursor().getParentTreeCursor().getValue() instanceof J.Assignment) {
                return false;
            }
            Cursor declaringScope = getCursor().dropParentUntil(it -> it instanceof J.ClassDeclaration
                    || it instanceof J.MethodDeclaration || it == Cursor.ROOT_VALUE);
            return declaringScope.getValue() instanceof J.MethodDeclaration;
        }

        @Nullable
        private J.Identifier getFieldIdentifier(String name) {
            Optional<J.Identifier> optionalFieldIdentifier = getMockedTypesFields().stream().filter(identifier -> identifier.getSimpleName().equals(name)).findFirst();
            if (optionalFieldIdentifier.isPresent()) {
                return optionalFieldIdentifier.get();
            }
            J.ClassDeclaration cd = getCursor().dropParentUntil(it -> it instanceof J.ClassDeclaration).getValue();
            List<List<J.VariableDeclarations.NamedVariable>> collect = cd.getBody().getStatements().stream().filter(statement -> statement instanceof J.VariableDeclarations)
                    .map(variableDeclarations -> ((J.VariableDeclarations) variableDeclarations).getVariables()).collect(Collectors.toList());
            for (List<J.VariableDeclarations.NamedVariable> namedVariables : collect) {
                for (J.VariableDeclarations.NamedVariable namedVariable : namedVariables) {
                    if (namedVariable.getSimpleName().equals(name)) {
                        return namedVariable.getName();
                    }
                }
            }
            return null;
        }
    }
}