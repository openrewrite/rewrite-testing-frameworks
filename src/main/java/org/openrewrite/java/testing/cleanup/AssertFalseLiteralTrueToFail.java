package org.openrewrite.java.testing.cleanup;

import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class AssertFalseLiteralTrueToFail extends Recipe {
    private static final MethodMatcher ASSERT_FALSE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertFalse(boolean, String)");

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Replace JUnit `assertFalse(true, \"reason\")` with `fail(\"reason\")`";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Using fail is more direct and clear.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_FALSE), new JavaVisitor<ExecutionContext>() {

            JavaParser.Builder<?, ?> javaParser = null;

            private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
                if (javaParser == null) {
                    javaParser = JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9");
                }
                return javaParser;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_FALSE.matches(mi)) {
                    if (mi.getArguments().get(0) instanceof J.Literal) {
                        J.Literal literal = (J.Literal) mi.getArguments().get(0);
                        if (JavaType.Primitive.Boolean.equals(literal.getType()) && Boolean.TRUE.equals(literal.getValue())) {
                            StringBuilder sb = new StringBuilder();
                            if (mi.getSelect() == null) {
                                maybeRemoveImport("org.junit.jupiter.api.Assertions");
                                maybeAddImport("org.junit.jupiter.api.Assertions", "fail");
                            } else {
                                sb.append("Assertions.");
                            }
                            sb.append("fail(#{any(java.lang.String)})");
                            Object[] args = new Object[]{mi.getArguments().get(1)};
                            JavaTemplate t;
                            if (mi.getSelect() == null) {
                                t = JavaTemplate.builder(sb.toString())
                                        .contextSensitive()
                                        .staticImports("org.junit.jupiter.api.Assertions.fail")
                                        .javaParser(javaParser(ctx))
                                        .build();
                            } else {
                                t = JavaTemplate.builder(sb.toString())
                                        .contextSensitive()
                                        .imports("org.junit.jupiter.api.Assertions")
                                        .javaParser(javaParser(ctx))
                                        .build();
                            }
                            return t.apply(updateCursor(mi), mi.getCoordinates().replace(), args);
                        }
                    }
                }
                return mi;
            }
        });
    }
}
