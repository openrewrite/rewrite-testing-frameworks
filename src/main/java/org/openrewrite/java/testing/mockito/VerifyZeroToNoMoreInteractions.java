package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.DependencyInsight;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;

public class VerifyZeroToNoMoreInteractions extends ScanningRecipe<AtomicBoolean> {

    private static final MethodMatcher ASSERT_INSTANCE_OF_MATCHER = new MethodMatcher("org.mockito.Mockito verifyZeroInteractions(..)", true);

    @Override
    public String getDisplayName() {
        return "Replace `verifyZeroInteractions() to `verifyNoMoreInteractions()";
    }

    @Override
    public String getDescription() {
        return "Replaces `verifyZeroInteractions()` with `verifyNoMoreInteractions()` in Mockito tests when migration when using a Mockito version < 3.x.";
    }

    @Override
    public AtomicBoolean getInitialValue(final ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean usingOlderMockito) {
        TreeVisitor<?, ExecutionContext> div = new DependencyInsight("org.mockito", "mockito-*", "[1.0,3.0)", null).getVisitor();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!usingOlderMockito.get() && div.visit(tree, ctx) != tree) {
                    usingOlderMockito.set(true);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean usingOlderMockito) {
        return Preconditions.check(usingOlderMockito.get(),
                Preconditions.check(new UsesMethod<>(ASSERT_INSTANCE_OF_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation md = super.visitMethodInvocation(method, ctx);

                        if (!ASSERT_INSTANCE_OF_MATCHER.matches(md)) {
                            return md;
                        }

                        maybeAddImport("org.mockito.Mockito", "verifyNoMoreInteractions");
                        maybeRemoveImport("org.mockito.Mockito.verifyZeroInteractions");

                        return method.withName(md.getName().withSimpleName("verifyNoMoreInteractions"));
                    }
                })
        );
    }
}
