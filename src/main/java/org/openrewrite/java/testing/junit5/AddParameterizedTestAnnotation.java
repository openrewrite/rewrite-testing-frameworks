package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
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
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(md, ctx);

                List<J.Annotation> annotations = md.getLeadingAnnotations();

                if (annotations.size() != 2) { return m; }

                J.Annotation firstAnn = annotations.get(0);
                J.Annotation secondAnn = annotations.get(1);

                if (TEST_ANNOTATION_MATCHER.matches(firstAnn) && secondAnn.getArguments() != null) {
                   maybeRemoveImport("org.junit.jupiter.api.Test");
                   maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                   return JavaTemplate.builder("@ParameterizedTest")
                           .javaParser(JavaParser.fromJavaVersion()
                                   .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9.+"))
                           .build()
                           .apply(getCursor(), firstAnn.getCoordinates().replace());
                }else if (firstAnn.getArguments() != null && annotations.size() == 1) {
                    // insert @ParameterizedTest
                }

                return m;
            }
        };
    }
}