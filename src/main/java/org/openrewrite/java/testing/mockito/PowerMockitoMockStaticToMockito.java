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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

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

        private static final TestFrameworkInfo TESTNG_FRAMEWORK_INFO = new TestFrameworkInfo("BeforeMethod",
          "AfterMethod", "org.testng.annotations", "testng-7.7.1");

        private static final TestFrameworkInfo JUNIT_FRAMEWORK_INFO = new TestFrameworkInfo("BeforeEach", "AfterEach",
          "org.junit.jupiter.api", "junit-jupiter-api-5.9.2");

        private static final String MOCKED_STATIC = "org.mockito.MockedStatic";
        private static final String POWER_MOCK_RUNNER = "org.powermock.modules.junit4.PowerMockRunner";
        private static final MethodMatcher MOCKED_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
        private static final MethodMatcher MOCKED_STATIC_CLOSE_MATCHER = new MethodMatcher("org.mockito.ScopedMock close(..)", true);
        private static final MethodMatcher MOCKITO_VERIFY_MATCHER = new MethodMatcher("org.mockito.Mockito verify(..)");
        private static final AnnotationMatcher PREPARE_FOR_TEST_MATCHER =
                new AnnotationMatcher("@org.powermock.core.classloader.annotations.PrepareForTest");
        private static final AnnotationMatcher RUN_WITH_POWER_MOCK_RUNNER_MATCHER =
                new AnnotationMatcher("@org.junit.runner.RunWith(" + POWER_MOCK_RUNNER + ".class)");

        private static final MethodMatcher MOCKITO_WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
        private static final MethodMatcher MOCKITO_STATIC_METHOD_MATCHER = new MethodMatcher("org.mockito.Mockito *(..)");
        public static final String MOCKED_TYPES_FIELDS = "mockedTypesFields";

        private TestFrameworkInfo testFrameworkInfo;

        private static class TestFrameworkInfo {
            private final String setUpMethodAnnotationSignature;

            private final String setUpMethodAnnotation;

            private final String tearDownMethodAnnotationSignature;

            private final String tearDownMethodAnnotation;

            private final String additionalClasspathResource;

            private final String setUpImportToAdd;

            private final String tearDownImportToAdd;

            public TestFrameworkInfo(String setUpMethodAnnotationName, String tearDownMethodAnnotationName,
              String annotationPackage, String additionalClasspathResource) {
                this.setUpMethodAnnotation = "@" + setUpMethodAnnotationName;
                this.tearDownMethodAnnotation = "@" + tearDownMethodAnnotationName;

                this.setUpMethodAnnotationSignature = "@" + annotationPackage + "." + setUpMethodAnnotationName;
                this.tearDownMethodAnnotationSignature = "@" + annotationPackage + "." + tearDownMethodAnnotationName;

                this.setUpImportToAdd = annotationPackage + "." + setUpMethodAnnotationName;
                this.tearDownImportToAdd = annotationPackage + "." + tearDownMethodAnnotationName;

                this.additionalClasspathResource = additionalClasspathResource;
            }
        }

        private void initTestFrameworkInfo(boolean useTestNg) {
            if (useTestNg) {
                testFrameworkInfo = TESTNG_FRAMEWORK_INFO;
            } else {
                testFrameworkInfo = JUNIT_FRAMEWORK_INFO;
            }
        }

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

            boolean useTestNg = containsTestNgTestMethods(classDecl.getBody().getStatements().stream()
              .filter(statement -> statement instanceof J.MethodDeclaration)
              .map(J.MethodDeclaration.class::cast).collect(Collectors.toList()));
            initTestFrameworkInfo(useTestNg);

            if (!prepareForTestAnnotations.isEmpty()) {
                List<Expression> mockedTypes = getMockedTypesFromPrepareForTestAnnotation(prepareForTestAnnotations);

                // If there are mocked types, add empty setUp() and tearDown() methods if not yet present
                if (!mockedTypes.isEmpty()) {
                    classDecl = maybeAddSetUpMethodBody(classDecl, ctx);
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
        private J.ClassDeclaration maybeAddSetUpMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            return maybeAddMethodWithAnnotation(classDecl, ctx, "setUp",
              testFrameworkInfo.setUpMethodAnnotationSignature, testFrameworkInfo.setUpMethodAnnotation,
              testFrameworkInfo.additionalClasspathResource, testFrameworkInfo.setUpImportToAdd);
        }

        @NotNull
        private J.ClassDeclaration maybeAddMethodWithAnnotation(J.ClassDeclaration classDecl, ExecutionContext ctx,
          String methodName, String methodAnnotationSignature, String methodAnnotationToAdd,
          String additionalClasspathResource, String importToAdd) {
            if (hasMethodWithAnnotation(classDecl, new AnnotationMatcher(methodAnnotationSignature))) {
                return classDecl;
            }

            J.MethodDeclaration firstTestMethod = getFirstTestMethod(
              classDecl.getBody().getStatements().stream().filter(statement -> statement instanceof J.MethodDeclaration)
                .map(J.MethodDeclaration.class::cast).collect(Collectors.toList()));

            JavaCoordinates tearDownCoordinates = (firstTestMethod != null) ?
              firstTestMethod.getCoordinates().before() :
              classDecl.getBody().getCoordinates().lastStatement();
            classDecl = classDecl.withBody(classDecl.getBody()
              .withTemplate(
                JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                    methodAnnotationToAdd + " void " + methodName + "() {}")
                  .javaParser(() -> JavaParser.fromJavaVersion()
                    .classpathFromResources(ctx, additionalClasspathResource)
                    .build())
                  .imports(importToAdd)
                  .build(),
                tearDownCoordinates));
            maybeAddImport(importToAdd);
            return classDecl;
        }

        @NotNull
        private J.ClassDeclaration maybeAddTearDownMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            return maybeAddMethodWithAnnotation(classDecl, ctx, "tearDown",
              testFrameworkInfo.tearDownMethodAnnotationSignature, testFrameworkInfo.tearDownMethodAnnotation,
              testFrameworkInfo.additionalClasspathResource, testFrameworkInfo.tearDownImportToAdd);
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

        private static boolean containsTestNgTestMethods(List<J.MethodDeclaration> methods) {
            for (J.MethodDeclaration methodDeclaration : methods) {
                for (J.Annotation annotation : methodDeclaration.getAllAnnotations()) {
                    JavaType annotationType = annotation.getAnnotationType().getType();
                    if (annotationType instanceof JavaType.Class && ((JavaType.Class) annotationType)
                      .getFullyQualifiedName().equals("org.testng.annotations.Test")) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean hasMethodWithAnnotation(J.ClassDeclaration classDecl, AnnotationMatcher annotationMatcher) {
            for (Statement statement : classDecl.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) statement;
                    if (methodDeclaration.getAllAnnotations().stream()
                            .anyMatch(annotationMatcher::matches)) {
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

            AnnotationMatcher tearDownAnnotationMatcher = new AnnotationMatcher(testFrameworkInfo.tearDownMethodAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(tearDownAnnotationMatcher::matches)) {
                List<J.Identifier> mockedTypesIdentifiers = getCursor().pollNearestMessage(MOCKED_TYPES_FIELDS);
                if (mockedTypesIdentifiers == null) {
                    mockedTypesIdentifiers = getMockedTypesFields();
                }

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
                }
                // The mocked field are needed for other visitors
                setMockedTypesFields(mockedTypesIdentifiers);
                return m;
            }

            AnnotationMatcher setUpAnnotationMatcher = new AnnotationMatcher(
              testFrameworkInfo.setUpMethodAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(setUpAnnotationMatcher::matches)) {
                List<J.Identifier> mockedTypesIdentifiers = getCursor().pollNearestMessage(MOCKED_TYPES_FIELDS);
                if (mockedTypesIdentifiers == null) {
                    mockedTypesIdentifiers = getMockedTypesFields();
                }

                if (mockedTypesIdentifiers != null) {
                    for (J.Identifier mockedTypesField : mockedTypesIdentifiers) {
                        // Only add close method invocation if not already exists
                        J.Block methodBody = m.getBody();
                        if (methodBody == null || isStaticMockAlreadyOpened(mockedTypesField, methodBody)) {
                            continue;
                        }

                        String className = ((JavaType.Class) ((JavaType.Parameterized) mockedTypesField.getType()).getTypeParameters()
                          .get(0)).getClassName();
                        m = m.withBody(methodBody.withTemplate(
                          JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                              "mocked#{any(org.mockito.MockedStatic)} = mockStatic(#{}.class);")
                            .javaParser(() -> JavaParser.fromJavaVersion()
                              .classpathFromResources(ctx, "mockito-core-3.*")
                              .build())
                            .build(),
                          methodBody.getCoordinates().lastStatement(),
                          mockedTypesField,
                          className
                        ));
                    }
                }
                // The mocked field are needed for other visitors
                setMockedTypesFields(mockedTypesIdentifiers);
                return m;
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

        private static boolean isStaticMockAlreadyOpened(J.Identifier staticMock, J.Block methodBody) {
            return methodBody.getStatements().stream().filter(statement -> statement instanceof J.MethodInvocation)
              .map(J.MethodInvocation.class::cast)
              .filter(MOCKED_STATIC_MATCHER::matches)
              .filter(methodInvocation -> methodInvocation.getSelect() instanceof J.Identifier)
              .anyMatch(methodInvocation -> ((J.Identifier) methodInvocation.getSelect()).getSimpleName()
                .equals(staticMock.getSimpleName()));
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (MOCKITO_WHEN_MATCHER.matches(method)
                    || MOCKITO_VERIFY_MATCHER.matches(method)) {
                method = modifyWhenMethodInvocation(method);
            } else if (MOCKED_STATIC_MATCHER.matches(method)) {
                J.Assignment assignment = getCursor().firstEnclosing(J.Assignment.class);
                if (assignment != null) {
                    return super.visitMethodInvocation(method, ctx);
                }
                return null;
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
                    whenMethod = whenMethod.withSelect(mockedField);
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