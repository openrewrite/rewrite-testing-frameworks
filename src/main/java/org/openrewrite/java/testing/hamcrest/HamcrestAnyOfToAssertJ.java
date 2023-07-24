package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("NullableProblems")
public class HamcrestAnyOfToAssertJ extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate `anyOf` Hamcrest Matcher to AssertJ";
    }

    @Override
    public String getDescription() {
        return "Migrate the `anyOf` Hamcrest Matcher to AssertJ's `satisfiesAnyOf` assertion.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AnyOfToAssertJVisitor();
    }

    private static class AnyOfToAssertJVisitor extends JavaVisitor<ExecutionContext> {
        private final MethodMatcher assertThatMatcher = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
        private final MethodMatcher anyOfMatcher = new MethodMatcher("org.hamcrest.Matchers anyOf(..)");
        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(mi, ctx);
            if (!assertThatMatcher.matches(m)) {
                return m;
            }

            if (!anyOfMatcher.matches(m.getArguments().get(1))) {
                return m;
            }

            Expression select = m.getArguments().get(0);
            J.MethodInvocation anyOf = ((J.MethodInvocation)m.getArguments().get(1));
            List<Expression> anyOfArguments = anyOf.getArguments();
            List<Expression> templateFillIns = new ArrayList<>();
            StringBuilder template = new StringBuilder();
            template.append("assertThat(#{any()}).satisfiesAnyOf(");
            templateFillIns.add(select);

            for (Expression exp : anyOfArguments) {
                template.append("\n() -> assertThat(#{any()}, #{any()}),");
                templateFillIns.add(select);
                templateFillIns.add(exp);
            }
            template.deleteCharAt(template.length()-1);
            template.append("\n);");

            JavaTemplate fullTemplate = JavaTemplate.builder(template.toString())
                    .contextSensitive()
                    .staticImports("org.assertj.core.api.Assertions.assertThat")
                    .imports("org.assertj.core.api.AbstractAssert")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3.24", "hamcrest-2.2", "junit-jupiter-api-5.9"))
                    .build();

            maybeAddImport("org.assertj.core.api.AbstractAssert");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            return fullTemplate.apply(getCursor(), m.getCoordinates().replace(), templateFillIns.toArray());
        }
    }
}
