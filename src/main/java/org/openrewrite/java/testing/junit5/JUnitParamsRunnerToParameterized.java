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
package org.openrewrite.java.testing.junit5;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.trait.Traits;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Converts Pragmatists JUnitParamsRunner tests to their JUnit 5 ParameterizedTest and associated MethodSource equivalent
 * <a href="https://github.com/Pragmatists/JUnitParams">...</a>
 * Supports the following conversions
 * `@Parameters` annotation without arguments and default `parametersFor...` init-method exists
 * `@Parameters(method = "...")` annotation with defined method references
 * `@Parameters(named = "...")` and associated `@NamedParameter` init-method
 * Unsupported tests are identified with a comment on the associated `@Parameters(...)` annotation.
 */
public class JUnitParamsRunnerToParameterized extends Recipe {

    private static final AnnotationMatcher RUN_WITH_JUNIT_PARAMS_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.runner.RunWith(junitparams.JUnitParamsRunner.class)");
    private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUPITER_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");

    private static final AnnotationMatcher PARAMETERS_MATCHER = new AnnotationMatcher("@junitparams.Parameters");
    private static final AnnotationMatcher TEST_CASE_NAME_MATCHER = new AnnotationMatcher("@junitparams.naming.TestCaseName");
    private static final AnnotationMatcher NAMED_PARAMETERS_MATCHER = new AnnotationMatcher("@junitparams.NamedParameters");
    private static final AnnotationMatcher CONVERTER_MATCHER = new AnnotationMatcher("@junitparams.converters.Param");

    private static final String INIT_METHOD_REFERENCES = "init-method-references";
    private static final String CSV_PARAMS = "csv-params";
    private static final String PARAMETERS_FOR_PREFIX = "parametersFor";
    private static final String PARAMETERIZED_TESTS = "parameterized-tests";
    private static final String INIT_METHODS_MAP = "named-parameters-map";
    private static final String CONVERSION_NOT_SUPPORTED = "conversion-not-supported";

    @Override
    public String getDisplayName() {
        return "Pragmatists `@RunWith(JUnitParamsRunner.class)` to JUnit Jupiter `@Parameterized` tests";
    }

    @Override
    public String getDescription() {
        return "Convert Pragmatists Parameterized test to the JUnit Jupiter ParameterizedTest equivalent.";
    }

    private static String junitParamsDefaultInitMethodName(String methodName) {
        return PARAMETERS_FOR_PREFIX + methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("junitparams.*", false), new ParameterizedTemplateVisitor());
    }

    private static class ParameterizedTemplateVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<String> initMethods = getCursor().computeMessageIfAbsent(INIT_METHOD_REFERENCES, v -> new HashSet<>());
            Boolean hasCsvParams = getCursor().getMessage(CSV_PARAMS);
            if (!initMethods.isEmpty() || Boolean.TRUE.equals(hasCsvParams)) {
                doAfterVisit(new ParametersNoArgsImplicitMethodSource(initMethods,
                        getCursor().computeMessageIfAbsent(INIT_METHODS_MAP, v -> new HashMap<>()),
                        getCursor().computeMessageIfAbsent(CONVERSION_NOT_SUPPORTED, v -> new HashSet<>()),
                        getCursor().computeMessageIfAbsent(PARAMETERIZED_TESTS, v -> new HashSet<>()),
                        ctx));
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
            // methods having names starting with parametersFor... are init methods
            if (m.getSimpleName().startsWith(PARAMETERS_FOR_PREFIX)) {
                classDeclCursor.computeMessageIfAbsent(INIT_METHOD_REFERENCES, v -> new HashSet<>()).add(m.getSimpleName());
            }
            return m;
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation anno = super.visitAnnotation(annotation, ctx);
            Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
            if (PARAMETERS_MATCHER.matches(anno)) {
                Annotated annotated = Traits.annotated(PARAMETERS_MATCHER).require(annotation, getCursor().getParentOrThrow());
                String annotationArgumentValue = getAnnotationArgumentForInitMethod(annotated, "method", "named");
                classDeclCursor.computeMessageIfAbsent(PARAMETERIZED_TESTS, v -> new HashSet<>())
                        .add(getCursor().firstEnclosing(J.MethodDeclaration.class).getSimpleName());
                if (annotationArgumentValue != null) {
                    for (String method : annotationArgumentValue.split(",")) {
                        classDeclCursor.computeMessageIfAbsent(INIT_METHOD_REFERENCES, v -> new HashSet<>()).add(method);
                    }
                } else if (isSupportedCsvParam(annotated)) {
                    anno = getCsVParamTemplate(ctx).apply(updateCursor(anno), anno.getCoordinates().replace(), anno.getArguments().get(0));
                    classDeclCursor.putMessage(CSV_PARAMS, Boolean.TRUE);
                } else if (anno.getArguments() != null && !anno.getArguments().isEmpty()) {
                    // This conversion is not supported add a comment to the annotation and the method name to the not supported list
                    String comment = " JunitParamsRunnerToParameterized conversion not supported";
                    if (anno.getComments().stream().noneMatch(c -> c.printComment(getCursor()).endsWith(comment))) {
                        anno = anno.withComments(ListUtils.concat(anno.getComments(), new TextComment(false, comment,
                                "\n" + anno.getPrefix().getIndent(), Markers.EMPTY)));
                    }
                    J.MethodDeclaration m = getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance).getValue();
                    Set<String> unsupportedMethods = classDeclCursor.computeMessageIfAbsent(CONVERSION_NOT_SUPPORTED, v -> new HashSet<>());
                    unsupportedMethods.add(m.getSimpleName());
                    unsupportedMethods.add(junitParamsDefaultInitMethodName(m.getSimpleName()));
                }
            } else if (NAMED_PARAMETERS_MATCHER.matches(annotation)) {
                Annotated annotated = Traits.annotated(NAMED_PARAMETERS_MATCHER).require(annotation, getCursor().getParentOrThrow());
                Optional<Literal> value = annotated.getDefaultAttribute("value");
                if (value.isPresent()) {
                    J.MethodDeclaration m = getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance).getValue();
                    classDeclCursor.computeMessageIfAbsent(INIT_METHOD_REFERENCES, v -> new HashSet<>()).add(m.getSimpleName());
                    classDeclCursor.computeMessageIfAbsent(INIT_METHODS_MAP, v -> new HashMap<>()).put(value.get().getString(), m.getSimpleName());
                }
            } else if (TEST_CASE_NAME_MATCHER.matches(anno)) {
                Annotated annotated = Traits.annotated(TEST_CASE_NAME_MATCHER).require(annotation, getCursor().getParentOrThrow());
                // test name for ParameterizedTest argument
                Optional<Literal> value = annotated.getDefaultAttribute("value");
                if (value.isPresent()) {
                    Object testNameArg = value.get().getString();
                    String testName = testNameArg != null ? testNameArg.toString() : "{method}({params}) [{index}]";
                    J.MethodDeclaration md = getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance).getValue();
                    classDeclCursor.computeMessageIfAbsent(INIT_METHODS_MAP, v -> new HashMap<>()).put(md.getSimpleName(), testName);
                }
            }
            return anno;
        }

        private @Nullable String getAnnotationArgumentForInitMethod(Annotated annotated, String... variableNames) {
            for (String variableName : variableNames) {
                Optional<Literal> attribute = annotated.getAttribute(variableName);
                if (attribute.isPresent()) {
                    return attribute.get().getString();
                }
            }
            return null;
        }

        private boolean isSupportedCsvParam(Annotated annotated) {
            if (annotated.getTree().getArguments() == null || annotated.getTree().getArguments().size() != 1) {
                return false;
            }
            Optional<Literal> value = annotated.getDefaultAttribute("value");
            return value.isPresent() &&
                    value.get().isArray() &&
                    !doTestParamsHaveCustomConverter(getCursor().firstEnclosing(J.MethodDeclaration.class));
        }

        private boolean doTestParamsHaveCustomConverter(J.@Nullable MethodDeclaration method) {
            if (method == null) {
                return false;
            }
            return method.getParameters().stream()
                    .filter(param -> param instanceof J.VariableDeclarations)
                    .map(J.VariableDeclarations.class::cast)
                    .anyMatch(v -> v.getLeadingAnnotations().stream().anyMatch(CONVERTER_MATCHER::matches));
        }

        private static JavaTemplate getCsVParamTemplate(ExecutionContext ctx) {
            return JavaTemplate.builder("@CsvSource(#{any(java.lang.String[])})")
                    .imports("org.junit.jupiter.params.provider.CsvSource")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-params"))
                    .build();
        }
    }

    /***
     * Case 1.
     * - Test has Parameters annotation without arguments and initMethods has match
     * case 2.
     * - Test has Parameters(method = "...") annotation with defined method source
     * case 3.
     * - Test has Parameters(named = "...") and NamedParameters annotation
     */
    private static class ParametersNoArgsImplicitMethodSource extends JavaIsoVisitor<ExecutionContext> {

        private final Set<String> initMethods;
        private final Set<String> unsupportedConversions;
        private final Set<String> parameterizedTests;
        private final Map<String, String> initMethodReferences;

        private final JavaTemplate parameterizedTestTemplate;
        private final JavaTemplate parameterizedTestTemplateWithName;
        private final JavaTemplate methodSourceTemplate;

        public ParametersNoArgsImplicitMethodSource(Set<String> initMethods, Map<String, String> initMethodReferences, Set<String> unsupportedConversions, Set<String> parameterizedTests, ExecutionContext ctx) {
            this.initMethods = initMethods;
            this.initMethodReferences = initMethodReferences;
            this.unsupportedConversions = unsupportedConversions;
            this.parameterizedTests = parameterizedTests;

            // build @ParameterizedTest template
            JavaParser.Builder<?, ?> javaParser = JavaParser.fromJavaVersion()
                    .classpathFromResources(ctx, "junit-jupiter-api-5", "hamcrest-3", "junit-jupiter-params-5");
            this.parameterizedTestTemplate = JavaTemplate.builder("@ParameterizedTest")
                    .javaParser(javaParser)
                    .imports("org.junit.jupiter.params.ParameterizedTest").build();
            // build @ParameterizedTest(#{}) template
            this.parameterizedTestTemplateWithName = JavaTemplate.builder("@ParameterizedTest(name = \"#{}\")")
                    .javaParser(javaParser)
                    .imports("org.junit.jupiter.params.ParameterizedTest").build();
            // build @MethodSource("...") template
            this.methodSourceTemplate = JavaTemplate.builder("@MethodSource(#{})")
                    .javaParser(javaParser)
                    .imports("org.junit.jupiter.params.provider.MethodSource").build();
        }

        @SuppressWarnings("SpellCheckingInspection")
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            // Remove @RunWith(JUnitParamsRunner.class) annotation
            doAfterVisit(new RemoveAnnotationVisitor(RUN_WITH_JUNIT_PARAMS_ANNOTATION_MATCHER));
            List<String> methodNames = getCursor().getMessage(MakeMethodStatic.REFERENCED_METHODS, emptyList());
            if (cd.getType() != null && !methodNames.isEmpty()) {
                doAfterVisit(new MakeMethodStatic(cd.getType(), methodNames));
            }

            // Update Imports
            maybeRemoveImport("org.junit.Test");
            maybeRemoveImport("org.junit.jupiter.api.Test");
            maybeRemoveImport("org.junit.runner.RunWith");
            maybeRemoveImport("junitparams.JUnitParamsRunner");
            maybeRemoveImport("junitparams.Parameters");
            maybeRemoveImport("junitparams.NamedParameters");
            maybeRemoveImport("junitparams.naming.TestCaseName");
            maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
            maybeAddImport("org.junit.jupiter.params.provider.MethodSource");
            maybeAddImport("org.junit.jupiter.params.provider.CsvSource");
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (unsupportedConversions.contains(method.getSimpleName())) {
                return method;
            }
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            final String paramTestName = initMethodReferences.get(m.getSimpleName());

            List<J.Annotation> annotations = ListUtils.map(m.getLeadingAnnotations(), anno -> {
                if (TEST_CASE_NAME_MATCHER.matches(anno) || NAMED_PARAMETERS_MATCHER.matches(anno)) {
                    return null;
                }
                anno = maybeReplaceTestAnnotation(new Cursor(getCursor(), anno), paramTestName);
                anno = maybeReplaceParametersAnnotation(new Cursor(getCursor(), anno), method.getSimpleName());
                return anno;
            });
            m = m.withLeadingAnnotations(annotations);

            // If the method is an init-method then add a static modifier if necessary
            if (initMethods.contains(m.getSimpleName()) || initMethodReferences.containsValue(m.getSimpleName())) {
                Cursor enclosingCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                enclosingCursor.computeMessageIfAbsent(MakeMethodStatic.REFERENCED_METHODS, k -> new ArrayList<>()).add(m.getSimpleName());
            }
            return maybeAutoFormat(method, m, m.getName(), ctx, getCursor().getParentTreeCursor());
        }

        private J.Annotation maybeReplaceTestAnnotation(Cursor anno, @Nullable String parameterizedTestArgument) {
            if (JUPITER_TEST_ANNOTATION_MATCHER.matches(anno.getValue()) || JUNIT_TEST_ANNOTATION_MATCHER.matches(anno.getValue())) {
                if (!parameterizedTests.contains(anno.firstEnclosing(J.MethodDeclaration.class).getSimpleName())) {
                    return anno.getValue();
                }
                if (parameterizedTestArgument == null) {
                    return parameterizedTestTemplate.apply(anno, ((J.Annotation) anno.getValue()).getCoordinates().replace());
                } else {
                    return parameterizedTestTemplateWithName.apply(anno, ((J.Annotation) anno.getValue()).getCoordinates().replace(),
                            parameterizedTestArgument);
                }
            }
            return anno.getValue();
        }

        private J.Annotation maybeReplaceParametersAnnotation(Cursor anno, String methodName) {
            if (PARAMETERS_MATCHER.matches(anno.getValue())) {
                String initMethodName = junitParamsDefaultInitMethodName(methodName);
                if (initMethods.contains(initMethodName)) {
                    return methodSourceTemplate.apply(anno, ((J.Annotation) anno.getValue()).getCoordinates().replace(), "\"" + initMethodName + "\"");
                } else {
                    String annotationArg = getAnnotationArgumentValueForMethodTemplate(anno.getValue());
                    if (annotationArg != null) {
                        return methodSourceTemplate.apply(anno, ((J.Annotation) anno.getValue()).getCoordinates().replace(), annotationArg);
                    }
                }
            }
            return anno.getValue();
        }

        private @Nullable String getAnnotationArgumentValueForMethodTemplate(J.Annotation anno) {
            String annotationArgumentValue = null;
            if (anno.getArguments() != null && anno.getArguments().size() == 1) {
                Expression annoArg = anno.getArguments().get(0);
                if (annoArg instanceof J.Literal) {
                    annotationArgumentValue = (String) ((J.Literal) annoArg).getValue();
                } else if (annoArg instanceof J.Assignment && (((J.Assignment) annoArg).getAssignment()) instanceof J.Literal) {
                    annotationArgumentValue = (String) ((J.Literal) ((J.Assignment) annoArg).getAssignment()).getValue();
                }
            }
            if (initMethodReferences.containsKey(annotationArgumentValue)) {
                annotationArgumentValue = initMethodReferences.get(annotationArgumentValue);
            }

            if (annotationArgumentValue != null) {
                String[] methodRefs = annotationArgumentValue.split(",");
                if (methodRefs.length > 1) {
                    String methods = Arrays.stream(methodRefs).map(mref -> "\"" + mref + "\"").collect(Collectors.joining(", "));
                    annotationArgumentValue = "{" + methods + "}";
                } else {
                    annotationArgumentValue = "\"" + annotationArgumentValue + "\"";
                }
            }
            return annotationArgumentValue;
        }

    }

    @RequiredArgsConstructor
    private static class MakeMethodStatic extends JavaIsoVisitor<ExecutionContext> {

        private static final String REFERENCED_METHODS = "referencedMethods";

        private final JavaType.FullyQualified classType;
        private final List<String> methodNames;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            if (classDeclaration.getType() != classType) {
                return classDeclaration;
            }
            J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);
            List<String> methodNames = getCursor().getMessage(REFERENCED_METHODS, emptyList());
            if (!methodNames.isEmpty()) {
                doAfterVisit(new MakeMethodStatic(classType, methodNames));
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (!methodNames.contains(method.getSimpleName()) ||
                    method.hasModifier(J.Modifier.Type.Static) ||
                    method.getMethodType() == null ||
                    method.getMethodType().getDeclaringType() != classType) {
                return method;
            }
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            J.Modifier staticModifier = new J.Modifier(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY, null, J.Modifier.Type.Static, new ArrayList<>());
            return m.withModifiers(ListUtils.concat(m.getModifiers(), staticModifier));
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodDeclaration enclosingMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
            if (enclosingMethod != null &&
                    method.getMethodType() != null &&
                    methodNames.contains(enclosingMethod.getSimpleName()) &&
                    method.getMethodType().getDeclaringType() == classType) {
                Cursor classCursor = getCursor().dropParentUntil(j -> j instanceof J.ClassDeclaration);
                classCursor.computeMessageIfAbsent(REFERENCED_METHODS, k -> new ArrayList<>()).add(method.getSimpleName());
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
