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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

public class ParameterizedRunnerToParameterized extends Recipe {
    private static final AnnotationMatcher RUN_WITH_PARAMETERS = new AnnotationMatcher("@org.junit.runner.RunWith(org.junit.runners.Parameterized.class)");
    private static final AnnotationMatcher JUNIT_TEST = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUPITER_TEST = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher PARAMETERS = new AnnotationMatcher("@org.junit.runners.Parameterized$Parameters");
    private static final AnnotationMatcher PARAMETER = new AnnotationMatcher("@org.junit.runners.Parameterized$Parameter");
    private static final AnnotationMatcher PARAMETERIZED_TEST = new AnnotationMatcher("@org.junit.jupiter.params.ParameterizedTest");

    private static final String PARAMETERS_ANNOTATION_ARGUMENTS = "parameters-annotation-args";
    private static final String CONSTRUCTOR_ARGUMENTS = "constructor-args";
    private static final String FIELD_INJECTION_ARGUMENTS = "field-injection-args";

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }
    private static final String PARAMETERS_METHOD_NAME = "parameters-method-name";

    @Override
    public String getDisplayName() {
        return "JUnit 4 `@RunWith(Parameterized.class)` to JUnit Jupiter parameterized tests";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit 4 parameterized runner the JUnit Jupiter parameterized test equivalent.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.runners.Parameterized", false);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ParameterizedRunnerVisitor();
    }

    private static class ParameterizedRunnerVisitor extends JavaIsoVisitor<ExecutionContext> {
        @SuppressWarnings("unchecked")
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Map<String, Object> params = getCursor().pollMessage(classDecl.getId().toString());
            if (params != null) {
                String parametersMethodName = (String) params.get(PARAMETERS_METHOD_NAME);
                List<Expression> parametersAnnotationArguments = (List<Expression>) params.get(PARAMETERS_ANNOTATION_ARGUMENTS);
                List<Statement> constructorParams = (List<Statement>) params.get(CONSTRUCTOR_ARGUMENTS);
                Map<Integer, Statement> fieldInjectionParams = (Map<Integer, Statement>) params.get(FIELD_INJECTION_ARGUMENTS);
                String initMethodName = "init" + cd.getSimpleName();

                // Constructor Injected Test
                if (parametersMethodName != null && constructorParams != null && constructorParams.stream().anyMatch(p -> p instanceof J.VariableDeclarations)) {
                    doAfterVisit(new ParameterizedRunnerToParameterizedTestsVisitor(classDecl, parametersMethodName, initMethodName, parametersAnnotationArguments, constructorParams, true, ctx));
                }

                // Field Injected Test
                else if (parametersMethodName != null && fieldInjectionParams != null) {
                    List<Statement> fieldParams = new ArrayList<>(fieldInjectionParams.values());
                    doAfterVisit(new ParameterizedRunnerToParameterizedTestsVisitor(classDecl, parametersMethodName, initMethodName, parametersAnnotationArguments, fieldParams, false, ctx));
                }
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
            if (m.isConstructor()) {
                Map<String, Object> params = classDeclCursor.computeMessageIfAbsent(((J.ClassDeclaration) classDeclCursor.getValue()).getId().toString(), v -> new HashMap<>());
                params.put(CONSTRUCTOR_ARGUMENTS, m.getParameters());
            }
            for (J.Annotation annotation : m.getLeadingAnnotations()) {
                if (PARAMETERS.matches(annotation)) {
                    Map<String, Object> params = classDeclCursor.computeMessageIfAbsent(((J.ClassDeclaration) classDeclCursor.getValue()).getId().toString(), v -> new HashMap<>());
                    params.put(PARAMETERS_ANNOTATION_ARGUMENTS, annotation.getArguments());
                    params.put(PARAMETERS_METHOD_NAME, method.getSimpleName());
                    break;
                }
            }
            return m;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, ctx);
            Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
            J.Annotation parameterAnnotation = null;
            Integer position = 0;
            for (J.Annotation leadingAnnotation : variableDeclarations.getLeadingAnnotations()) {
                if (PARAMETER.matches(leadingAnnotation)) {
                    parameterAnnotation = leadingAnnotation;
                    if (parameterAnnotation.getArguments() != null && !(parameterAnnotation.getArguments().get(0) instanceof J.Empty)) {
                        J positionArg = parameterAnnotation.getArguments().get(0);
                        if (positionArg instanceof J.Assignment) {
                            position = (Integer) ((J.Literal) ((J.Assignment) positionArg).getAssignment()).getValue();
                        } else {
                            position = (Integer) ((J.Literal) positionArg).getValue();
                        }
                    }
                    break;
                }
            }

            if (parameterAnnotation != null) {
                // the variableDeclaration will be used for a method parameter set the prefix to empty and remove any comments
                J.VariableDeclarations variableForInitMethod = variableDeclarations.withLeadingAnnotations(new ArrayList<>()).withModifiers(new ArrayList<>()).withPrefix(Space.EMPTY);
                if (variableForInitMethod.getTypeExpression() != null) {
                    variableForInitMethod = variableForInitMethod.withTypeExpression(variableForInitMethod.getTypeExpression().withPrefix(Space.EMPTY).withComments(new ArrayList<>()));
                }
                Map<String, TreeMap<Integer, Statement>> params = classDeclCursor.computeMessageIfAbsent(((J.ClassDeclaration) classDeclCursor.getValue()).getId().toString(), v -> new HashMap<>());
                params.computeIfAbsent(FIELD_INJECTION_ARGUMENTS, v -> new TreeMap<>()).put(position, variableForInitMethod);
            }
            return variableDeclarations;
        }
    }

    private static class ParameterizedRunnerToParameterizedTestsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final J.ClassDeclaration scope;
        private final String initMethodName;
        private final List<Statement> parameterizedTestMethodParameters;
        @Nullable
        private final List<Expression> parameterizedTestAnnotationParameters;
        private final String initStatementParamString;

        private final JavaTemplate parameterizedTestTemplate;
        private final JavaTemplate methodSourceTemplate;
        private final JavaTemplate initMethodStatementTemplate;

        @Nullable
        private final JavaTemplate initMethodDeclarationTemplate;

        public ParameterizedRunnerToParameterizedTestsVisitor(J.ClassDeclaration scope,
                                                              String parametersMethodName,
                                                              String initMethodName,
                                                              @Nullable List<Expression> parameterizedTestAnnotationParameters,
                                                              List<Statement> parameterizedTestMethodParameters,
                                                              boolean isConstructorInjection,
                                                              ExecutionContext ctx) {
            this.scope = scope;
            this.initMethodName = initMethodName;

            this.parameterizedTestMethodParameters = parameterizedTestMethodParameters.stream()
                    .map(mp -> mp.withPrefix(Space.EMPTY).withComments(new ArrayList<>()))
                    .map(Statement.class::cast)
                    .collect(Collectors.toList());

            initStatementParamString = parameterizedTestMethodParameters.stream()
                    .filter(J.VariableDeclarations.class::isInstance)
                    .map(J.VariableDeclarations.class::cast)
                    .map(v -> v.getVariables().get(0).getSimpleName())
                    .collect(Collectors.joining(", "));

            // build @ParameterizedTest(#{}) template
            this.parameterizedTestAnnotationParameters = parameterizedTestAnnotationParameters;
            String parameterizedTestAnnotationTemplate = parameterizedTestAnnotationParameters != null ?
                    "@ParameterizedTest(#{any()})" :
                    "@ParameterizedTest";

            JavaParser.Builder<?, ?> javaParserBuilder = JavaParser.fromJavaVersion()
              .classpathFromResources(ctx, "junit-jupiter-api-5.9.2", "junit-jupiter-params-5.9.2");

            this.parameterizedTestTemplate = JavaTemplate.builder(this::getCursor, parameterizedTestAnnotationTemplate)
                    .javaParser(javaParserBuilder)
                    .imports("org.junit.jupiter.params.ParameterizedTest")
                    .build();

            // build @MethodSource("...") template
            this.methodSourceTemplate = JavaTemplate.builder(this::getCursor, "@MethodSource(\"" + parametersMethodName + "\")")
                    .javaParser(javaParserBuilder)
                    .imports("org.junit.jupiter.params.provider.MethodSource").build();

            // build init-method with parameters template
            this.initMethodStatementTemplate = JavaTemplate.builder(this::getCursor, initMethodName + "(#{});")
                    .javaParser(javaParserBuilder)
                    .build();

            // If this is not a constructor injected test then build a javaTemplate for a new init-method
            if (!isConstructorInjection) {
                final StringBuilder initMethodTemplate = new StringBuilder("public void ").append(initMethodName).append("() {\n");
                final List<String> initStatementParams = new ArrayList<>();
                for (Statement parameterizedTestMethodParameter : parameterizedTestMethodParameters) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) parameterizedTestMethodParameter;
                    if (vd.getTypeExpression() != null && vd.getVariables().size() == 1) {
                        initStatementParams.add(vd.getVariables().get(0).getSimpleName());
                    } else {
                        throw new AssertionError("Expected VariableDeclarations with TypeExpression and single Variable, got [" + parameterizedTestMethodParameter + "]");
                    }
                }

                for (String p : initStatementParams) {
                    initMethodTemplate.append("    this.").append(p).append(" = ").append(p).append(";\n");
                }

                initMethodTemplate.append("}");
                initMethodDeclarationTemplate = JavaTemplate.builder(this::getCursor, initMethodTemplate.toString()).javaParser(javaParserBuilder).build();
            } else {
                initMethodDeclarationTemplate = null;
            }
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
            if (c != cu) {
                doAfterVisit(new RemoveAnnotationVisitor(PARAMETERS));
                doAfterVisit(new RemoveAnnotationVisitor(PARAMETER));
                doAfterVisit(new RemoveAnnotationVisitor(RUN_WITH_PARAMETERS));

                maybeRemoveImport("org.junit.Test");
                maybeRemoveImport("org.junit.runner.RunWith");
                maybeRemoveImport("org.junit.runners.Parameterized");
                maybeRemoveImport("org.junit.runners.Parameterized.Parameters");
                maybeRemoveImport("org.junit.runners.Parameterized.Parameter");
                maybeRemoveImport("org.junit.jupiter.api.Test");
                maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                maybeAddImport("org.junit.jupiter.params.provider.MethodSource");
            }
            return c;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (!scope.isScope(classDecl)) {
                return cd;
            }


            if (initMethodDeclarationTemplate != null) {
                cd = cd.withBody(cd.getBody().withTemplate(initMethodDeclarationTemplate,
                        cd.getBody().getCoordinates().lastStatement()));
                J.Block finalBody = cd.getBody();
                cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), stmt -> {
                    if (stmt instanceof J.MethodDeclaration) {
                        J.MethodDeclaration md = (J.MethodDeclaration) stmt;
                        if (md.getName().getSimpleName().equals(initMethodName)) {
                            J.Block body = md.getBody(); // Preserve body formatting
                            return autoFormat(md.withParameters(parameterizedTestMethodParameters).withBody(null),
                                    ctx, new Cursor(getCursor(), finalBody)).withBody(body);
                        }
                    }
                    return stmt;
                })));
            }

            // if a constructor was converted to an init method then remove final modifiers from any associated field variables.
            final Set<String> fieldNames = getCursor().pollMessage("INIT_VARS");
            if (fieldNames != null && !fieldNames.isEmpty()) {
                J.Block finalBody = cd.getBody();
                cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                        if (varDecls.getVariables().stream().anyMatch(it -> fieldNames.contains(it.getSimpleName()))
                                && (varDecls.hasModifier(J.Modifier.Type.Final))) {
                            varDecls = varDecls.withModifiers(ListUtils.map(varDecls.getModifiers(), mod -> mod.getType() == J.Modifier.Type.Final ? null : mod));
                            statement = maybeAutoFormat(statement, varDecls, ctx, new Cursor(getCursor(), finalBody));
                        }
                    }
                    return statement;
                })));
            }
            return cd;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations vdecls = super.visitVariableDeclarations(multiVariable, ctx);
            if (!getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).isScopeInPath(scope)) {
                return vdecls;
            }

            final AtomicReference<Space> annoPrefix = new AtomicReference<>();
            vdecls = vdecls.withLeadingAnnotations(ListUtils.map(vdecls.getLeadingAnnotations(), anno -> {
                if (PARAMETER.matches(anno)) {
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
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            if (!getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).isScopeInPath(scope)) {
                return m;
            }
            // Replace @Test with @ParameterizedTest
            m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), annotation -> {
                if (JUPITER_TEST.matches(annotation) || JUNIT_TEST.matches(annotation)) {
                    List<Comment> annotationComments = annotation.getComments();
                    if (parameterizedTestAnnotationParameters == null) {
                        annotation = annotation.withTemplate(parameterizedTestTemplate,
                                annotation.getCoordinates().replace());
                    } else {
                        annotation = annotation.withTemplate(parameterizedTestTemplate,
                                annotation.getCoordinates().replace(), parameterizedTestAnnotationParameters.get(0));
                    }
                    if (!annotationComments.isEmpty()) {
                        annotation = annotation.withComments(annotationComments);
                    }
                }
                return annotation;
            }));

            // Add @MethodSource, insert test init statement, add test method parameters
            if (m.getLeadingAnnotations().stream().anyMatch(PARAMETERIZED_TEST::matches)) {
                m = m.withTemplate(methodSourceTemplate,
                        m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                assert m.getBody() != null;
                JavaCoordinates newStatementCoordinates = !m.getBody().getStatements().isEmpty() ? m.getBody().getStatements().get(0).getCoordinates().before() : m.getBody().getCoordinates().lastStatement();
                m = m.withTemplate(initMethodStatementTemplate, newStatementCoordinates, initStatementParamString);
                m = maybeAutoFormat(m, m.withParameters(parameterizedTestMethodParameters), m.getName(), ctx, getCursor().getParentTreeCursor());
            }

            // Change constructor to test init method
            if (initMethodDeclarationTemplate == null && m.isConstructor()) {
                m = m.withName(m.getName().withSimpleName(initMethodName));
                m = maybeAutoFormat(m, m.withReturnTypeExpression(new J.Primitive(randomId(), Space.EMPTY, Markers.EMPTY, JavaType.Primitive.Void)),
                        m.getName(), ctx, getCursor().getParentTreeCursor());

                // converting a constructor to a void init method may require removing final modifiers from field vars.
                if (m.getBody() != null) {
                    Set<String> fieldNames = m.getBody().getStatements().stream()
                            .filter(J.Assignment.class::isInstance).map(J.Assignment.class::cast)
                            .map(it -> {
                                if (it.getVariable() instanceof J.FieldAccess) {
                                    return ((J.FieldAccess) it.getVariable()).getName().getSimpleName();
                                }
                                return it.getVariable() instanceof J.Identifier ? ((J.Identifier) it.getVariable()).getSimpleName() : null;
                            })
                            .collect(Collectors.toSet());
                    getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).putMessage("INIT_VARS", fieldNames);
                }
            }
            return m;
        }
    }
}
