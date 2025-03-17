package org.openrewrite.java.testing.mockito;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

public class PowerMockitoWhenNewToMockito extends Recipe {

    private static final MethodMatcher PM_WHEN_NEW = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(..)");

    @Override
    public String getDisplayName() {
        return "Replace `PowerMockito.whenNew` with Mockito counterpart";
    }

    @Override
    public String getDescription() {
        return "Replaces `PowerMockito.whenNew` calls with respective `Mockito.whenConstructed` calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
         return Preconditions.check(
                 new UsesMethod<>(PM_WHEN_NEW),
                 new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (PM_WHEN_NEW.matches(method)) {
                    maybeRemoveImport("org.powermock.api.mockito.PowerMockito");
                    maybeAddImport("org.mockito.Mockito", "whenConstructed");
                    // TODO add cursor message
                }
                return super.visitMethodInvocation(method, executionContext);
            }
        });
    }
}
