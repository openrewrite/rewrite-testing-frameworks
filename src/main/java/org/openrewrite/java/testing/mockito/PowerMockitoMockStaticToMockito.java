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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.NonNull;
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
        private static final String MOCKED_STATIC = "org.mockito.MockedStatic";
        private static final String POWER_MOCK_RUNNER = "org.powermock.modules.junit4.PowerMockRunner";
        private static final String POWER_MOCK_CONFIG = "org.powermock.configuration.PowerMockConfiguration";
        private static final String POWER_MOCK_TEST_CASE = "org.powermock.modules.testng.PowerMockTestCase";
        private static final MethodMatcher MOCKED_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
        private static final MethodMatcher MOCKED_STATIC_CLOSE_MATCHER = new MethodMatcher("org.mockito.ScopedMock close(..)", true);
        private static final MethodMatcher MOCKITO_VERIFY_MATCHER = new MethodMatcher("org.mockito.Mockito verify(..)");
        private static final MethodMatcher MOCKITO_WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
        private static final MethodMatcher MOCKITO_STATIC_METHOD_MATCHER = new MethodMatcher("org.mockito..* *(..)");
        private static final AnnotationMatcher PREPARE_FOR_TEST_MATCHER =
          new AnnotationMatcher("@org.powermock.core.classloader.annotations.PrepareForTest");
        private static final AnnotationMatcher RUN_WITH_POWER_MOCK_RUNNER_MATCHER =
          new AnnotationMatcher("@org.junit.runner.RunWith(" + POWER_MOCK_RUNNER + ".class)");
        private static final String MOCKED_TYPES_FIELDS = "mockedTypesFields";
        private static final String MOCK_STATIC_INVOCATIONS = "mockStaticInvocationsByClassName";
        private static final MethodMatcher DYNAMIC_WHEN_METHOD_MATCHER = new MethodMatcher("org.mockito.Mockito when(java.lang.Class, String, ..)");
        private String setUpMethodAnnotationSignature;
        private String setUpMethodAnnotation;
        private String tearDownMethodAnnotationSignature;
        private String tearDownMethodAnnotation;
        private String additionalClasspathResource;
        private String setUpImportToAdd;
        private String tearDownImportToAdd;
        private String tearDownMethodAnnotationParameters = "";
        private Map<J.Identifier, Expression> mockedTypesFields;

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

            // Remove the extension of class PowerMockConfiguration
            classDecl = removeExtension(classDecl, POWER_MOCK_CONFIG);
            // Remove teh extension of class PowerMockTestCase
            classDecl = removeExtension(classDecl, POWER_MOCK_TEST_CASE);

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

            findMockStaticInvocations(classDecl);

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

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);

            AnnotationMatcher tearDownAnnotationMatcher = new AnnotationMatcher(tearDownMethodAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(tearDownAnnotationMatcher::matches)) {
                // Add close statements to the static mocks in the tear down method
                for (Map.Entry<J.Identifier, Expression> mockedTypesField : getMockedTypesFields().entrySet()) {
                    // Only add close method invocation if not already exists
                    J.Block methodBody = m.getBody();
                    if (methodBody == null || isStaticMockAlreadyClosed(mockedTypesField.getKey(), methodBody)) {
                        continue;
                    }
                    m = m.withBody(methodBody.withTemplate(
                      JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                          "#{any(org.mockito.MockedStatic)}.closeOnDemand();")
                        .javaParser(() -> JavaParser.fromJavaVersion()
                          .classpathFromResources(ctx, "mockito-core-3.*")
                          .build())
                        .build(),
                      methodBody.getCoordinates().lastStatement(),
                      mockedTypesField.getKey()
                    ));
                }
                return m;
            }

            AnnotationMatcher setUpAnnotationMatcher = new AnnotationMatcher(
              setUpMethodAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(setUpAnnotationMatcher::matches)) {
                // Move the mockStatic method to the setUp method
                Map<String, J.MethodInvocation> mockStaticInvocations = getCursor().getNearestMessage(MOCK_STATIC_INVOCATIONS);

                if (mockStaticInvocations != null) {
                    for (Map.Entry<J.Identifier, Expression> mockedTypesFieldEntry : getMockedTypesFields().entrySet()) {
                        // Only add close method invocation if not already exists
                        J.Block methodBody = m.getBody();
                        if (methodBody == null || isStaticMockAlreadyOpened(mockedTypesFieldEntry.getKey(), methodBody)) {
                            continue;
                        }

                        String className = mockedTypesFieldEntry.getValue().toString();
                        J.MethodInvocation methodInvocation = mockStaticInvocations.get(className);
                        if (methodInvocation != null) {
                            m = m.withBody(methodBody.withTemplate(
                              JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                                  "mocked#{any(org.mockito.MockedStatic)} = #{any(org.mockito.Mockito)};")
                                .javaParser(() -> JavaParser.fromJavaVersion()
                                  .classpathFromResources(ctx, "mockito-core-3.*")
                                  .build())
                                .build(),
                              methodBody.getCoordinates().firstStatement(),
                              mockedTypesFieldEntry.getKey(),
                              methodInvocation
                            ));
                        }
                    }
                }
            }
            return m;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
            if (DYNAMIC_WHEN_METHOD_MATCHER.matches(mi)) {
                return modifyDynamicWhenMethodInvocation(mi);
            }

            if (MOCKITO_WHEN_MATCHER.matches(mi) || MOCKITO_VERIFY_MATCHER.matches(mi)) {
                return modifyWhenMethodInvocation(mi);
            }

            if (MOCKED_STATIC_MATCHER.matches(mi)) {
                J.Assignment assignment = getCursor().firstEnclosing(J.Assignment.class);
                if (assignment == null) {
                    //noinspection DataFlowIssue
                    return null;
                }
            }
            return mi;
        }

        private J.MethodInvocation modifyDynamicWhenMethodInvocation(J.MethodInvocation method) {
            // Example
            // Mockito.mockStatic(Calendar.class, "getInstance")
            // is modified to
            // Mockito.mockStatic(Calendar.getInstance())
            List<Expression> arguments = method.getArguments();
            String declaringClassName = ((J.FieldAccess) arguments.get(0)).getTarget().toString();
            arguments.remove(0);
            J.Literal calledMethod = (J.Literal)arguments.get(0);
            arguments.remove(0);
            String stringOfArguments = arguments.stream().map(argument -> argument.toString()).collect(Collectors.joining(","));
            method = method.withTemplate(
                JavaTemplate.builder(this::getCursor,
                    "() -> #{}.#{}(#{})")
                  .javaParser(() -> JavaParser.fromJavaVersion()
                    .build())
                  .build(),
                method.getCoordinates().replaceArguments(),
              declaringClassName,
                calledMethod.getValue().toString(),
                stringOfArguments
              );
            J.Identifier mockedField = getFieldIdentifier("mocked" + declaringClassName);
            method = method.withSelect(mockedField);

            return method;
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
            return classDecl.getBody().getStatements().stream()
              .filter(statement -> statement instanceof J.MethodDeclaration)
              .map(J.MethodDeclaration.class::cast)
              .map(J.MethodDeclaration::getAllAnnotations)
              .flatMap(Collection::stream)
              .anyMatch(annotationMatcher::matches);
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
                        } else if (a instanceof J.FieldAccess) {
                            // case `@PrepareForTest(Object1.class)`
                            return a;
                        }
                        return null;
                    }));
                }
            }
            return mockedTypes;
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

        @NotNull
        private J.ClassDeclaration removeExtension(J.ClassDeclaration classDecl, String extensionFQN) {
            TypeTree extension = classDecl.getExtends();
            if (extension != null && TypeUtils.isAssignableTo(extensionFQN, extension.getType())) {
                classDecl = classDecl.withExtends(null);
                maybeRemoveImport(extensionFQN);
            }
            return classDecl;
        }

        private void initTestFrameworkInfo(boolean useTestNg) {
            String setUpMethodAnnotationName;
            String tearDownMethodAnnotationName;
            String annotationPackage;

            if (!useTestNg) {
                setUpMethodAnnotationName = "BeforeEach";
                tearDownMethodAnnotationName = "AfterEach";
                annotationPackage = "org.junit.jupiter.api";
                additionalClasspathResource = "junit-jupiter-api-5.9.2";
            } else {
                setUpMethodAnnotationName = "BeforeMethod";
                tearDownMethodAnnotationName = "AfterMethod";
                annotationPackage = "org.testng.annotations";
                additionalClasspathResource = "testng-7.7.1";
                tearDownMethodAnnotationParameters = "(alwaysRun = true)";
            }

            this.setUpMethodAnnotation = "@" + setUpMethodAnnotationName;
            this.tearDownMethodAnnotation = "@" + tearDownMethodAnnotationName;

            this.setUpMethodAnnotationSignature = "@" + annotationPackage + "." + setUpMethodAnnotationName;
            this.tearDownMethodAnnotationSignature = "@" + annotationPackage + "." + tearDownMethodAnnotationName;

            this.setUpImportToAdd = annotationPackage + "." + setUpMethodAnnotationName;
            this.tearDownImportToAdd = annotationPackage + "." + tearDownMethodAnnotationName;
        }

        private Map<J.Identifier, Expression> getMockedTypesFields() {
            if (mockedTypesFields == null) {
                mockedTypesFields = getCursor().getNearestMessage(MOCKED_TYPES_FIELDS, new LinkedHashMap<>());
            }
            return mockedTypesFields;
        }

        private void findMockStaticInvocations(J.ClassDeclaration classDecl) {
            // Interfaces have null bodies
            if (classDecl.getKind() == J.ClassDeclaration.Kind.Type.Interface) {
                return;
            }
            J.Block classBody = classDecl.getBody();
            List<J.Block> methodBodies = classBody.getStatements().stream()
              .filter(statement -> statement instanceof J.MethodDeclaration)
              .map(J.MethodDeclaration.class::cast)
              .map(J.MethodDeclaration::getBody)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
            Set<J.MethodInvocation> mockStaticMethodInvocations = new HashSet<>();
            for (J.Block methodBody : methodBodies) {
                mockStaticMethodInvocations.addAll(
                  methodBody.getStatements()
                    .stream().filter(statement -> statement instanceof J.MethodInvocation)
                    .map(J.MethodInvocation.class::cast)
                    .filter(MOCKED_STATIC_MATCHER::matches)
                    .collect(Collectors.toSet()));
            }
            Map<String, J.MethodInvocation> mockStaticInvocationsByClassName = new HashMap<>();
            for (J.MethodInvocation mockStaticMethodInvocation : mockStaticMethodInvocations) {
                Optional<Expression> firstArgument = mockStaticMethodInvocation.getArguments().stream().findFirst();
                firstArgument.ifPresent(expression -> mockStaticInvocationsByClassName.put(expression.toString(), mockStaticMethodInvocation));
            }
            getCursor().putMessage(MOCK_STATIC_INVOCATIONS, mockStaticInvocationsByClassName);
        }

        @NotNull
        private J.ClassDeclaration addFieldDeclarationForMockedTypes(J.ClassDeclaration classDecl, ExecutionContext ctx, List<Expression> mockedTypes) {
            // Add field declarations of mocked types
            Map<J.Identifier, Expression> mockedTypesIdentifiers = new LinkedHashMap<>();

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
                mockedTypesIdentifiers.put(mockField.getVariables().get(0).getName(), mockedType);
            }
            getCursor().putMessage(MOCKED_TYPES_FIELDS, mockedTypesIdentifiers);

            maybeAutoFormat(classDecl, classDecl.withPrefix(classDecl.getPrefix().
              withWhitespace("")), classDecl.getName(), ctx, getCursor());
            maybeAddImport(MOCKED_STATIC);
            maybeAddImport("org.mockito.Mockito", "mockStatic");
            return classDecl;
        }

        @NotNull
        private J.ClassDeclaration maybeAddSetUpMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            return maybeAddMethodWithAnnotation(classDecl, ctx, "setUpStaticMocks",
              setUpMethodAnnotationSignature, setUpMethodAnnotation,
              additionalClasspathResource, setUpImportToAdd, "");
        }

        private J.ClassDeclaration maybeAddMethodWithAnnotation(J.ClassDeclaration classDecl, ExecutionContext ctx,
                                                                String methodName, String methodAnnotationSignature,
                                                                String methodAnnotationToAdd,
                                                                String additionalClasspathResource, String importToAdd,
                                                                @NonNull String methodAnnotationParameters) {
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
                    methodAnnotationToAdd + methodAnnotationParameters + " void " + methodName + "() {}")
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
            return maybeAddMethodWithAnnotation(classDecl, ctx, "tearDownStaticMocks",
              tearDownMethodAnnotationSignature,
              tearDownMethodAnnotation,
              additionalClasspathResource, tearDownImportToAdd, tearDownMethodAnnotationParameters);
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
                              "() -> #{any()}")
                            .build(),
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
        private J.Identifier getFieldIdentifier(String fieldName) {
            return getMockedTypesFields().keySet().stream()
              .filter(identifier -> identifier.getSimpleName().equals(fieldName)).findFirst()
              .orElseGet(() -> {
                  J.ClassDeclaration cd = getCursor().dropParentUntil(it -> it instanceof J.ClassDeclaration).getValue();
                  return cd.getBody().getStatements().stream()
                    .filter(statement -> statement instanceof J.VariableDeclarations)
                    .map(variableDeclarations -> ((J.VariableDeclarations) variableDeclarations).getVariables())
                    .flatMap(Collection::stream)
                    .filter(namedVariable -> namedVariable.getSimpleName().equals(fieldName))
                    .map(J.VariableDeclarations.NamedVariable::getName)
                    .findFirst()
                    .orElse(null);
              });
        }
    }
}
