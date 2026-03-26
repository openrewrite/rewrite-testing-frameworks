/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.util.*;

import static java.util.Collections.replaceAll;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.testing.mockito.MockitoUtils.maybeAddMethodWithAnnotation;

public class PowerMockitoMockStaticToMockito extends Recipe {

    @Getter
    final String displayName = "Replace `PowerMock.mockStatic()` with `Mockito.mockStatic()`";

    @Getter
    final String description = "Replaces `PowerMockito.mockStatic()` by `Mockito.mockStatic()`. Removes " +
            "the `@PrepareForTest` annotation.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        Preconditions.or(
                                new UsesType<>("org.powermock..*", false),
                                new UsesType<>("org.mockito..*", false)
                        ),
                        Preconditions.not(new KotlinFileChecker<>())
                ),
                new PowerMockitoToMockitoVisitor()
        );
    }

    private static class PowerMockitoToMockitoVisitor extends JavaVisitor<ExecutionContext> {
        private static final String MOCKED_STATIC = "org.mockito.MockedStatic";
        private static final MethodMatcher MOCKED_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
        private static final MethodMatcher MOCKED_STATIC_CLOSE_MATCHER = new MethodMatcher("org.mockito.ScopedMock close(..)", true);
        private static final MethodMatcher MOCKITO_VERIFY_MATCHER = new MethodMatcher("org.mockito.Mockito verify(..)");
        private static final MethodMatcher MOCKITO_WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
        private static final MethodMatcher MOCKITO_STATIC_METHOD_MATCHER = new MethodMatcher("org.mockito..* *(..)");
        private static final AnnotationMatcher PREPARE_FOR_TEST_MATCHER =
                new AnnotationMatcher("@org.powermock.core.classloader.annotations.PrepareForTest");
        private static final String MOCKED_TYPES_FIELDS = "mockedTypesFields";
        private static final String MOCK_STATIC_INVOCATIONS = "mockStaticInvocationsByClassName";
        private static final MethodMatcher DYNAMIC_WHEN_METHOD_MATCHER = new MethodMatcher("org.mockito.Mockito when(java.lang.Class, String, ..)");
        private static final String MOCK_PREFIX = "mocked";
        private static final String TEST_GROUP = "testGroup";
        private static final String TEST_FRAMEWORK_KEY = "testFramework";

        @Override
        public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                TestFramework framework = TestFramework.detect((J) tree);
                getCursor().putMessage(TEST_FRAMEWORK_KEY, framework);
            }
            return super.visit(tree, ctx);
        }

        private TestFramework getTestFramework() {
            return getCursor().getNearestMessage(TEST_FRAMEWORK_KEY, TestFramework.JUNIT5);
        }

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            getCursor().putMessage(MOCK_STATIC_INVOCATIONS, new HashMap<>());

            // Extract classes from @PrepareForTest annotation
            List<Expression> mockedStaticClasses = extractPrepareForTestClasses(classDecl);

            J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);

            if (mockedStaticClasses.isEmpty()) {
                return cd;
            }

            cd = addFieldDeclarationForMockedTypes(cd, ctx, mockedStaticClasses);

            if (getMockedTypesFields().isEmpty()) {
                return cd;
            }

            TestFramework framework = getTestFramework();
            cd = maybeAddSetUpMethodBody(cd, ctx, framework);
            cd = maybeAddTearDownMethodBody(cd, ctx, framework);

            // Invoke the visitors of the child tree a 2nd time to fill the new methods
            return super.visitClassDeclaration(cd, ctx);
        }

        private List<Expression> extractPrepareForTestClasses(J.ClassDeclaration classDecl) {
            List<Expression> mockedStaticClasses = new ArrayList<>();
            for (J.Annotation j : classDecl.getAllAnnotations()) {
                if (PREPARE_FOR_TEST_MATCHER.matches(j)) {
                    List<Expression> arguments = j.getArguments();
                    if (arguments != null && !arguments.isEmpty()) {
                        mockedStaticClasses.addAll(ListUtils.flatMap(arguments, a -> {
                            if (a instanceof J.NewArray && ((J.NewArray) a).getInitializer() != null) {
                                // case `@PrepareForTest( {Object1.class, Object2.class ...} )`
                                return ((J.NewArray) a).getInitializer();
                            }
                            if (a instanceof J.Assignment && ((J.NewArray) ((J.Assignment) a).getAssignment()).getInitializer() != null) {
                                // case `@PrepareForTest( value = {Object1.class, Object2.class ...} }`
                                return ((J.NewArray) ((J.Assignment) a).getAssignment()).getInitializer();
                            }
                            if (a instanceof J.FieldAccess) {
                                // case `@PrepareForTest(Object1.class)`
                                return a;
                            }
                            return null;
                        }));
                        doAfterVisit(new RemoveAnnotationVisitor(PREPARE_FOR_TEST_MATCHER));
                    }
                }
            }
            return mockedStaticClasses;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
            TestFramework framework = getTestFramework();

            // Add close static mocks on demand to tear down method
            AnnotationMatcher tearDownAnnotationMatcher = new AnnotationMatcher(framework.tearDownAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(tearDownAnnotationMatcher::matches)) {
                return addCloseStaticMocksOnDemandStatement(m, ctx);
            }

            // Initialize the static mocks in the setup method
            AnnotationMatcher setUpAnnotationMatcher = new AnnotationMatcher(framework.setUpAnnotationSignature);
            if (m.getAllAnnotations().stream().anyMatch(setUpAnnotationMatcher::matches)) {
                m = moveMockStaticMethodToSetUp(m, ctx);
            }
            return m;
        }

        @Override
        public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

            if (DYNAMIC_WHEN_METHOD_MATCHER.matches(mi)) {
                return modifyDynamicWhenMethodInvocation(mi);
            }

            if (MOCKITO_WHEN_MATCHER.matches(mi) || MOCKITO_VERIFY_MATCHER.matches(mi)) {
                return modifyWhenMethodInvocation(mi);
            }

            if (!MOCKED_STATIC_MATCHER.matches(mi)) {
                return mi;
            }

            // Track mockStatic invocations for use by setUp method generation
            Map<String, J.MethodInvocation> mockStaticInvocationsByClassName = getCursor().getNearestMessage(MOCK_STATIC_INVOCATIONS);
            if (mockStaticInvocationsByClassName != null && !mi.getArguments().isEmpty()) {
                Expression firstArgument = mi.getArguments().get(0);
                mockStaticInvocationsByClassName.put(firstArgument.toString(), mi);
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, MOCK_STATIC_INVOCATIONS, mockStaticInvocationsByClassName);
            }

            determineTestGroups();

            // Remove bare mockStatic() calls that aren't part of a variable declaration, assignment, or try-with-resources
            if (!getCursor().getPath(o -> o instanceof J.VariableDeclarations ||
                                          o instanceof J.Assignment ||
                                          o instanceof J.Try.Resource).hasNext()) {
                //noinspection DataFlowIssue
                return null;
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

        private static boolean hasMatchingInvocation(J.Identifier staticMock, J.Block methodBody, MethodMatcher matcher) {
            for (Statement statement : methodBody.getStatements()) {
                if (statement instanceof J.MethodInvocation) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statement;
                    if (matcher.matches(methodInvocation) &&
                            methodInvocation.getSelect() instanceof J.Identifier &&
                            ((J.Identifier) methodInvocation.getSelect()).getSimpleName()
                                    .equals(staticMock.getSimpleName())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private J.MethodDeclaration moveMockStaticMethodToSetUp(J.MethodDeclaration m, ExecutionContext ctx) {
            Map<String, J.MethodInvocation> mockStaticInvocations = getCursor().getNearestMessage(MOCK_STATIC_INVOCATIONS);
            if (mockStaticInvocations == null) {
                return m;
            }

            for (Map.Entry<J.Identifier, Expression> mockedTypesFieldEntry : getMockedTypesFields().entrySet()) {
                J.Block methodBody = m.getBody();
                if (methodBody == null || hasMatchingInvocation(mockedTypesFieldEntry.getKey(), methodBody, MOCKED_STATIC_MATCHER)) {
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
            return m;
        }

        private J.MethodDeclaration addCloseStaticMocksOnDemandStatement(J.MethodDeclaration m, ExecutionContext ctx) {
            for (Map.Entry<J.Identifier, Expression> mockedTypesField : getMockedTypesFields().entrySet()) {
                J.Block methodBody = m.getBody();
                if (methodBody == null || hasMatchingInvocation(mockedTypesField.getKey(), methodBody, MOCKED_STATIC_CLOSE_MATCHER)) {
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
            if (getCursor().getNearestMessage(TEST_GROUP) != null) {
                return;
            }
            J.MethodDeclaration methodDeclarationCursor = getCursor().firstEnclosing(J.MethodDeclaration.class);
            if (methodDeclarationCursor == null) {
                return;
            }
            methodDeclarationCursor.getLeadingAnnotations().stream()
                    .filter(annotation -> "Test".equals(annotation.getSimpleName()))
                    .findFirst()
                    .ifPresent(ta -> {
                        if (ta.getArguments() != null) {
                            getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, TEST_GROUP, ta.getArguments());
                        }
                    });
        }

        private J.MethodInvocation modifyDynamicWhenMethodInvocation(J.MethodInvocation method) {
            List<Expression> arguments = method.getArguments();
            String declaringClassName = ((J.FieldAccess) arguments.get(0)).getTarget().toString();
            J.Identifier mockedField = getFieldIdentifier(MOCK_PREFIX + declaringClassName);
            if (mockedField == null) {
                return method;
            }
            arguments.remove(0);
            J.Literal calledMethod = (J.Literal) arguments.get(0);
            arguments.remove(0);
            String stringOfArguments = arguments.stream().map(Object::toString).collect(joining(","));
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
            return method.withSelect(mockedField);
        }

        private Map<J.Identifier, Expression> getMockedTypesFields() {
            return getCursor().getNearestMessage(MOCKED_TYPES_FIELDS, new LinkedHashMap<>());
        }

        private J.ClassDeclaration addFieldDeclarationForMockedTypes(J.ClassDeclaration classDecl, ExecutionContext ctx, List<Expression> mockedStaticClasses) {
            Map<String, J.MethodInvocation> invocationByClassName = getCursor().getNearestMessage(MOCK_STATIC_INVOCATIONS);
            if (invocationByClassName == null || invocationByClassName.isEmpty()) {
                return classDecl;
            }
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
                                classlessTypeName.replace(".", "_")
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

        private J.ClassDeclaration maybeAddSetUpMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx, TestFramework framework) {
            String testGroupsAsString = getTestGroupsAsString();
            return maybeAddMethodWithAnnotation(this, classDecl, ctx, framework.publicMethods, "setUpStaticMocks",
                    framework.setUpAnnotationSignature, framework.setUpAnnotation,
                    framework.classpathResource, framework.setUpImport, testGroupsAsString);
        }

        private String getTestGroupsAsString() {
            List<Expression> testGroups = getCursor().getNearestMessage(TEST_GROUP);
            if (testGroups == null) {
                return "";
            }
            return "(" + testGroups.stream().map(Object::toString).collect(joining(",")) + ")";
        }

        private J.ClassDeclaration maybeAddTearDownMethodBody(J.ClassDeclaration classDecl, ExecutionContext ctx, TestFramework framework) {
            String testGroupsAsString = getTestGroupsAsString();
            if (testGroupsAsString.isEmpty()) {
                testGroupsAsString = framework.tearDownAnnotationParameters;
            }
            return maybeAddMethodWithAnnotation(this, classDecl, ctx, framework.publicMethods, "tearDownStaticMocks",
                    framework.tearDownAnnotationSignature,
                    framework.tearDownAnnotation,
                    framework.classpathResource, framework.tearDownImport, testGroupsAsString);
        }

        private J.MethodInvocation modifyWhenMethodInvocation(J.MethodInvocation whenMethod) {
            List<Expression> methodArguments = whenMethod.getArguments();
            List<J.MethodInvocation> staticMethodInvocationsInArguments = methodArguments.stream()
                    .filter(J.MethodInvocation.class::isInstance).map(J.MethodInvocation.class::cast)
                    .filter(methodInvocation -> !MOCKITO_STATIC_METHOD_MATCHER.matches(methodInvocation))
                    .filter(methodInvocation -> methodInvocation.getMethodType() != null)
                    .filter(methodInvocation -> methodInvocation.getMethodType().hasFlags(Flag.Static))
                    .collect(toList());
            if (staticMethodInvocationsInArguments.size() != 1) {
                return whenMethod;
            }

            J.MethodInvocation staticMI = staticMethodInvocationsInArguments.get(0);
            String declaringClassName = getDeclaringClassName(staticMI);
            J.Identifier mockedStaticClassField = getFieldIdentifier(MOCK_PREFIX + declaringClassName);
            if (mockedStaticClassField == null) {
                // The field definition of the static mocked class is still missing.
                // Return and wait for the second invocation
                return whenMethod;
            }

            Expression lambdaInvocation;
            if (staticMI.getArguments().stream().map(Expression::getType)
                    .noneMatch(Objects::nonNull)) {
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
                    lambdaInvocation = staticMI;
                }
            }
            if (replaceAll(methodArguments, staticMI, lambdaInvocation)) {
                whenMethod = whenMethod.withSelect(mockedStaticClassField);
                whenMethod = whenMethod.withArguments(methodArguments);
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
