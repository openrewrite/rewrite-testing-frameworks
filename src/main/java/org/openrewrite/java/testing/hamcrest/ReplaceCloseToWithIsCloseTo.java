package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;

@SuppressWarnings("ALL")
public class ReplaceCloseToWithIsCloseTo extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate Hamcrest `closeTo(..)` to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate from Hamcrest `closeTo(..)` to AssertJ assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ReplaceCloseToWithIsCloseToVisitor();
    }

    private static final class ReplaceCloseToWithIsCloseToVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
        // TODO make sure recipe works for every version of closeTo() method
        private final MethodMatcher CLOSE_TO_MATCHER = new MethodMatcher("org.hamcrest.Matchers closeTo(..)");
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);
            if (!ASSERT_THAT_MATCHER.matches(m)) {
                return m;
            }

            List<Expression> arguments = m.getArguments();
            Expression firstArgument = arguments.get(0);
            if (!(arguments.get(1) instanceof J.MethodInvocation)) {
                return m;
            }

            J.MethodInvocation methodArgument = (J.MethodInvocation) arguments.get(1);
            if (!CLOSE_TO_MATCHER.matches(methodArgument)) {
                return m;
            }

            Expression targetNumber = methodArgument.getArguments().get(0);
            Expression withinNumber = methodArgument.getArguments().get(1);

            maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
            maybeRemoveImport("org.hamcrest.Matchers.closeTo");
            maybeAddImport("org.assertj.core.api.Assertions", "within");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            return JavaTemplate.builder("assertThat(#{any()}).isCloseTo(#{any()}, within(#{any()}))")
                    .staticImports("org.assertj.core.api.Assertions.assertThat",
                                   "org.assertj.core.api.Assertions.within")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), m.getCoordinates().replace(), firstArgument, targetNumber, withinNumber);
        }
    }
}
