package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import java.util.Comparator;
import java.util.List;

public class AddParameterizedTestAnnotation extends Recipe {
    private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher VALUE_SOURCE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.ValueSource()");
    private static final AnnotationMatcher CSV_SOURCE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.CsvSource()");
    private static final AnnotationMatcher METHOD_SOURCE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.MethodSource()");

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
            public boolean checkForValueAnnotations(J.Annotation ann) {
                return (VALUE_SOURCE_ANNOTATION_MATCHER.matches(ann) ||
                        CSV_SOURCE_ANNOTATION_MATCHER.matches(ann) ||
                        METHOD_SOURCE_ANNOTATION_MATCHER.matches(ann));
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);

                // return early if @ValueSource and siblings are not detected
                if (
                        m.getLeadingAnnotations().stream().noneMatch(VALUE_SOURCE_ANNOTATION_MATCHER::matches) ||
                        m.getLeadingAnnotations().stream().noneMatch(CSV_SOURCE_ANNOTATION_MATCHER::matches)   ||
                        m.getLeadingAnnotations().stream().noneMatch(METHOD_SOURCE_ANNOTATION_MATCHER::matches)
                ) {
                    return m;
                }

                List<J.Annotation> annotations = m.getLeadingAnnotations();

                for (int i = 0; i < annotations.size(); i++) {
                    J.Annotation ann = annotations.get(i);

                    if (TEST_ANNOTATION_MATCHER.matches(ann)) {
                        if (i+1 >= annotations.size()) { continue; }

                        if (checkForValueAnnotations(annotations.get(i+1))) {
                            // replace @Test with @ParameterizedTest
                            JavaCoordinates coordinates = ann.getCoordinates().replace();
                            m = JavaTemplate.builder("@ParameterizedTest")
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
                                    .imports("org.junit.jupiter.params.ParameterizedTest")
                                    .build()
                                    .apply(getCursor(), coordinates);
                            maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                            maybeRemoveImport("org.junit.jupiter.api.Test");
                            break;
                        }
                   }else if (checkForValueAnnotations(ann)) {
                        // add missing @ParameterizedTest annotation
                        JavaCoordinates coordinates = m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName));
                        m = JavaTemplate.builder("@ParameterizedTest")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
                                .imports("org.junit.jupiter.params.ParameterizedTest")
                                .build()
                                .apply(getCursor(), coordinates);
                        maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                        break;
                    }
                }

                return m;
            }
        };
    }
}