/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReplaceMockitoTestExecutionListener extends Recipe {

    private static final AnnotationMatcher EXECUTION_LISTENER_ANNOTATION_MATCHER =
            new AnnotationMatcher("@org.springframework.test.context.TestExecutionListeners");
    private static final AnnotationMatcher RUN_WITH_MATCHER =
            new AnnotationMatcher("@org.junit.runner.RunWith");
    private static final String MOCKITO_TEST_EXECUTION_LISTENER =
            "org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener";
    private static final MethodMatcher OPEN_MOCKS_MATCHER =
            new MethodMatcher("org.mockito.MockitoAnnotations openMocks(..)");

    @Getter
    final String displayName = "Replace `MockitoTestExecutionListener` with the equivalent Mockito test initialization";

    @Getter
    final String description = "Replace `@TestExecutionListeners(MockitoTestExecutionListener.class)` with the appropriate " +
            "Mockito initialization for the test framework in use: `@ExtendWith(MockitoExtension.class)` for JUnit 5, " +
            "`@RunWith(MockitoJUnitRunner.class)` for JUnit 4, or `MockitoAnnotations.openMocks(this)` for TestNG.";

    private enum TestFramework {
        JUNIT5, JUNIT4, TESTNG
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(MOCKITO_TEST_EXECUTION_LISTENER, true), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);
                MockitoListenerContext context = MockitoListenerContext.ofClass(cd);
                if (!context.hasMockitoListener()) {
                    return cd;
                }

                TestFramework framework = detectFramework(cd);

                // Skip if replacement already exists or can't be added
                if (framework == TestFramework.JUNIT5 && context.extendWithMockitoFound) {
                    return cd;
                }
                if (framework == TestFramework.JUNIT4 && context.runWithFound) {
                    return cd;
                }
                if (framework == TestFramework.TESTNG && hasOpenMocksCall(cd)) {
                    return cd;
                }

                // Add replacement based on framework
                switch (framework) {
                    case JUNIT5:
                        cd = JavaTemplate.builder("@ExtendWith(MockitoExtension.class)")
                                .imports("org.junit.jupiter.api.extension.ExtendWith", "org.mockito.junit.jupiter.MockitoExtension")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api", "mockito-junit-jupiter"))
                                .build()
                                .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");
                        maybeAddImport("org.mockito.junit.jupiter.MockitoExtension");
                        break;
                    case JUNIT4:
                        cd = JavaTemplate.builder("@RunWith(MockitoJUnitRunner.class)")
                                .imports("org.junit.runner.RunWith", "org.mockito.junit.MockitoJUnitRunner")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-4", "mockito-core-3"))
                                .build()
                                .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                        maybeAddImport("org.junit.runner.RunWith");
                        maybeAddImport("org.mockito.junit.MockitoJUnitRunner");
                        break;
                    case TESTNG:
                        // Add field at beginning of class body
                        cd = JavaTemplate.builder("private AutoCloseable mockitoCloseable;")
                                .build()
                                .apply(updateCursor(cd), cd.getBody().getCoordinates().firstStatement());

                        // Find first method for initMocks placement
                        J.MethodDeclaration firstMethod = cd.getBody().getStatements().stream()
                                .filter(J.MethodDeclaration.class::isInstance)
                                .map(J.MethodDeclaration.class::cast)
                                .findFirst()
                                .orElse(null);

                        // Add initMocks before first method (or at end if no methods)
                        cd = JavaTemplate.builder(
                                        "@BeforeMethod\n" +
                                        "public void initMocks() {\n" +
                                        "    mockitoCloseable = MockitoAnnotations.openMocks(this);\n" +
                                        "}")
                                .contextSensitive()
                                .imports("org.testng.annotations.BeforeMethod", "org.mockito.MockitoAnnotations")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "testng-7", "mockito-core-3"))
                                .build()
                                .apply(updateCursor(cd),
                                        firstMethod != null ? firstMethod.getCoordinates().before() : cd.getBody().getCoordinates().lastStatement());

                        // Add closeMocks at end of class
                        cd = JavaTemplate.builder(
                                        "@AfterMethod\n" +
                                        "public void closeMocks() throws Exception {\n" +
                                        "    mockitoCloseable.close();\n" +
                                        "}")
                                .contextSensitive()
                                .imports("org.testng.annotations.AfterMethod")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "testng-7"))
                                .build()
                                .apply(updateCursor(cd), cd.getBody().getCoordinates().lastStatement());

                        maybeAddImport("org.testng.annotations.BeforeMethod");
                        maybeAddImport("org.testng.annotations.AfterMethod");
                        maybeAddImport("org.mockito.MockitoAnnotations");
                        break;
                }

                // Remove/modify @TestExecutionListeners
                Space prefix = cd.getLeadingAnnotations().get(cd.getLeadingAnnotations().size() - 1).getPrefix();
                return cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
                    if (annotation != null && EXECUTION_LISTENER_ANNOTATION_MATCHER.matches(annotation)) {
                        J.Annotation executionListenerAnnotation = context.getExecutionListenerAnnotation();
                        maybeRemoveImport(MOCKITO_TEST_EXECUTION_LISTENER);
                        maybeRemoveImport("org.springframework.test.context.TestExecutionListeners.MergeMode");
                        maybeRemoveImport("org.springframework.test.context.TestExecutionListeners");
                        if (executionListenerAnnotation != null) {
                            return executionListenerAnnotation
                                    .withArguments(firstItemPrefixWorkaround(executionListenerAnnotation.getArguments()))
                                    .withPrefix(prefix);
                        }
                        return null;
                    }
                    return annotation;
                }));
            }

            private TestFramework detectFramework(J.ClassDeclaration cd) {
                // Check extends for TestNG base classes
                if (cd.getExtends() != null && cd.getExtends().getType() instanceof JavaType.Class) {
                    if (isTestNGType((JavaType.Class) cd.getExtends().getType())) {
                        return TestFramework.TESTNG;
                    }
                }

                // Check compilation unit imports
                J.CompilationUnit cu = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class);
                boolean hasTestNG = false;
                boolean hasJupiter = false;
                boolean hasJUnit4 = false;
                for (J.Import imp : cu.getImports()) {
                    String pkg = imp.getPackageName();
                    if (pkg.startsWith("org.testng")) {
                        hasTestNG = true;
                    } else if (pkg.startsWith("org.junit.jupiter")) {
                        hasJupiter = true;
                    } else if (pkg.startsWith("org.junit") && !pkg.startsWith("org.junit.jupiter")) {
                        hasJUnit4 = true;
                    }
                }

                if (hasTestNG) {
                    return TestFramework.TESTNG;
                }
                if (hasJupiter) {
                    return TestFramework.JUNIT5;
                }
                if (hasJUnit4) {
                    return TestFramework.JUNIT4;
                }

                // Default to JUnit 5
                return TestFramework.JUNIT5;
            }

            private boolean hasOpenMocksCall(J.ClassDeclaration cd) {
                return new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                        if (OPEN_MOCKS_MATCHER.matches(method)) {
                            found.set(true);
                        }
                        return super.visitMethodInvocation(method, found);
                    }
                }.reduce(cd, new AtomicBoolean(false)).get();
            }

            private boolean isTestNGType(JavaType.Class type) {
                String fqn = type.getFullyQualifiedName();
                if (fqn.startsWith("org.testng") || fqn.startsWith("org.springframework.test.context.testng")) {
                    return true;
                }
                JavaType.FullyQualified supertype = type.getSupertype();
                if (supertype instanceof JavaType.Class) {
                    return isTestNGType((JavaType.Class) supertype);
                }
                return false;
            }
        });
    }

    private static class MockitoListenerContext {
        private J.@Nullable Annotation testExecutionListenerAnnotation;
        boolean extendWithMockitoFound = false;
        boolean runWithFound = false;
        private J.@Nullable NewArray listeners;
        private J.@Nullable FieldAccess listener;
        private @Nullable Expression inheritListeners;
        private @Nullable Expression mergeMode;

        static MockitoListenerContext ofClass(J.ClassDeclaration clazz) {
            MockitoListenerContext context = new MockitoListenerContext();
            clazz.getLeadingAnnotations().forEach(annotation -> {
                if (EXECUTION_LISTENER_ANNOTATION_MATCHER.matches(annotation)) {
                    context.testExecutionListenersFound(annotation);
                } else if (new AnnotationMatcher("@org.junit.jupiter.api.extension.ExtendWith").matches(annotation)) {
                    // Check if it's specifically ExtendWith(MockitoExtension.class)
                    if (annotation.getArguments() != null) {
                        for (Expression arg : annotation.getArguments()) {
                            if (isTypeReference(arg, "org.mockito.junit.jupiter.MockitoExtension")) {
                                context.extendWithMockitoFound = true;
                            }
                        }
                    }
                } else if (RUN_WITH_MATCHER.matches(annotation)) {
                    context.runWithFound = true;
                }
            });
            return context;
        }

        private void testExecutionListenersFound(final J.Annotation annotation) {
            testExecutionListenerAnnotation = annotation;
            if (annotation.getArguments() != null) {
                annotation.getArguments().forEach(arg -> {
                    if (arg instanceof J.Assignment) {
                        J.Assignment assignment = (J.Assignment) arg;
                        switch (((J.Identifier) assignment.getVariable()).getSimpleName()) {
                            case "value":
                            case "listeners":
                                if (assignment.getAssignment() instanceof J.NewArray) {
                                    listeners = (J.NewArray) assignment.getAssignment();
                                }
                                break;
                            case "inheritListeners":
                                inheritListeners = assignment.getAssignment();
                                break;
                            case "mergeMode":
                                mergeMode = assignment.getAssignment();
                                break;
                        }
                    } else if (arg instanceof J.NewArray) {
                        listeners = (J.NewArray) arg;
                    } else if (arg instanceof J.FieldAccess) {
                        listener = (J.FieldAccess) arg;
                    }
                });
            }
        }

        public boolean hasMockitoListener() {
            if (listener != null) {
                return isTypeReference(listener, MOCKITO_TEST_EXECUTION_LISTENER);
            }
            if (listeners != null && listeners.getInitializer() != null) {
                return listeners.getInitializer().stream().anyMatch(l -> isTypeReference(l, MOCKITO_TEST_EXECUTION_LISTENER));
            }
            return false;
        }

        public J.@Nullable Annotation getExecutionListenerAnnotation() {
            if (hasMockitoListener()) {
                if (canTestExecutionListenerBeRemoved()) {
                    return null;
                }
                if (testExecutionListenerAnnotation != null && testExecutionListenerAnnotation.getArguments() != null) {
                    return testExecutionListenerAnnotation.withArguments(ListUtils.map(testExecutionListenerAnnotation.getArguments(), arg -> {
                        if (arg instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) arg;
                            Expression newValue = assignment.getAssignment();
                            switch (((J.Identifier) assignment.getVariable()).getSimpleName()) {
                                case "value":
                                case "listeners":
                                    if (assignment.getAssignment() instanceof J.NewArray) {
                                        newValue = getMigratedListeners();
                                    }
                                    break;
                                case "inheritListeners":
                                    newValue = getMigratedInheritListeners();
                                    break;
                                case "mergeMode":
                                    newValue = getMigratedMergeMode();
                                    break;
                            }
                            if (newValue == null) {
                                return null;
                            }
                            return assignment.withAssignment(newValue);
                        }
                        if (arg instanceof J.NewArray) {
                            return getMigratedListeners();
                        }
                        if (arg instanceof J.FieldAccess && isTypeReference(arg, MOCKITO_TEST_EXECUTION_LISTENER)) {
                            return null;
                        }
                        return arg;
                    }));
                }
            }

            return testExecutionListenerAnnotation;
        }

        private boolean canTestExecutionListenerBeRemoved() {
            if (listener == null && listeners != null && listeners.getInitializer() != null &&
                listeners.getInitializer().stream().allMatch(l -> isTypeReference(l, MOCKITO_TEST_EXECUTION_LISTENER))) {
                return (getMigratedInheritListeners() == null && getMigratedMergeMode() != null);
            }
            return false;
        }

        private @Nullable Expression getMigratedMergeMode() {
            if (mergeMode instanceof J.FieldAccess && "REPLACE_DEFAULTS".equals(((J.FieldAccess) mergeMode).getName().getSimpleName())) {
                return null;
            }
            return mergeMode;
        }

        private @Nullable Expression getMigratedInheritListeners() {
            if (inheritListeners != null && (inheritListeners instanceof J.Literal && Boolean.TRUE.equals(((J.Literal) inheritListeners).getValue()))) {
                return null;
            }
            return inheritListeners;
        }

        private J.@Nullable NewArray getMigratedListeners() {
            if (listeners != null && listeners.getInitializer() != null) {
                List<Expression> newListeners = ListUtils.map(listeners.getInitializer(), l -> {
                    if (l instanceof J.FieldAccess && isTypeReference(l, MOCKITO_TEST_EXECUTION_LISTENER)) {
                        return null;
                    }
                    return l;
                });
                if (newListeners.isEmpty()) {
                    return null;
                }
                return listeners.withInitializer(firstItemPrefixWorkaround(newListeners));
            }
            return listeners;
        }

        private static boolean isTypeReference(Expression expression, String type) {
            return expression.getType() instanceof JavaType.Parameterized &&
                   "java.lang.Class".equals(((JavaType.Parameterized) expression.getType()).getFullyQualifiedName()) &&
                   ((JavaType.Parameterized) expression.getType()).getTypeParameters().size() == 1 &&
                   ((JavaType.Parameterized) expression.getType()).getTypeParameters().get(0) instanceof JavaType.Class &&
                   ((JavaType.Class) ((JavaType.Parameterized) expression.getType()).getTypeParameters().get(0)).getFullyQualifiedName().equals(type);
            }
    }

    private static <T extends Expression> @Nullable List<T> firstItemPrefixWorkaround(@Nullable List<T> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        return ListUtils.mapFirst(list, t -> t.withPrefix(t.getPrefix().withWhitespace(t.getPrefix().getLastWhitespace().replaceAll(" $", ""))));
    }
}
