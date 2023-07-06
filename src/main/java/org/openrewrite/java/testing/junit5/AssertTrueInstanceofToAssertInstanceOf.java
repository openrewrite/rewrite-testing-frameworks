package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class AssertTrueInstanceofToAssertInstanceOf extends Recipe {
    @Override
    public String getDisplayName() {
        return "assertTrue(x instanceof y) to assertInstanceOf(y.class, x)";
    }

    @Override
    public String getDescription() {
        return "Migration of JUnit4 (or potentially JUnit5) test case in form of assertTrue(x instanceof y) to assertInstanceOf(y.class, x).";
    }

    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                MethodMatcher junit5Matcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertTrue(* instanceof *,String)");
                MethodMatcher junit4Matcher = new MethodMatcher("org.junit.Assert assertTrue(..,* instanceof *)");

                J clazz;
                Expression expression;
                Expression reason;

                if (junit5Matcher.matches(mi)) {
                    System.out.println("matched");
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertTrue");
                    Expression argument = mi.getArguments().get(0);
                    if (mi.getArguments().size() == 1) {
                        reason = null;
                    } else if (mi.getArguments().size() == 2) {
                        reason = mi.getArguments().get(1);
                    } else return mi;

                    if (argument instanceof J.InstanceOf) {
                        J.InstanceOf instanceOf = (J.InstanceOf) argument;
                        expression = instanceOf.getExpression();
                        clazz = instanceOf.getClazz();
                    } else {
                        return mi;
                    }
                } else if (junit4Matcher.matches(mi)) {
                    maybeRemoveImport("org.junit.Assert.assertTrue");
                    Expression argument;
                    if (mi.getArguments().size() == 1) {
                        reason = null;
                        argument = mi.getArguments().get(0);
                    } else if (mi.getArguments().size() == 2) {
                        reason = mi.getArguments().get(0);
                        argument = mi.getArguments().get(1);
                    } else return mi;

                    if (argument instanceof J.InstanceOf) {
                        J.InstanceOf instanceOf = (J.InstanceOf) argument;
                        expression = instanceOf.getExpression();
                        clazz = instanceOf.getClazz();
                    } else {
                        return mi;
                    }
                } else {
                    return mi;
                }


                JavaTemplate template = JavaTemplate
                    .builder("assertInstanceOf(#{}.class, #{any(java.lang.Object)}" + (reason != null ? ", #{any(java.lang.String)})" : ")"))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "junit-jupiter-api-5.9", "junit-4.13"))
                    .staticImports("org.junit.jupiter.api.Assertions.assertInstanceOf")
                    .build();

                J.MethodInvocation methodd = reason != null
                    ? template.apply(getCursor(), mi.getCoordinates().replace(), clazz.toString(), expression, reason)
                    : template.apply(getCursor(), mi.getCoordinates().replace(), clazz.toString(), expression);
                maybeAddImport("org.junit.jupiter.api.Assertions", "assertInstanceOf");
                return methodd;
            }
        };
    }
}
