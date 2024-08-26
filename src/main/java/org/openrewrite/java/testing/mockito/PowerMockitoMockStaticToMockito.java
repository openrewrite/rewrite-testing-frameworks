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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
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
        return Preconditions.check(
                Preconditions.or(
                    new UsesType<>("org.powermock..*", false),
                    new UsesType<>("org.mockito..*", false)
                ),
                new PowerMockitoToMockitoVisitor()
        );
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
        private static final String MOCK_PREFIX = "mocked";
        private static final String TEST_GROUP = "testGroup";

        private String setUpMethodAnnotationSignature;
        private String setUpMethodAnnotation;
        private String tearDownMethodAnnotationSignature;
        private String tearDownMethodAnnotation;
        private String additionalClasspathResource;
        private String setUpImportToAdd;
        private String tearDownImportToAdd;
        private String tearDownMethodAnnotationParameters = "";

        @Override
        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                boolean useTestNg = !FindAnnotations.find((J) tree, "@org.testng.annotations.Test").isEmpty();
                initTestFrameworkInfo(useTestNg);
            }
            return super.visit(tree, ctx);
        }

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            getCursor().putMessage(MOCK_STATIC_INVOCATIONS, new HashMap<>());

            // Add the classes of the arguments in the annotation @PrepareForTest as fields
            // e.g. `@PrepareForTest(Calendar.class)`
            // becomes
            // `private MockStatic mockedCalendar;`
            List<Expression> mockedStaticClasses = new ArrayList<>();
            for (J.Annotation j : classDecl.getAllAnnotations()) {
                if (PREPARE_FOR_TEST_MATCHER.matches(j)) {
                    List<Expression> arguments = j.getArguments();
                    if (arguments != null && !arguments.isEmpty()) {
                        mockedStaticClasses.addAll(ListUtils.flatMap(arguments, a -> {
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
                        doAfterVisit(new RemoveAnnotationVisitor(PREPARE_FOR_TEST_MATCHER));
                    }
                }
            }

            // Remove `@RunWithPowerMockRunner`
            for (J.Annotation annotation : classDecl.getAllAnnotations()) {
                if (RUN_WITH_POWER_MOCK_RUNNER_MATCHER.matches(annotation)) {
                    doAfterVisit(new RemoveAnnotationVisitor(RUN_WITH_POWER_MOCK_RUNNER_MATCHER));
                    maybeRemoveImport(POWER_MOCK_RUNNER);
                }
            }

            J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);

            // Remove the extension of class PowerMockConfiguration
            cd = removeExtension(cd, POWER_MOCK_CONFIG);
            // Remove the extension of class PowerMockTestCase
            cd = removeExtension(cd, POWER_MOCK_TEST_CASE);

            if (!mockedStaticClasses.isEmpty()) {
                // If there are mocked types, add empty setUp() and tearDown() methods if not yet present
                cd = maybeAddSetUpMethodBody(cd, ctx);
                cd = maybeAddTearDownMethodBody(cd, ctx);
                cd = addFieldDeclarationForMockedTypes(cd, ctx, mockedStaticClasses);

                // Invoke the visitors of the child tree a 2nd time to fill the new methods
                return super.visitClassDeclaration(cd, ctx);
            }
            return cd;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);

            // Add close static mocks on demand to tear down method
            AnnotationMatcher tearDownAnnotationMatcher = new AnnotationMatcher(tearDownMethodAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(tearDownAnnotationMatcher::matches)) {
                // Add close statements to the static mocks in the tear down method
                return addCloseStaticMocksOnDemandStatement(m, ctx);
            }

            // Initialize the static mocks in the setup method
            AnnotationMatcher setUpAnnotationMatcher = new AnnotationMatcher(
                    setUpMethodAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(setUpAnnotationMatcher::matches)) {
                // Move the mockStatic method to the setUp method
                m = moveMockStaticMethodToSetUp(m, ctx);
            }
            return m;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

            Map<String, J.MethodInvocation> mockStaticInvocationsByClassName = getCursor().getNearestMessage(MOCK_STATIC_INVOCATIONS);
            if (mockStaticInvocationsByClassName != null && MOCKED_STATIC_MATCHER.matches(mi)) {
                Optional<Expression> firstArgument = mi.getArguments().stream().findFirst();
                firstArgument.ifPresent(expression -> {
                    mockStaticInvocationsByClassName.put(expression.toString(), mi);
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, MOCK_STATIC_INVOCATIONS, mockStaticInvocationsByClassName);
                });
            }

            if (DYNAMIC_WHEN_METHOD_MATCHER.matches(mi)) {
                return modifyDynamicWhenMethodInvocation(mi);
            }

            if (MOCKITO_WHEN_MATCHER.matches(mi) || MOCKITO_VERIFY_MATCHER.matches(mi)) {
                return modifyWhenMethodInvocation(mi);
            }

            if (MOCKED_STATIC_MATCHER.matches(mi)) {
                determineTestGroups();
                if (!getCursor().getPath(o -> o instanceof J.Assignment || o instanceof J.Try.Resource).hasNext()) {
                    //noinspection DataFlowIssue
                    return null;
                }
            }
            return mi;
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

        private static J.@Nullable MethodDeclaration getFirstTestMethod(List<J.MethodDeclaration> methods) {
            for (J.MethodDeclaration methodDeclaration : methods) {
                for (J.Annotation annotation : methodDeclaration.getLeadingAnnotations()) {
                    if ("Test".equals(annotation.getSimpleName())) {
                        return methodDeclaration;
                    }
                }
            }
            return null;
        }

        private static boolean hasMethodWithAnnotation(J.ClassDeclaration classDecl, AnnotationMatcher annotationMatcher) {
            for (Statement statement : classDecl.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) statement;
                    List<J.Annotation> allAnnotations = methodDeclaration.getAllAnnotations();
                    for (J.Annotation annotation : allAnnotations) {
                        if (annotationMatcher.matches(annotation)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean isStaticMockAlreadyClosed(J.Identifier staticMock, J.Block methodBody) {
            for (Statement statement : methodBody.getStatements()) {
                if (statement instanceof J.MethodInvocation) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statement;
                    if (MOCKED_STATIC_CLOSE_MATCHER.matches(methodInvocation)) {
                        if (methodInvocation.getSelect() instanceof J.Identifier) {
                            if (((J.Identifier) methodInvocation.getSelect()).getSimpleName()
                                    .equals(staticMock.getSimpleName())) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private static boolean isStaticMockAlreadyOpened(J.Identifier staticMock, J.Block methodBody) {
            for (Statement statement : methodBody.getStatements()) {
                if (statement instanceof J.MethodInvocation) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statement;
                    if (MOCKED_STATIC_MATCHER.matches(methodInvocation)) {
                        if (methodInvocation.getSelect() instanceof J.Identifier) {
                            if (((J.Identifier) methodInvocation.getSelect()).getSimpleName()
                                    .equals(staticMock.getSimpleName())) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        private J.MethodDeclaration moveMockStaticMethodToSetUp(J.MethodDeclaration m, ExecutionContext ctx) {
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
                        m = JavaTemplate.builder("mocked#{any(org.mockito.MockedStatic)} = #{any(org.mockito.Mockito)};")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                                .build()
                                .apply(
                                        new Cursor(getCursor().getParentOrThrow(), m),
                                        methodBody.getCoordinates().firstStatement(),
                                        mockedTypesFieldEntry.getKey(),
                                        methodInvocation
                                );
                    }
                }
            }
            return m;
        }

        private J.MethodDeclaration addCloseStaticMocksOnDemandStatement(J.MethodDeclaration m, ExecutionContext ctx) {
            for (Map.Entry<J.Identifier, Expression> mockedTypesField : getMockedTypesFields().entrySet()) {
                // Only add close method invocation if not already exists
                J.Block methodBody = m.getBody();
                if (methodBody == null || isStaticMockAlreadyClosed(mockedTypesField.getKey(), methodBody)) {
                    continue;
                }
                m = JavaTemplate.builder("#{any(org.mockito.MockedStatic)}.closeOnDemand();")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .build()
                        .apply(
                                new Cursor(getCursor().getParentOrThrow(), m),
                                methodBody.getCoordinates().lastStatement(),
                                mockedTypesField.getKey()
                        );
            }
            return m;
        }

        private void determineTestGroups() {
            if (getCursor().getNearestMessage(TEST_GROUP) == null) {
                J.MethodDeclaration methodDeclarationCursor = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (methodDeclarationCursor != null) {
                    Optional<J.Annotation> testAnnotation = methodDeclarationCursor
                            .getLeadingAnnotations().stream()
                            .filter(annotation -> annotation.getSimpleName().equals("Test")).findFirst();
                    testAnnotation.ifPresent(
                            ta -> {
                                if (ta.getArguments() != null) {
                                    getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, TEST_GROUP, ta.getArguments());
                                }
                            });
                }
            }
        }

        private J.MethodInvocation modifyDynamicWhenMethodInvocation(J.MethodInvocation method) {
            // Example
            // `Mockito.when(Calendar.class, "getInstance")`
            // is modified to
            // `mockedCalendar.when(() -> Calendar.getInstance())`
            List<Expression> arguments = method.getArguments();
            String declaringClassName = ((J.FieldAccess) arguments.get(0)).getTarget().toString();
            J.Identifier mockedField = getFieldIdentifier(MOCK_PREFIX + declaringClassName);
            if (mockedField != null) {
                arguments.remove(0);
                J.Literal calledMethod = (J.Literal) arguments.get(0);
                arguments.remove(0);
                String stringOfArguments = arguments.stream().map(Object::toString).collect(Collectors.joining(","));
                method = JavaTemplate.builder("() -> #{}.#{}(#{})")
                        .contextSensitive()
                        .build()
                        .apply(
                                new Cursor(getCursor().getParentOrThrow(), method),
                                method.getCoordinates().replaceArguments(),
                                declaringClassName,
                                Objects.requireNonNull(calledMethod.getValue()).toString(),
                                stringOfArguments
                        );
                method = method.withSelect(mockedField);
            }
            return method;
        }

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
                additionalClasspathResource = "junit-jupiter-api-5.9";
            } else {
                setUpMethodAnnotationName = "BeforeMethod";
                tearDownMethodAnnotationName = "AfterMethod";
                annotationPackage = "org.testng.annotations";
                additionalClasspathResource = "testng-7.7";
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
            return getCursor().getNearestMessage(MOCKED_TYPES_FIELDS, new LinkedHashMap<>());
        }

        private J.ClassDeclaration addFieldDeclarationForMockedTypes(J.ClassDeclaration classDecl, ExecutionContext ctx, List<Expression> mockedStaticClasses) {
            // Get the actually invoked staticMethod by class
            Map<String, J.MethodInvocation> invocationByClassName = getCursor().getNearestMessage(MOCK_STATIC_INVOCATIONS);
            if (invocationByClassName == null || invocationByClassName.isEmpty()) {
                // If there are no invocations, nothing needs to be done here
                return classDecl;
            }
            // Add field declarations of mocked types
            Map<J.Identifier, Expression> mockedTypesIdentifiers = new LinkedHashMap<>();
            for (Expression mockedStaticClass : mockedStaticClasses) {
                JavaType.Parameterized classType = TypeUtils.asParameterized(mockedStaticClass.getType());
                if (classType == null) {
                    continue;
                }
                JavaType.FullyQualified fullyQualifiedMockedType = TypeUtils.asFullyQualified(classType.getTypeParameters().get(0));
                if (fullyQualifiedMockedType == null) {
                    continue;
                }
                String classlessTypeName = fullyQualifiedMockedType.getClassName();
                if (invocationByClassName.get(classlessTypeName + ".class") == null) {
                    // Only add fields for classes that are actually invoked by mockStatic()
                    // The not mocked class can be removed from import
                    maybeRemoveImport(fullyQualifiedMockedType.getFullyQualifiedName());
                    continue;
                }
                String mockedTypedFieldName = MOCK_PREFIX + classlessTypeName;
                if (isFieldAlreadyDefined(classDecl.getBody(), mockedTypedFieldName)) {
                    continue;
                }
                classDecl = JavaTemplate.builder("private MockedStatic<#{}> " + MOCK_PREFIX + "#{};")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .staticImports("org.mockito.Mockito.mockStatic")
                        .imports(MOCKED_STATIC)
                        .build()
                        .apply(
                                new Cursor(getCursor().getParentOrThrow(), classDecl),
                                classDecl.getBody().getCoordinates().firstStatement(),
                                classlessTypeName,
                                classlessTypeName
                        );

                J.VariableDeclarations mockField = (J.VariableDeclarations) classDecl.getBody().getStatements().get(0);
                mockedTypesIdentifiers.put(mockField.getVariables().get(0).getName(), mockedStaticClass);
            }
            getCursor().putMessage(MOCKED_TYPES_FIELDS, mockedTypesIdentifiers);

            maybeAutoFormat(classDecl, classDecl.withPrefix(classDecl.getPrefix().
                    withWhitespace("")), classDecl.getName(), ctx, getCursor());
            maybeAddImport(MOCKED_STATIC);
            maybeAddImport("org.mockito.Mockito", "mockStatic");
            return classDecl;
        }

        private J.ClassDeclaration maybeAddSetUpMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            String testGroupsAsString = getTestGroupsAsString();
            return maybeAddMethodWithAnnotation(classDecl, ctx, "setUpStaticMocks",
                    setUpMethodAnnotationSignature, setUpMethodAnnotation,
                    additionalClasspathResource, setUpImportToAdd, testGroupsAsString);
        }

        private String getTestGroupsAsString() {
            List<Expression> testGroups = getCursor().getNearestMessage(TEST_GROUP);
            String testGroupsAsString = "";
            if (testGroups != null) {
                testGroupsAsString = "(" +
                                     testGroups.stream().map(Object::toString).collect(Collectors.joining(",")) +
                                     ")";
            }
            return testGroupsAsString;
        }

        private J.ClassDeclaration maybeAddTearDownMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            String testGroupsAsString = (getTestGroupsAsString().isEmpty()) ? tearDownMethodAnnotationParameters : getTestGroupsAsString();
            return maybeAddMethodWithAnnotation(classDecl, ctx, "tearDownStaticMocks",
                    tearDownMethodAnnotationSignature,
                    tearDownMethodAnnotation,
                    additionalClasspathResource, tearDownImportToAdd, testGroupsAsString);
        }

        private J.ClassDeclaration maybeAddMethodWithAnnotation(J.ClassDeclaration classDecl, ExecutionContext ctx,
                                                                String methodName, String methodAnnotationSignature,
                                                                String methodAnnotationToAdd,
                                                                String additionalClasspathResource, String importToAdd,
                                                                String methodAnnotationParameters) {
            if (hasMethodWithAnnotation(classDecl, new AnnotationMatcher(methodAnnotationSignature))) {
                return classDecl;
            }

            J.MethodDeclaration firstTestMethod = getFirstTestMethod(
                    classDecl.getBody().getStatements().stream().filter(J.MethodDeclaration.class::isInstance)
                            .map(J.MethodDeclaration.class::cast).collect(Collectors.toList()));

            maybeAddImport(importToAdd);
            return JavaTemplate.builder(methodAnnotationToAdd + methodAnnotationParameters + " void " + methodName + "() {}")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, additionalClasspathResource))
                    .imports(importToAdd)
                    .build()
                    .apply(
                            new Cursor(getCursor().getParentOrThrow(), classDecl),
                            (firstTestMethod != null) ?
                                    firstTestMethod.getCoordinates().before() :
                                    classDecl.getBody().getCoordinates().lastStatement()
                    );
        }

        private J.MethodInvocation modifyWhenMethodInvocation(J.MethodInvocation whenMethod) {
            List<Expression> methodArguments = whenMethod.getArguments();
            List<J.MethodInvocation> staticMethodInvocationsInArguments = methodArguments.stream()
                    .filter(J.MethodInvocation.class::isInstance).map(J.MethodInvocation.class::cast)
                    .filter(methodInvocation -> !MOCKITO_STATIC_METHOD_MATCHER.matches(methodInvocation))
                    .filter(methodInvocation -> methodInvocation.getMethodType() != null)
                    .filter(methodInvocation -> methodInvocation.getMethodType().hasFlags(Flag.Static))
                    .collect(Collectors.toList());
            if (staticMethodInvocationsInArguments.size() == 1) {
                J.MethodInvocation staticMI = staticMethodInvocationsInArguments.get(0);
                Expression lambdaInvocation;
                String declaringClassName = getDeclaringClassName(staticMI);
                J.Identifier mockedStaticClassField = getFieldIdentifier(MOCK_PREFIX + declaringClassName);
                if (mockedStaticClassField == null) {
                    // The field definition of the static mocked class is still missing.
                    // Return and wait for the second invocation
                    return whenMethod;
                }
                if (staticMI.getArguments().stream().map(Expression::getType)
                        .noneMatch(Objects::nonNull)) {
                    // If the method invocation has no arguments
                    lambdaInvocation = JavaTemplate.builder(declaringClassName + "::" + staticMI.getSimpleName())
                            .contextSensitive()
                            .build()
                            .apply(new Cursor(getCursor(), staticMI), staticMI.getCoordinates().replace());
                } else {
                    JavaType.Method methodType = staticMI.getMethodType();
                    if (methodType != null) {
                        lambdaInvocation = JavaTemplate.builder("() -> #{any()}")
                                .contextSensitive()
                                .build()
                                .apply(new Cursor(getCursor(), staticMI), staticMI.getCoordinates().replace(), staticMI);
                    } else {
                        // do nothing
                        lambdaInvocation = staticMI;
                    }
                }
                if (Collections.replaceAll(methodArguments, staticMI, lambdaInvocation)) {
                    whenMethod = whenMethod.withSelect(mockedStaticClassField);
                    whenMethod = whenMethod.withArguments(methodArguments);
                }
            }
            return whenMethod;
        }

        private @Nullable String getDeclaringClassName(J.MethodInvocation mi) {
            JavaType.Method methodType = mi.getMethodType();
            if (methodType != null) {
                JavaType.FullyQualified declaringType = methodType.getDeclaringType();
                return declaringType.getClassName();
            }
            return null;
        }

        private J.@Nullable Identifier getFieldIdentifier(String fieldName) {
            return getMockedTypesFields().keySet().stream()
                    .filter(identifier -> identifier.getSimpleName().equals(fieldName)).findFirst()
                    .orElseGet(() -> {
                        J.ClassDeclaration cd = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).getValue();
                        return cd.getBody().getStatements().stream()
                                .filter(J.VariableDeclarations.class::isInstance)
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
