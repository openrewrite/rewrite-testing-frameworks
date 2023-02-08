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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

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

        public static final String AFTER_EACH = "org.junit.jupiter.api.AfterEach";
        public static final String MOCKED_STATIC = "org.mockito.MockedStatic";
        public static final MethodMatcher MOCKED_STATIC_MATCHER = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
        private final AnnotationMatcher tearDownAnnotationMatcher = new AnnotationMatcher("@" + AFTER_EACH);
        public static final AnnotationMatcher PREPARE_FOR_TEST_MATCHER =
                new AnnotationMatcher("@org.powermock.core.classloader.annotations.PrepareForTest");

        @Override
        public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            // Add the classes of the arguments in the annotation @PrepareForTest as fields
            // e.g. `@PrepareForTest(Calendar.class)`
            // becomes
            // `private MockStatic mockedCalendar;`
            List<J.Annotation> prepareForTestAnnotations = classDecl.getAllAnnotations().stream()
                    .filter(PREPARE_FOR_TEST_MATCHER::matches).collect(Collectors.toList());
            if (!prepareForTestAnnotations.isEmpty()) {
                List<Expression> mockedTypes = getMockedTypes(prepareForTestAnnotations);
                doAfterVisit(new RemoveAnnotationVisitor(PREPARE_FOR_TEST_MATCHER));

                // If there are mocked types, add an empty tearDown() method if not yet present
                if (!mockedTypes.isEmpty() && !hasTearDownMethod(classDecl)) {
                    J.MethodDeclaration firstTestMethod = getFirstTestMethod(classDecl.getBody().getStatements().stream().filter(statement -> statement instanceof J.MethodDeclaration)
                            .map(J.MethodDeclaration.class::cast).collect(Collectors.toList()));

                    // Tear down only makes sen
                    JavaCoordinates tearDownCoordinates = (firstTestMethod!=null) ?
                            firstTestMethod.getCoordinates().before():
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
                List<String> mockedTypesFieldNames = new ArrayList<>();
                for (Expression mockedType : mockedTypes) {
                    String typeName = mockedType.toString();
                    String classlessTypeName = removeDotClass(typeName);
                    String mockedTypedFieldName = "mocked" + classlessTypeName;
                    mockedTypesFieldNames.add(mockedTypedFieldName);
                    if (classDecl.getBody().getStatements().stream().filter(statement -> statement instanceof J.VariableDeclarations).map(J.VariableDeclarations.class::cast).noneMatch(variableDeclarations -> variableDeclarations.getVariables().stream().anyMatch(namedVariable -> namedVariable.getSimpleName().equals(mockedTypedFieldName)))) {
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
                    }
                }
                getCursor().putMessage("mockedTypesFieldNames", mockedTypesFieldNames);

                maybeAutoFormat(classDecl, classDecl.withPrefix(classDecl.getPrefix().
                                withWhitespace("")), classDecl.getName(), ctx,
                        getCursor());
                maybeAddImport(MOCKED_STATIC);
                maybeAddImport("org.mockito.Mockito", "mockStatic");
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }

        @CheckForNull
        private J.MethodDeclaration getFirstTestMethod(List<J.MethodDeclaration> methods) {
            Optional<J.MethodDeclaration> firstTestMethod = methods.stream().filter(methodDeclaration ->
                    methodDeclaration.getLeadingAnnotations().stream()
                            .anyMatch(annotation -> annotation.getSimpleName().equals("Test"))).findFirst();
            return firstTestMethod.orElse(null);
        }

        private boolean hasTearDownMethod(J.ClassDeclaration classDecl) {
            return classDecl.getBody().getStatements().stream()
                    .filter(statement -> statement instanceof J.MethodDeclaration)
                    .map(J.MethodDeclaration.class::cast)
                    .anyMatch(methodDeclaration -> methodDeclaration.getAllAnnotations().stream()
                            .anyMatch(getTearDownAnnotationMatcher()::matches));
        }

        @NotNull
        private static List<Expression> getMockedTypes(List<J.Annotation> prepareForTestAnnotations) {
            List<Expression> mockedTypes = new ArrayList<>();
            for (J.Annotation prepareForTest : prepareForTestAnnotations) {
                if (prepareForTest!=null && prepareForTest.getArguments()!=null) {
                    mockedTypes.addAll(ListUtils.flatMap(prepareForTest.getArguments(), a -> {
                        if (a instanceof J.NewArray && ((J.NewArray) a).getInitializer()!=null) {
                            // case `@PrepareForTest( {Object1.class, Object2.class ...} )`
                            return ((J.NewArray) a).getInitializer();
                        } else if (a instanceof J.Assignment && ((J.NewArray) ((J.Assignment) a).getAssignment()).getInitializer()!=null) {
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
        public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            // Only the @AfterEach or @AfterMethod is relevant
            if (method.getAllAnnotations().stream().noneMatch(getTearDownAnnotationMatcher()::matches)) {
                return super.visitMethodDeclaration(method, executionContext);
            }

            List<Object> mockedTypesFieldNames = getCursor().pollNearestMessage("mockedTypesFieldNames");
            if (mockedTypesFieldNames!=null) {
                for (Object mockedTypesFieldName : mockedTypesFieldNames) {
                    // Only add close method invocation if not already exists
                    J.Block methodBody = method.getBody();
                    if (methodBody!=null && methodBody.getStatements().stream()
                            .filter(statement -> statement instanceof J.MethodInvocation)
                            .map(J.MethodInvocation.class::cast)
                            .noneMatch(methodInvocation ->
                                    methodInvocation.toString().equals(mockedTypesFieldName + ".close()"))) {
                        method = method.withBody(methodBody.withTemplate(
                                JavaTemplate.builder(() -> getCursor().getParentTreeCursor(),
                                                "#{}.close();")
                                        .build(),
                                methodBody.getCoordinates().lastStatement(),
                                mockedTypesFieldName));
                    }
                }
            }
            return method;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            if (MOCKED_STATIC_MATCHER.matches(method)) {
                Cursor declaringScope = getCursor().dropParentUntil(it -> it instanceof J.ClassDeclaration
                        || it instanceof J.MethodDeclaration || it==Cursor.ROOT_VALUE);
                if (declaringScope.getValue() instanceof J.MethodDeclaration) {
                    List<Expression> methodArguments = method.getArguments();
                    if (methodArguments.size()==1) {
                        J.FieldAccess fieldAccess = (J.FieldAccess) methodArguments.get(0);
                        String className = fieldAccess.getTarget().toString();
                        return method.withTemplate(
                                JavaTemplate.builder(
                                                this::getCursor, "mocked#{} = #{};")
                                        .build(),
                                method.getCoordinates().replace(),
                                className,
                                method.toString()
                        );
                    }
                }
            }
            return super.visitMethodInvocation(method, executionContext);
        }

        private static String removeDotClass(String s) {
            return s.endsWith(".class") ? s.substring(0, s.length() - 6):s;
        }

        private AnnotationMatcher getTearDownAnnotationMatcher() {
            return tearDownAnnotationMatcher;
        }
    }
}
