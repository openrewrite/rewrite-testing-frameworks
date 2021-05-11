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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

/**
 * Recipe for migrating JUnit4 @RunWith(Parameterized.class) tests to the JUnit 5 Jupiter ParameterizedTest equivalents.
 * <p>
 * 1. Remove `@RunWith(Parameterized.class)`
 * 2. Replace `@Test` with `@ParameterizedTest` having arguments from `@Parameters` method
 * 3. Add `@MethodSource(...)` with methodName argument equal to `@Parameters` method name to each `@ParameterizedTest`
 * 4. For constructor injected tests change constructor to an initialization method having a void return type.
 * 5. For field injected test generate and insert an init-method for class field values
 * 6. Remove @Parameters and @Parameter annotations
 * 7. Remove imports
 * org.junit.Test;
 * org.junit.runner.RunWith;
 * org.junit.runners.Parameterized;
 * org.junit.runners.Parameterized.Parameter;
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
    private static final AnnotationMatcher PARAMETER_MATCHER = new AnnotationMatcher("@org.junit.runners.Parameterized.Parameter");
    private static final AnnotationMatcher PARAMETERIZED_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.ParameterizedTest");

    private static final String PARAMETERS_ANNOTATION_ARGUMENTS = "parameters-annotation-args";
    private static final String CONSTRUCTOR_ARGUMENTS = "constructor-args";
    private static final String FIELD_INJECTION_ARGUMENTS = "field-injection-args";
    private static final String PARAMETERS_METHOD_NAME = "parameters-method-name";

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
    protected @Nullable TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>("org.junit.runners.Parameterized");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ParameterizedRunnerVisitor();
    }

    /**
     * Visitor for collecting Parameterized Test components and then scheduling the appropriate conversion visitor for the next visit
     */
    private static class ParameterizedRunnerVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

            String parametersMethodName = getCursor().pollMessage(PARAMETERS_METHOD_NAME);
            List<Expression> parametersAnnotationArguments = getCursor().pollMessage(PARAMETERS_ANNOTATION_ARGUMENTS);
            List<Statement> constructorParams = getCursor().pollMessage(CONSTRUCTOR_ARGUMENTS);
            Map<Integer, Statement> fieldInjectionParams = getCursor().pollMessage(FIELD_INJECTION_ARGUMENTS);
            String initMethodName = "init" + cd.getSimpleName();
            // Constructor Injected Test
            if (parametersMethodName != null && constructorParams != null) {
                doAfterVisit(new ParameterizedRunnerToParameterizedTestsVisitor(parametersMethodName, initMethodName, parametersAnnotationArguments, constructorParams, true));
            }
            // Field Injected Test
            else if (parametersMethodName != null && fieldInjectionParams != null) {
                List<Statement> fieldParams = new ArrayList<>(fieldInjectionParams.values());
                doAfterVisit(new ParameterizedRunnerToParameterizedTestsVisitor(parametersMethodName, initMethodName, parametersAnnotationArguments, fieldParams, false));
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
            Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
            m.getLeadingAnnotations().forEach(annotation -> {
                if (PARAMETERS_MATCHER.matches(annotation)) {
                    classDeclCursor.putMessage(PARAMETERS_ANNOTATION_ARGUMENTS, annotation.getArguments());
                    classDeclCursor.putMessage(PARAMETERS_METHOD_NAME, method.getSimpleName());
                }
            });
            if (m.isConstructor()) {
                classDeclCursor.putMessage(CONSTRUCTOR_ARGUMENTS, m.getParameters());
            }
            return m;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
            J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, executionContext);
            Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
            J.Annotation parameterAnnotation = variableDeclarations.getLeadingAnnotations().stream().filter(PARAMETER_MATCHER::matches).findFirst().orElse(null);
            if (parameterAnnotation != null) {
                Integer position = 0;
                if (parameterAnnotation.getArguments() != null && !parameterAnnotation.getArguments().isEmpty() && !(parameterAnnotation.getArguments().get(0) instanceof J.Empty)) {
                    position = (Integer) ((J.Literal) parameterAnnotation.getArguments().get(0)).getValue();
                }
                // the variableDeclaration will be used for a method parameter set the prefix to empty and remove any comments
                J.VariableDeclarations variableForInitMethod = variableDeclarations.withLeadingAnnotations(new ArrayList<>()).withModifiers(new ArrayList<>()).withPrefix(Space.EMPTY);
                if (variableForInitMethod.getTypeExpression() != null) {
                    variableForInitMethod = variableForInitMethod.withTypeExpression(variableForInitMethod.getTypeExpression().withPrefix(Space.EMPTY).withComments(new ArrayList<>()));
                }
                classDeclCursor.computeMessageIfAbsent(FIELD_INJECTION_ARGUMENTS, v -> new TreeMap<Integer, Statement>()).put(position, variableForInitMethod);
            }
            return variableDeclarations;
        }
    }

    private static class ParameterizedRunnerToParameterizedTestsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final String initMethodName;
        private final List<Statement> parameterizedTestMethodParameters;
        private final String initStatementParamString;

        private final JavaTemplate parameterizedTestTemplate;
        private final JavaTemplate methodSourceTemplate;
        private final JavaTemplate initMethodStatementTemplate;
        @Nullable
        private final JavaTemplate initMethodDeclarationTemplate;

        public ParameterizedRunnerToParameterizedTestsVisitor(String parametersMethodName, String initMethodName, @Nullable List<Expression> parameterizedTestAnnotationParameters, List<Statement> parameterizedTestMethodParameters, boolean isConstructorInjection) {
            this.initMethodName = initMethodName;
            this.parameterizedTestMethodParameters = parameterizedTestMethodParameters.stream().map(mp -> mp.withPrefix(Space.EMPTY).withComments(new ArrayList<>())).map(Statement.class::cast).collect(Collectors.toList());
            initStatementParamString = parameterizedTestMethodParameters.stream()
                    .map(J.VariableDeclarations.class::cast)
                    .map(v -> v.getVariables().get(0).getSimpleName())
                    .collect(Collectors.joining(", "));

            // build @ParameterizedTest(#{}) template
            String parameterizedTestAnnotationTemplate = parameterizedTestAnnotationParameters != null ? "@ParameterizedTest(" + parameterizedTestAnnotationParameters.get(0).print() + ")" : "@ParameterizedTest";
            this.parameterizedTestTemplate = template(parameterizedTestAnnotationTemplate)
                    .javaParser(PARAMETERIZED_TEMPLATE_PARSER.get())
                    .imports("org.junit.jupiter.params.ParameterizedTest").build();
            // build @MethodSource("...") template
            this.methodSourceTemplate = template("@MethodSource(\"" + parametersMethodName + "\")")
                    .javaParser(PARAMETERIZED_TEMPLATE_PARSER.get())
                    .imports("org.junit.jupiter.params.provider.MethodSource").build();
            // build init-method with parameters template
            this.initMethodStatementTemplate = template(initMethodName + "(#{});")
                    .javaParser(PARAMETERIZED_TEMPLATE_PARSER.get())
                    .build();

            // If this is not a constructor injected test then build a javaTemplate for a new init-method
            if (!isConstructorInjection) {
                final StringBuilder initMethodTemplate = new StringBuilder("public void ").append(initMethodName).append("(");
                final List<String> initStatementParams = new ArrayList<>();
                for (Statement parameterizedTestMethodParameter : parameterizedTestMethodParameters) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) parameterizedTestMethodParameter;
                    if (vd.getTypeExpression() != null && vd.getVariables().size() == 1) {
                        initStatementParams.add(vd.getVariables().get(0).getSimpleName());
                        initMethodTemplate.append(parameterizedTestMethodParameter.print()).append(", ");
                    } else {
                        throw new AssertionError("Expected VariableDeclarations with TypeExpression and single Variable, got [" + parameterizedTestMethodParameter.print() + "]");
                    }
                }
                initMethodTemplate.replace(initMethodTemplate.length() - 2, initMethodTemplate.length(), ") {\n");
                initStatementParams.forEach(p -> initMethodTemplate.append("    this.").append(p).append(" = ").append(p).append(";\n"));
                initMethodTemplate.append("}");
                initMethodDeclarationTemplate = template(initMethodTemplate.toString()).javaParser(PARAMETERIZED_TEMPLATE_PARSER.get()).build();
            } else {
                initMethodDeclarationTemplate = null;
            }
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
            maybeRemoveImport("org.junit.runners.Parameterized.Parameter");
            maybeRemoveImport("org.junit.jupiter.api.Test");
            maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
            maybeAddImport("org.junit.jupiter.params.provider.MethodSource");

            if (initMethodDeclarationTemplate != null) {
                cd = maybeAutoFormat(cd, cd.withBody(cd.getBody().withTemplate(initMethodDeclarationTemplate,
                        cd.getBody().getCoordinates().lastStatement())), executionContext);
            }
            return cd;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
            J.VariableDeclarations vdecls = super.visitVariableDeclarations(multiVariable, executionContext);
            final AtomicReference<Space> annoPrefix = new AtomicReference<>();
            vdecls = vdecls.withLeadingAnnotations(ListUtils.map(vdecls.getLeadingAnnotations(), anno -> {
                if (PARAMETER_MATCHER.matches(anno)) {
                    annoPrefix.set(anno.getPrefix());
                    return null;
                }
                return anno;
            }));
            if (annoPrefix.get() != null) {
                vdecls = vdecls.withPrefix(annoPrefix.get());
            }
            return vdecls;
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

            // Replace @Test with @ParameterizedTest
            m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), annotation -> {
                if (JUPITER_TEST_ANNOTATION_MATCHER.matches(annotation) || JUNIT_TEST_ANNOTATION_MATCHER.matches(annotation)) {
                    List<Comment> annoComments = annotation.getComments();
                    annotation = annotation.withTemplate(parameterizedTestTemplate,
                            annotation.getCoordinates().replace());
                    if (!annoComments.isEmpty()) {
                        annotation = annotation.withComments(annoComments);
                    }
                }
                return annotation;
            }));

            // Add @MethodSource, insert test init statement, add test method parameters
            if (m.getLeadingAnnotations().stream().anyMatch(PARAMETERIZED_TEST_ANNOTATION_MATCHER::matches)) {
                m = m.withTemplate(methodSourceTemplate,
                        m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                assert m.getBody() != null;
                JavaCoordinates newStatementCoordinates = !m.getBody().getStatements().isEmpty() ? m.getBody().getStatements().get(0).getCoordinates().before() : m.getBody().getCoordinates().lastStatement();
                m = m.withTemplate(initMethodStatementTemplate, newStatementCoordinates, initStatementParamString);
                m = maybeAutoFormat(m, m.withParameters(parameterizedTestMethodParameters), executionContext, getCursor().dropParentUntil(J.class::isInstance));
            }

            // Change constructor to test init method
            if (initMethodDeclarationTemplate == null && m.isConstructor()) {
                m = m.withName(m.getName().withName(initMethodName));
                m = maybeAutoFormat(m, m.withReturnTypeExpression(new J.Primitive(randomId(), Space.EMPTY, Markers.EMPTY, JavaType.Primitive.Void)),
                        executionContext, getCursor().dropParentUntil(J.class::isInstance));
            }
            return m;
        }
    }
}
