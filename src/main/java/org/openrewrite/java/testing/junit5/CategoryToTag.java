package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;

import java.util.Set;

/**
 * Transforms the Junit4 @Category, which can list multiple categories, into one @Tag annotation per category listed
 */
public class CategoryToTag extends Recipe {
    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new CategoryToTagVisitor();
    }

    public static class CategoryToTagVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl classDecl, ExecutionContext context) {
            J.ClassDecl cd = super.visitClassDecl(classDecl, context);
            Set<J.Annotation> categoryAnnotations = FindAnnotations.find(cd, "org.junit.experimental.categories.Category");
            if(!categoryAnnotations.isEmpty()) {
                
            }
            return cd;
        }
    }
}
