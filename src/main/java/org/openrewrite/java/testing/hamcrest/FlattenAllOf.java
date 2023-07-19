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

import javax.naming.ldap.ExtendedRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

@SuppressWarnings("NullableProblems")
class FlattenAllOfVisitor extends JavaVisitor<ExecutionContext> {
    private final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private final MethodMatcher ALL_OF_MATCHER = new MethodMatcher("org.hamcrest.Matchers allOf(..)");

    //@Override
    //public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
    //    J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
    //    if (md.getBody() == null) { return md; }

    //    List<Statement> newStatements = md.getBody().getStatements().stream().map(statement -> {
    //        if (statement instanceof J.MethodInvocation) {
    //            J.MethodInvocation assertThat = (J.MethodInvocation) statement;
    //            if (ASSERT_THAT_MATCHER.matches(assertThat)) {
    //                List<Expression> assertThatArguments = assertThat.getArguments();
    //                Expression actual = assertThatArguments.get(assertThatArguments.size() - 2);
    //                Expression matcherExpression = assertThatArguments.get(assertThatArguments.size() - 1);
    //                if (ALL_OF_MATCHER.matches(matcherExpression)) {
    //                    J.MethodInvocation allOf = (J.MethodInvocation) matcherExpression;
    //                    List<Expression> matchers = allOf.getArguments();
    //                    for (Expression matcher : matchers) {
    //                        JavaTemplate template = JavaTemplate.builder("assertThat(#{any(java.lang.Object)}, #{any(org.hamcrest.Matcher)});")
    //                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hamcrest-2.2"))
    //                                .staticImports("org.hamcrest.MatcherAssert.assertThat")
    //                                .build();
    //                        // TODO figure out correct use of coordinates and cursor when inserting multiple new statements
    //                        JavaCoordinates coordinates = assertThat.getCoordinates().replace();
    //                        Cursor cursor = null;
    //                        return template.apply(getCursor(), coordinates, actual, matcher);
    //                    }
    //                    //continue;
    //                }
    //            }
    //        }
    //        // Keep the original statement
    //        return statement;
    //    }).collect(Collectors.toList());


    //    return md.withBody(md.getBody().withStatements(newStatements));
    //}

    @Override
    public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
        J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(invocation, ctx);

        List<Expression> arguments = mi.getArguments();
        if (!ASSERT_THAT_MATCHER.matches(mi) && !ALL_OF_MATCHER.matches(arguments.get(arguments.size() - 1))) {
            return mi;
        }

        Expression actual = arguments.get(arguments.size() - 2);
        List<Expression> allOfArgs = ((J.MethodInvocation) arguments.get(arguments.size() - 1)).getArguments();

        StringBuilder stringBuilderTemplate = new StringBuilder();
        List<Expression> replacements = new ArrayList<>();
        for (Expression matcher : allOfArgs) {
            stringBuilderTemplate.append("assertThat(#{any(java.lang.Object)}, #{any(org.hamcrest.Matcher)});\n");
            replacements.add(actual);
            replacements.add(matcher);
        }
        JavaTemplate template = JavaTemplate.builder(stringBuilderTemplate.toString())
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "hamcrest-2.2"))
                .staticImports("org.hamcrest.MatcherAssert.assertThat")
                .build();

        return template.apply(getCursor(), mi.getCoordinates().replace(), replacements.toArray());
    }
}
