package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class ReplaceHasCauseReferenceWithCause extends Recipe {

    private static final MethodMatcher HAS_CAUSE_REFERENCE_MATCHER =
            new MethodMatcher("org.assertj.core.api.AbstractThrowableAssert hasCauseReference(java.lang.Throwable)");

    @Override
    public String getDisplayName() {
        return "Replace AssertJ `hasCauseReference()` with `cause().isEqualTo()`";
    }

    @Override
    public String getDescription() {
        return "Replaces AssertJ `hasCauseReference(Throwable)` with `cause().isEqualTo(Throwable)` for better chaining.";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private JavaTemplate template(ExecutionContext ctx) {
                return JavaTemplate.builder(this::getCursor, "cause().isEqualTo(#{any(java.lang.Throwable)})")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                    .build();
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (HAS_CAUSE_REFERENCE_MATCHER.matches(m)) {
                    if (m.getSelect() == null) {
                        return m; // Should not happen with AssertJ fluent assertions
                    }
                    maybeAddImport("org.assertj.core.api.Assertions");
                    maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
                    m = m.withTemplate(template(ctx),
                            m.getCoordinates().replace(),
                            m.getArguments().get(0));
                }
                return m;
            }
        };
    }
}
