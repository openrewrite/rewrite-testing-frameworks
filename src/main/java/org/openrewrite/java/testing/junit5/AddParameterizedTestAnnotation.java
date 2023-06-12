package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;

import java.util.List;

public class AddParameterizedTestAnnotation extends Recipe {
    private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");

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
            // checks if annotations matches ValueSource, CsvSource or MethodSource
            public boolean checkForValueAnnotations(J.Annotation ann) {
                boolean valSourceCheck = (ann.getSimpleName().equals("ValueSource"));
                boolean csvSourceCheck = (ann.getSimpleName().equals("CsvSource"));
                boolean methodSourceCheck = (ann.getSimpleName().equals("MethodSource"));
                return ((valSourceCheck || methodSourceCheck || csvSourceCheck) && ann.getArguments() != null);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);

                // return early if @ValueSource and siblings are not detected
                if (m.getLeadingAnnotations().stream().noneMatch(this::checkForValueAnnotations) &&
                    m.getLeadingAnnotations().stream().noneMatch(TEST_ANNOTATION_MATCHER::matches)) {
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
                    }
                }

                return m;
            }
        };
    }
}