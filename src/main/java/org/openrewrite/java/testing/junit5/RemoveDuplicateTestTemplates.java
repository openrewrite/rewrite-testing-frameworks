package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class RemoveDuplicateTestTemplates extends Recipe {
    private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher REPEATED_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.RepeatedTest");

    public String getDisplayName() {
        return "Remove duplicates uses of @TestTemplate implementations for a single method";
    }

    public String getDescription() {
        return "Remove duplicates uses of @TestTemplate implementations for a single method.";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.api.*", false), new RemoveDuplicateTestTemplateVisitor());
    }

    private static class RemoveDuplicateTestTemplateVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);
            // first check if @Test or @RepeatedTest is present, else return early
            if (m.getLeadingAnnotations().stream().noneMatch(TEST_ANNOTATION_MATCHER::matches) ||
                m.getLeadingAnnotations().stream().noneMatch(REPEATED_TEST_ANNOTATION_MATCHER::matches)) {
                return m;
            }

            m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(),
                    ann -> TEST_ANNOTATION_MATCHER.matches(ann) ? null : ann));
            maybeRemoveImport("org.junit.jupiter.api.Test");

            return autoFormat(m, ctx);
        }
    }
}
