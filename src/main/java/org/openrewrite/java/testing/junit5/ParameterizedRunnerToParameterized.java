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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

/**
 * Recipe for migrating JUnit4 @RunWith(Parameterized.class) tests to their JUnit 5 Jupiter ParameterizedTest equivalent.
 * <p>
 * Visitor for converting @RunWith(@Parameterized.class) to @ParameterizedTests with @MethodSource
 * <p>
 * 1. Remove `@RunWith(Parameterized.class)`
 * 2. Replace `@Test` with `@ParameterizedTest` having arguments from `@Parameters` method
 * 3. Add `@MethodSource(...)` with argument equal to `@Parameters` method name to each `@ParameterizedTest`
 * 4. Remove @Parameters annotation
 * 5. Change constructor to an initialization method having a void return type.
 * 6. For each `@ParameterizedTest` Insert statement to Invoke initialization method with test parameters
 * 7. Remove imports
 * org.junit.Test;
 * org.junit.runner.RunWith;
 * org.junit.runners.Parameterized;
 * org.junit.runners.Parameterized.Parameters;
 * 8. Add imports
 * org.junit.jupiter.params.ParameterizedTest;
 * org.junit.jupiter.params.provider.MethodSource;
 */
public class ParameterizedRunnerToParameterized extends Recipe {

    private static final AnnotationMatcher RUN_WITH_PARAMETERS_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)");
    private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUPITER_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher PARAMETERS_MATCHER = new AnnotationMatcher("@org.junit.runners.Parameterized.Parameters");
    private static final AnnotationMatcher PARAMETERIZED_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.ParameterizedTest");

    private static final String PARAMETERIZED_TEST_ANNOTATION_PARAMETERS = "parameterizedTestParameters";
    private static final String PARAMETERIZED_TEST_METHOD_PARAMETERS = "parameterizedTestMethodParameters";
    private static final String METHOD_REFERENCE_NAME = "methodReferenceName";
    private static final String INIT_METHOD_NAME = "initMethodName";

    private static final ThreadLocal<JavaParser> PARAMETERIZED_TEMPLATE_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(Parser.Input.fromResource("/META-INF/rewrite/Parameterized.java", "---")).build()
    );

    @Override
    public String getDisplayName() {
        return "JUnit4 @RunWith(Parameterized.class) to JUnit Jupiter Parameterized Tests";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit4 Parameterized runner the JUnit Jupiter ParameterizedTest equivalent.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ParameterizedRunnerVisitor();
    }

    /**
     * Visitor for collecting Parameterized Test components and then scheduling the appropriate conversion visitor for the next visit
     */
    protected static class ParameterizedRunnerVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

            String methodReferenceName = getCursor().getMessage(METHOD_REFERENCE_NAME);
            String initMethodName = getCursor().getMessage(INIT_METHOD_NAME);
            List<Expression> testAnnotationParams = getCursor().getMessage(PARAMETERIZED_TEST_ANNOTATION_PARAMETERS);
            List<Statement> testMethodParams = getCursor().getMessage(PARAMETERIZED_TEST_METHOD_PARAMETERS);

            // Condition for converting to ParameterizedTest with MethodParams
            if (methodReferenceName != null && testMethodParams != null && initMethodName != null) {
                doAfterVisit(new ParameterizedTestWithMethodSourceVisitor(methodReferenceName, initMethodName, testAnnotationParams, testMethodParams));
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
            Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
            m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), annotation -> {
                if (PARAMETERS_MATCHER.matches(annotation)) {
                    classDeclCursor.putMessage(PARAMETERIZED_TEST_ANNOTATION_PARAMETERS, annotation.getArguments());
                    classDeclCursor.putMessage(METHOD_REFERENCE_NAME, method.getSimpleName());
                }
                return annotation;
            }));
            if (m.isConstructor()) {
                classDeclCursor.putMessage(PARAMETERIZED_TEST_METHOD_PARAMETERS, m.getParameters());
                classDeclCursor.putMessage(INIT_METHOD_NAME, "init" + m.getSimpleName());
            }
            return m;
        }

        /**
         * Visitor for converting Parameterized runner to Parameterized Tests with an associated MethodSource
         */
        protected class ParameterizedTestWithMethodSourceVisitor extends JavaIsoVisitor<ExecutionContext> {
            private final String methodReference;
            private final String initMethodName;
            private final List<Statement> parameterizedTestMethodParameters;
            private final String initStatementParams;
            private final String parameterizedTestAnnotationTemplate;

            public ParameterizedTestWithMethodSourceVisitor(String methodReference, String initMethodName, @Nullable List<Expression> parameterizedTestAnnotationParameters, List<Statement> parameterizedTestMethodParameters) {
                this.methodReference = methodReference;
                this.initMethodName = initMethodName;
                this.parameterizedTestMethodParameters = parameterizedTestMethodParameters;
                this.initStatementParams = parameterizedTestMethodParameters.stream()
                        .map(J.VariableDeclarations.class::cast)
                        .map(n -> n.getVariables().get(0).getSimpleName())
                        .collect(Collectors.joining(", "));
                this.parameterizedTestAnnotationTemplate = parameterizedTestAnnotationParameters != null ? "@ParameterizedTest(" + parameterizedTestAnnotationParameters.get(0).print() + ")" : "@ParameterizedTest";
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);
                // Remove @RunWith(Parameterized.class) annotation
                cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), annotation -> {
                    if (RUN_WITH_PARAMETERS_ANNOTATION_MATCHER.matches(annotation)) {
                        return null;
                    }
                    return annotation;
                }));

                // Update Imports
                maybeRemoveImport("org.junit.Test");
                maybeRemoveImport("org.junit.runner.RunWith");
                maybeRemoveImport("org.junit.runners.Parameterized");
                maybeRemoveImport("org.junit.runners.Parameterized.Parameters");
                maybeRemoveImport("org.junit.jupiter.api.Test");
                maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                maybeAddImport("org.junit.jupiter.params.provider.MethodSource");
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

                // Remove the @Parameters annotation
                m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), annotation -> {
                    if (PARAMETERS_MATCHER.matches(annotation)) {
                        return null;
                    }
                    return annotation;
                }));

                // Replace the @Test with @ParameterizedTest
                m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), annotation -> {
                    if (JUPITER_TEST_ANNOTATION_MATCHER.matches(annotation) || JUNIT_TEST_ANNOTATION_MATCHER.matches(annotation)) {
                        annotation = annotation.withTemplate(template(parameterizedTestAnnotationTemplate)
                                        .javaParser(PARAMETERIZED_TEMPLATE_PARSER.get())
                                        .imports("org.junit.jupiter.params.ParameterizedTest").build(),
                                annotation.getCoordinates().replace());
                    }
                    return annotation;
                }));

                // Add @MethodSource, insert test init statement, add test method parameters
                if (m.getLeadingAnnotations().stream().anyMatch(PARAMETERIZED_TEST_ANNOTATION_MATCHER::matches)) {
                    m = m.withTemplate(template("@MethodSource(\"" + methodReference + "\")")
                                    .javaParser(PARAMETERIZED_TEMPLATE_PARSER.get())
                                    .imports("org.junit.jupiter.params.provider.MethodSource").build(),
                            m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                    assert m.getBody() != null;
                    JavaCoordinates newStatementCoordinates = !m.getBody().getStatements().isEmpty() ? m.getBody().getStatements().get(0).getCoordinates().before() : m.getBody().getCoordinates().lastStatement();
                    m = m.withTemplate(template(initMethodName + "(#{});")
                            .javaParser(PARAMETERIZED_TEMPLATE_PARSER.get())
                            .build(), newStatementCoordinates, initStatementParams);
                    m = m.withParameters(parameterizedTestMethodParameters);
                }

                // Change constructor to test init method
                if (m.isConstructor()) {
                    m = m.withName(m.getName().withName(initMethodName));
                    m = maybeAutoFormat(m, m.withReturnTypeExpression(new J.Primitive(randomId(), Space.EMPTY, Markers.EMPTY, JavaType.Primitive.Void)),
                            executionContext, getCursor().dropParentUntil(J.class::isInstance));
                }
                return m;
            }
        }
    }
}
