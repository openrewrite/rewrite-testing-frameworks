package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

public class AddParameterizedTestAnnotation extends Recipe {
    private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher VALUE_SOURCE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.ValueSource");
    private static final AnnotationMatcher CSV_SOURCE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.CsvSource");
    private static final AnnotationMatcher METHOD_SOURCE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.MethodSource");
    boolean foundTestAnnotation = false;

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
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation ann = (J.Annotation) super.visitAnnotation(annotation, ctx);
                // check if @Test is present
                if (TEST_ANNOTATION_MATCHER.matches(ann)) { foundTestAnnotation = true; }

                System.out.println(ann.getAnnotationType());
                System.out.println(VALUE_SOURCE_ANNOTATION_MATCHER.matches(ann));

                // check if source annotation is present
                if (
                    VALUE_SOURCE_ANNOTATION_MATCHER.matches(ann) ||
                    CSV_SOURCE_ANNOTATION_MATCHER.matches(ann)   ||
                    METHOD_SOURCE_ANNOTATION_MATCHER.matches(ann)
                ) {
                    System.out.println("passing source conditional");
                    if (foundTestAnnotation) {
                        // replace @Test with @ParameterizedTest
                        System.out.println("REPLACING");
                        //JavaTemplate test = JavaTemplate.builder("@ParameterizedTest").build();
                        //test.apply(getCursor(), annotation.getCoordinates().replace(), annotation);
                    }else {
                        // add @ParameterizedTest
                        System.out.println("ADDING");
                    }
                }

                // this could replace the first conditional check, but let's get that working first
                //foundTestAnnotation = TEST_ANNOTATION_MATCHER.matches(ann);

                System.out.println("--------------");
                return ann;
            }
        };
    }
}