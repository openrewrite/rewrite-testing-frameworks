package org.openrewrite.java.testing.assertj;

import lombok.AllArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
@AllArgsConstructor
public class SimplifyChainedAssertJAssertions extends Recipe {
    @Option(displayName = "AssertJ Assertion",
            description = "The chained AssertJ assertion to move to dedicated assertion.",
            example = "equals",
            required = false)
    @Nullable
    String chainedAssertion;

    @Option(displayName = "",
            description = "",
            example = "",
            required = false)
    @Nullable
    String assertToReplace;

    @Option(displayName = "AssertJ Assertion",
            description = "The AssertJ method to migrate to.",
            example = "isEqualTo",
            required = false)
    @Nullable
    String dedicatedAssertion;

    @Override
    public String getDisplayName() {
        return "Simplify AssertJ chained assertions";
    }

    @Override
    public String getDescription() {
        return "Many AssertJ chained assertions have dedicated assertions that function the same. " +
               "It is best to use the dedicated assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyChainedAssertJAssertionsVisitor();
    }

    private class SimplifyChainedAssertJAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertThat(..)");
        private final MethodMatcher CHAINED_ASSERT_MATCHER = new MethodMatcher("java..* " + chainedAssertion + "(..)");
        private final MethodMatcher ASSERT_TO_REPLACE = new MethodMatcher("org.assertj.core.api.AbstractAssert " + assertToReplace + "()");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            //  assert has assertion
            if (!ASSERT_TO_REPLACE.matches(mi)) {
                return mi;
            }

            J.MethodInvocation dedicatedSelect = (J.MethodInvocation)mi.getSelect();
            //assertThat has method call
            if (!ASSERT_THAT_MATCHER.matches(dedicatedSelect) && !(dedicatedSelect.getArguments().get(0) instanceof J.MethodInvocation)) {
                return mi;
            }

            J.MethodInvocation firstArgument = (J.MethodInvocation) dedicatedSelect.getArguments().get(0);
            Expression select = firstArgument;
            //  method call has select
            if (firstArgument.getSelect() != null) {
                select = firstArgument.getSelect();
            }

            String stringTemplate = mi.getArguments().size() == 0
                    ? String.format("assertThat(%s).%s(%s)", select, dedicatedAssertion, mi.getArguments().get(0))
                    : String.format("assertThat(#{any()}).%s()", dedicatedAssertion);

            return JavaTemplate.builder(stringTemplate)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9", "assertj-core-3.24"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), select);
        }
    }
}
