package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.tree.J;

public class FindJUnit extends Recipe {

    public static final String JUNIT_REFS_EXIST_KEY = "junitReferencesExist";

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new FindJUnitVisitor();
    }

    private static class FindJUnitVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            boolean junitReferencesExist =
                    !FindTypes.find(cu, "junit.framework.Test").isEmpty()
                            || !FindTypes.find(cu, "org.junit.jupiter.api.Test").isEmpty();
            ctx.putMessage(JUNIT_REFS_EXIST_KEY, junitReferencesExist);
            return cu;
        }
    }
}
