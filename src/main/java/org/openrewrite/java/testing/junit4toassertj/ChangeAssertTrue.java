package org.openrewrite.java.testing.junit4toassertj;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindReferencedTypes;
import org.openrewrite.java.search.FindType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

/**
 * This is a refactoring visitor that will convert JUnit-style assertTrue() to assertJ asserts:
 *
 * <PRE>
 * assertTrue("This should be true", EXPRESSION) -> assertThat(EXPRESSION).as("This should be true").isTrue()
 * assertTrue(EXPRESSION) -> assert(EXPRESSION).isTrue()
 * </PRE>
 */
@AutoConfigure
public class ChangeAssertTrue extends JavaIsoRefactorVisitor {

    private static final String FULLY_QUALIFIED_ASSERTIONS = "org.assertj.core.api.Assertions";
    private static final String FULLY_QULIFIED_ABSTRACT_BOOLEAN_ASSERT = "org.assertj.core.api.AbstractBooleanAssert";

    private static final MethodMatcher assertTrueMatcher = new MethodMatcher("org.junit.Assert assertTrue(..)");
    private static final JavaType.Method assertThatMethodDeclaration;

    static {
        JavaType.Method.Signature booleanAssertThatMethod = new JavaType.Method.Signature(JavaType.Class.build(FULLY_QULIFIED_ABSTRACT_BOOLEAN_ASSERT), Collections.singletonList(JavaType.Primitive.Boolean));
        assertThatMethodDeclaration = JavaType.Method.build(
                JavaType.Class.build(FULLY_QUALIFIED_ASSERTIONS),
                "assertThat",
                booleanAssertThatMethod,
                booleanAssertThatMethod,
                Collections.singletonList("arg1"),
                Stream.of(Flag.Public,Flag.Static).collect(Collectors.toSet()));
    }

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
                assertThatMethodDeclaration,
                EMPTY
        );

        if (originalArgs.size() == 2) {
            //If the assertTrue is the two-argument variant, we need to maintain the message via a chained method call to "as".
            //This means the initial method invocation will be the select into an "as" method: "assertThat(<EXPRESSION>).as(<MESSAGE>)"
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

        //Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat"
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
        andThen(op);
    }
}

