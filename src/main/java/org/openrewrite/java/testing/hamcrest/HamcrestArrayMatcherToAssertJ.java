package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

@SuppressWarnings("NullableProblems")
public class HamcrestArrayMatcherToAssertJ extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate Hamcrest Array Matchers to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest Array Matchers to AssertJ assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new HamcrestArrayMatchersToAssertJVisitor();
    }

    private static class HamcrestArrayMatchersToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher HAMCREST_ARRAY_MATCHER = new MethodMatcher("org.hamcrest.Matchers arrayContains(..)");
        private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (!ASSERT_THAT_MATCHER.matches(mi)) {
                return mi;
            }

            if (mi.getArguments().get(1) instanceof J.MethodInvocation && !HAMCREST_ARRAY_MATCHER.matches(mi.getArguments().get(1))) {
                return mi;
            }


            return mi;
        }
    }
}
