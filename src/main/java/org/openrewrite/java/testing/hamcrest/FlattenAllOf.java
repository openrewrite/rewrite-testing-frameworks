package org.openrewrite.java.testing.hamcrest;

import org.openrewrite.*;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

public class FlattenAllOf extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert Hamcrest `allOf(Matcher...)` to individual `assertThat` statements";
    }

    @Override
    public String getDescription() {
        return "Convert Hamcrest `allOf(Matcher...)` to individual `assertThat` statements for easier migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>("org.hamcrest.Matchers allOf(..)"),
                new FlattenAllOfVisitor());
    }
}

class FlattenAllOfVisitor extends JavaVisitor<ExecutionContext> {
    private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private final MethodMatcher ALL_OF_MATCHER = new MethodMatcher("org.hamcrest.Matchers allOf(..)");

    @Override
    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);

        List<Statement> newStatements = new ArrayList<>();
        for (Statement statement : md.getBody().getStatements()) {
            if (statement instanceof J.MethodInvocation) {
                J.MethodInvocation assertThat = (J.MethodInvocation) statement;
                if (ASSERT_THAT_MATCHER.matches(assertThat)) {
                    List<Expression> assertThatArguments = assertThat.getArguments();
                    Expression actual = assertThatArguments.get(assertThatArguments.size() - 2);
                    Expression matcherExpression = assertThatArguments.get(assertThatArguments.size() - 1);
                    if (ALL_OF_MATCHER.matches(matcherExpression)) {
                        J.MethodInvocation allOf = (J.MethodInvocation) matcherExpression;
                        List<Expression> matchers = allOf.getArguments();
                        if (1 < matchers.size()) { // Iterable? Inadvertent use of allOf with single Matcher?
                            for (Expression matcher : matchers) {
                                JavaTemplate template = JavaTemplate.builder("assertThat(#{any(java.lang.Object)}, #{any(org.hamcrest.Matcher)});")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hamcrest-2.2"))
                                        .staticImports("org.hamcrest.MatcherAssert.assertThat")
                                        .build();
                                // TODO figure out correct use of cooridnates and cursor when inserting multiple new statements
                                JavaCoordinates coordinates = newStatements.isEmpty() ?
                                        assertThat.getCoordinates().replace() :
                                        newStatements.get(newStatements.size() - 1).getCoordinates().after();
                                Cursor cursor = null;
                                J.MethodInvocation newAssertThat = template.apply(cursor, coordinates, actual, matcher);
                                newStatements.add(newAssertThat);
                            }
                            continue;
                        }
                    }

                }
            }
            // Keep the original statement
            newStatements.add(statement);
        }

        return md.withBody(md.getBody().withStatements(newStatements));
    }
}
