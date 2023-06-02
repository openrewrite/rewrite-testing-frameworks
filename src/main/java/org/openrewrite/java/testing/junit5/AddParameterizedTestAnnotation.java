package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

public class AddParameterizedTestAnnotation extends Recipe {
    @Override
    public String getDisplayName() {
        return "Add missing @ParameterizedTest annotation when @ValueSource is used or " +
               "replace @Test with @ParameterizedTest";
    }

    @Override
    public String getDescription() {
        return "Add missing @ParameterizedTest annotation when @ValueSource is used or " +
               "replace @Test with @ParameterizedTest";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        AnnotationMatcher annotationMatcher = new AnnotationMatcher("@Test");
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = (J.Annotation) super.visitAnnotation(annotation, ctx);
                if (annotationMatcher.matches(annotation)) {
                    a = annotation;
                }
                return a;
            }
        };
    }
}
