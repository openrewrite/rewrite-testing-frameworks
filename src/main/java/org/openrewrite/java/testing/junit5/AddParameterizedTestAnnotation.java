package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
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
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(md, ctx);

                // TODO Figure out if we're using @Test combined with @ValueSource and siblings; if not, return early

                List<J.Annotation> oldAnnotations = md.getLeadingAnnotations();
                List<J.Annotation> newAnnotations = new ArrayList<>(oldAnnotations);

                for (int i = 0; i < oldAnnotations.size(); i++) {
                    J.Annotation currAnn = oldAnnotations.get(i);

                    if (TEST_ANNOTATION_MATCHER.matches(currAnn)) {
                        if (i + 1 >= oldAnnotations.size()) {break;}

                        if (checkForValueAnnotations(oldAnnotations.get(i + 1))) {
                            // replace @Test with @ParameterizedTest
                            newAnnotations.remove(currAnn);
                            J.Annotation parameterizedTest = JavaTemplate.builder("@ParameterizedTest")
                                            .build()
                                            .apply(getCursor(), currAnn.getCoordinates().replace());
                            newAnnotations.add(parameterizedTest);
                            maybeRemoveImport("org.junit.jupiter.api.Test");
                            maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                        }
                    } else if (checkForValueAnnotations(currAnn)) {
                        // value source annotations being used without @ParameterizedTest
                        J.Annotation parameterizedTest = JavaTemplate.builder("@ParameterizedTest")
                                .build()
                                .apply(getCursor(), currAnn.getCoordinates().replace());
                        newAnnotations.add(0, parameterizedTest);
                        maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                        break;
                    }
                }

                return m.withLeadingAnnotations(newAnnotations);
            }
        };
    }
}