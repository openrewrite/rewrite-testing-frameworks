package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AddParameterizedTestAnnotation extends Recipe {
    private static final String PARAMS_PATH = "@org.junit.jupiter.params.provider.";
    private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher VALUE_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"ValueSource");
    private static final AnnotationMatcher CSV_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"CsvSource");
    private static final AnnotationMatcher METHOD_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"MethodSource");
    private static final AnnotationMatcher NULL_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"NullSource");
    private static final AnnotationMatcher EMPTY_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"EmptySource");
    private static final AnnotationMatcher NULL_EMPTY_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"NullAndEmptySource");
    private static final AnnotationMatcher ENUM_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"EnumSource");
    private static final AnnotationMatcher CSV_FILE_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"CsvFileSource");
    private static final AnnotationMatcher ARG_SOURCE_MATCHER = new AnnotationMatcher(PARAMS_PATH+"ArgumentsSource");
    private static final AnnotationMatcher PARAM_TEST_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.ParameterizedTest");

    @Override
    public String getDisplayName() {
        return "Add missing @ParameterizedTest annotation when @ValueSource is used or " +
               "replace @Test with @ParameterizedTest";
    }

    @Override
    public String getDescription() {
        return "Add missing @ParameterizedTest annotation when @ValueSource is used or " +
               "replace @Test with @ParameterizedTest.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            // checks if annotations matches ValueSource and siblings
            public boolean checkForSourceAnnotation(J.Annotation ann) {
                // must be a better way to writer this
                return (VALUE_SOURCE_MATCHER.matches(ann) || CSV_SOURCE_MATCHER.matches(ann) ||
                        METHOD_SOURCE_MATCHER.matches(ann) || NULL_SOURCE_MATCHER.matches(ann) ||
                        EMPTY_SOURCE_MATCHER.matches(ann) || NULL_EMPTY_SOURCE_MATCHER.matches(ann) ||
                        ENUM_SOURCE_MATCHER.matches(ann) || CSV_FILE_SOURCE_MATCHER.matches(ann) ||
                        ARG_SOURCE_MATCHER.matches(ann));
            }

            public List<J.Annotation> reorderAnnotations(List<J.Annotation> annotations) {
                // if @ParameterizedTest is already at start then return early
                if (PARAM_TEST_MATCHER.matches(annotations.get(0))) { return annotations; }
                List<J.Annotation> ordered = new ArrayList<>(annotations);

                // get @ParameterizedTest
                List<J.Annotation> filteredList = ordered.stream().filter(PARAM_TEST_MATCHER::matches).collect(Collectors.toList());
                J.Annotation paramAnn = filteredList.get(0);

                ordered.remove(paramAnn);
                ordered.add(0, paramAnn);

                return ordered;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);

                // return early if @ValueSource and siblings are not detected
                if (m.getLeadingAnnotations().stream().noneMatch(this::checkForSourceAnnotation) ||
                    m.getLeadingAnnotations().stream().anyMatch(PARAM_TEST_MATCHER::matches)) {
                    return m;
                }

                JavaCoordinates coordinates = m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName));

                for (J.Annotation ann : m.getLeadingAnnotations()) {
                    if (TEST_ANNOTATION_MATCHER.matches(ann)) {
                        coordinates = ann.getCoordinates().replace();
                        break;
                    }
                }

                m = JavaTemplate.builder("@ParameterizedTest")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "junit-jupiter-api-5.9", "junit-jupiter-params-5.9"))
                        .imports("org.junit.jupiter.params.ParameterizedTest")
                        .build()
                        .apply(getCursor(), coordinates);
                maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                maybeRemoveImport("org.junit.jupiter.api.Test");

                return m;
            }
        };
    }
}