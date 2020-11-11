package org.openrewrite.java.testing.junit4toassertj;

import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

/**
 * This is a refactoring visitor that will convert JUnit-style assertTrue() to assertJ asserts:
 *
 * assertTrue("This should be true", EXPRESSION) -> assert(EXPRESSION).with("This should be true").isTrue()
 * assertTrue(EXPRESSION) -> assert(EXPRESSION).isTrue()
 *
 */
public class ChangeAssertTrue extends JavaIsoRefactorVisitor {

    private static final MethodMatcher assertTrueMatcher = new MethodMatcher("org.junit.Assert assertTrue(..)");

    public ChangeAssertTrue() {
        setCursoringOn();
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu) {

        maybeRemoveImport("org.junit.Assert");
        return super.visitCompilationUnit(cu);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation original = super.visitMethodInvocation(method);
        if (!assertTrueMatcher.matches(method)) {
            return original;
        }

        //The expression that evaluates to true/false is either the first argument (if using the one-argument form)
        //or the second argument (if using the two-argument form)

        List<Expression> originalArgs = original.getArgs().getArgs();
        Expression assertExpression = originalArgs.size() == 1 ? originalArgs.get(0) : originalArgs.get(1);

        //This create the `assertThat(<EXPRESSION>)` method invocation.
        J.MethodInvocation assertSelect = new J.MethodInvocation(
                randomId(),
                null,
                null,
                J.Ident.build(randomId(), "assertThat", JavaType.Primitive.Void, EMPTY),
                new J.MethodInvocation.Arguments(
                        randomId(),
                        Collections.singletonList(assertExpression.withPrefix("")),
                        EMPTY
                ),
                null,
                EMPTY
        );

        if (originalArgs.size() == 2) {
            //If the assertTrue is the two-argument variant, we need to maintain the message via a chained method call to "as".
            //This means the initial method invocation witll be the select into an "as" method: "assertThat(<EXRPESSION>).as(<MESSAGE>)"
            assertSelect = new J.MethodInvocation(
                    randomId(),
                    assertSelect,
                    null,
                    J.Ident.build(randomId(), "as", null, EMPTY),
                    new J.MethodInvocation.Arguments(
                            randomId(),
                            Collections.singletonList(original.getArgs().getArgs().get(0)),
                            EMPTY
                    ),
                    null,
                    EMPTY
            );
        }

        //The method that will always be returned is the "isTrue()" method using the assertSelect as the selector.
        //One argument form : assertThat(<EXPRESSION>).isTrue();
        //Two argument form : assertThat(<EXPRESSION>).as(<MESSAGE>).isTrue();
        J.MethodInvocation replacement = new J.MethodInvocation(
                randomId(),
                assertSelect,
                null,
                J.Ident.build(randomId(), "isTrue", JavaType.Primitive.Boolean, EMPTY),
                new J.MethodInvocation.Arguments(
                        randomId(),
                        Collections.emptyList(),
                        EMPTY
                ),
                null,
                format("\n")
        );

        //Make sure there is a static import for "org.assertj.core.api.Assertions.asserThat"
        maybeAddStaticImport("org.assertj.core.api.Assertions", "assertThat");

        //Format the replacement method invocation in the context of where it is called.
        andThen(new AutoFormat(replacement));
        return replacement;
    }

    /**
     * This method will add a static import method to the compilation unit.
     *
     * @param fullyQualifiedName Fully-qualified name of the class.
     * @param method The static method to be imported.
     */
    public void maybeAddStaticImport(String fullyQualifiedName, String method) {
        AddImport op = new AddImport();
        op.setType(fullyQualifiedName);
        op.setStaticMethod(method);
        op.setOnlyIfReferenced(false);
        andThen(op);
    }
}

