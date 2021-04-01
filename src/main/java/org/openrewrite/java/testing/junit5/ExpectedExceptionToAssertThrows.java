/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.FindFields;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5 Assertions.
 *
 * Supported ExpectedException methods:
 *      expect(java.lang.Class)
 *      expect(org.hamcrest.Matcher)
 *      expectMessage(java.lang.String)
 *      expectMessage(org.hamcrest.Matcher)
 *      expectCause(org.hamcrest.Matcher)
 *
 * Does not currently support refactors of ExpectedException.isAnyExceptionExpected().
 */
public class ExpectedExceptionToAssertThrows extends Recipe {
    private static final ThreadLocal<JavaParser> ASSERTIONS_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(Arrays.asList(
                    Parser.Input.fromString("" +
                            "package org.junit.jupiter.api;" +
                            "import java.util.function.Supplier;" +
                            "import org.junit.jupiter.api.function.Executable;" +
                            "class AssertThrows {" +
                                "static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable,Supplier<String> messageSupplier){ return null; }" +
                                "static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable,String message){ return null; }" +
                                "static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable){ return null; }" +
                            "}"
                    ),
                    Parser.Input.fromString(
                            "package org.junit.jupiter.api.function;" +
                            "public interface Executable {" +
                                "void execute() throws Throwable;" +
                            "}"
                    )
            )).build());

    @Override
    public String getDisplayName() {
        return "ExpectedException To AssertThrows";
    }

    @Override
    public String getDescription() {
        return "Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5's Assertions.assertThrows.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ExpectedExceptionToAssertThrowsVisitor();
    }

    public static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String EXPECTED_EXCEPTION_FQN = "org.junit.rules.ExpectedException";
        private static final String HAMCREST_MATCHER_FQN = "org.hamcrest.Matchers";
        private static final String EXPECT_INVOCATION_KEY = "expectedExceptionMethodInvocation";
        private static final String EXPECT_MESSAGE_INVOCATION_KEY = "expectMessageMethodInvocation";
        private static final String EXPECT_CAUSE_INVOCATION_KEY = "expectCauseMethodInvocation";

        private static final String CODE_TEMPLATE = "{#{} assertThrows(#{}, () -> { #{} }#{});#{}#{}#{}}";
        private static final String ASSERT_THAT_FORMAT = "assertThat(%s, %s);";

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<J.VariableDeclarations> expectedExceptionFields = FindFields.find(cd, EXPECTED_EXCEPTION_FQN);
            if (!expectedExceptionFields.isEmpty()) {
                // Remove the ExpectedException fields
                List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                statements.removeAll(expectedExceptionFields);
                cd = cd.withBody(cd.getBody().withStatements(statements));

                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.junit.rules.ExpectedException");
            }

            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {

            J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);

            J.MethodInvocation expectMethodInvocation = getCursor().pollMessage(EXPECT_INVOCATION_KEY);
            J.MethodInvocation expectMessageMethodInvocation = getCursor().pollMessage(EXPECT_MESSAGE_INVOCATION_KEY);
            J.MethodInvocation expectCauseMethodInvocation = getCursor().pollMessage(EXPECT_CAUSE_INVOCATION_KEY);
            if (expectMethodInvocation == null &&
                    expectMessageMethodInvocation == null &&
                    expectCauseMethodInvocation == null) {
                return m;
            }

            boolean isExpectArgAMatcher = false;
            if (expectMethodInvocation != null) {
                List<Expression> args = expectMethodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                final Expression expectMethodArg = args.get(0);
                if (expectMethodArg instanceof J.MethodInvocation) {
                    isExpectArgAMatcher = isHamcrestMatcher((J.MethodInvocation) expectMethodArg);
                    if (!isExpectArgAMatcher) {
                        return m;
                    }
                } else {
                    final JavaType.FullyQualified argType = TypeUtils.asFullyQualified(expectMethodArg.getType());
                    if (argType == null || !argType.getFullyQualifiedName().equals("java.lang.Class")) {
                        return m;
                    }
                }
            }

            boolean isExpectMessageArgAMatcher = false;
            if (expectMessageMethodInvocation != null) {
                List<Expression> args = expectMessageMethodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                final Expression expectMessageMethodArg = args.get(0);
                if (expectMessageMethodArg instanceof J.MethodInvocation) {
                    isExpectMessageArgAMatcher = isHamcrestMatcher((J.MethodInvocation) expectMessageMethodArg);
                    if (!isExpectMessageArgAMatcher) {
                        return m;
                    }
                } else {
                    if (!(expectMessageMethodArg instanceof J.Literal &&
                            expectMessageMethodArg.getType() == JavaType.Primitive.String)) {
                        return m;
                    }
                }
            }

            boolean isExpectedCauseArgAMatcher = false;
            if (expectCauseMethodInvocation != null) {
                List<Expression> args = expectCauseMethodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                final Expression expectCauseMethodArg = args.get(0);
                if (expectCauseMethodArg instanceof J.MethodInvocation) {
                    isExpectedCauseArgAMatcher = isHamcrestMatcher((J.MethodInvocation) expectCauseMethodArg);
                    if (!isExpectedCauseArgAMatcher) {
                        return m;
                    }
                }
            }

            final String exceptionName = "exception";
            final String exceptionDeclParam =
                    (isExpectArgAMatcher || isExpectMessageArgAMatcher || isExpectedCauseArgAMatcher) ?
                            "Exception " + exceptionName + " =" : "";

            final String expectedExceptionParam = (expectMethodInvocation == null || isExpectArgAMatcher) ?
                    "Exception.class" : expectMethodInvocation.getArguments().get(0).print();

            assert m.getBody() != null;
            final String printedStatementsParam = getPrintedStatements(m.getBody());

            final String expectedMessageParam = (expectMessageMethodInvocation == null || isExpectMessageArgAMatcher) ?
                    "" : expectMessageMethodInvocation.getArguments().get(0).print();

            final String expectedExceptionAssertThatParam = isExpectArgAMatcher ?
                    String.format(ASSERT_THAT_FORMAT, exceptionName, expectMethodInvocation.getArguments().get(0).print()) : "";

            final String expectedMessageAssertThatParam = isExpectMessageArgAMatcher ?
                    String.format(ASSERT_THAT_FORMAT, exceptionName + ".getMessage()", expectMessageMethodInvocation.getArguments().get(0).print()) : "";

            final String expectCauseAssertThatParam = isExpectedCauseArgAMatcher ?
                    String.format(ASSERT_THAT_FORMAT, exceptionName + ".getCause()", expectCauseMethodInvocation.getArguments().get(0).print()) : "";

            /* Code Template;
                {
                    #{exceptionDeclaration} assertThrows(#{expectedException}, () -> {
                        #{printedStatements}
                    } #{expectedMessage});

                    #{assertThatA}#{assertThatB}#{assertThatC}
                }"
            */
            m = m.withBody(
                    m.getBody().withTemplate(
                            template(CODE_TEMPLATE)
                                    .javaParser(ASSERTIONS_PARSER.get())
                                    .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                                    .staticImports("org.hamcrest.MatcherAssert.assertThat")
                                    .build(),
                            m.getBody().getCoordinates().replace(),
                            exceptionDeclParam,
                            expectedExceptionParam,
                            printedStatementsParam,
                            !StringUtils.isBlank(expectedMessageParam) ? "," + expectedMessageParam : expectedMessageParam,
                            expectedExceptionAssertThatParam,
                            expectedMessageAssertThatParam,
                            expectCauseAssertThatParam
                    )
            );

            maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");
            maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");

            m = m.withBody((J.Block) new AutoFormatVisitor<ExecutionContext>().visit(m.getBody(), ctx, getCursor()));

            return m;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getType() != null && method.getType().getDeclaringType().getFullyQualifiedName().equals(EXPECTED_EXCEPTION_FQN)) {
                if (method.getSimpleName().equals("expect")) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, EXPECT_INVOCATION_KEY, method);
                } else if (method.getSimpleName().equals("expectMessage")) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, EXPECT_MESSAGE_INVOCATION_KEY, method);
                } else if (method.getSimpleName().equals("expectCause")) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, EXPECT_CAUSE_INVOCATION_KEY, method);
                }
            }
            return method;
        }

        private boolean isHamcrestMatcher(J.MethodInvocation method) {
            return method.getArguments().size() == 1 &&
                    method.getType() != null &&
                    method.getType().getDeclaringType() != null &&
                    TypeUtils.isOfClassType(method.getType().getDeclaringType(), HAMCREST_MATCHER_FQN);
        }

        // Remove the ExpectedException invocations, use the remaining statements as the lambda body for Assertions.assertThrows()
        private String getPrintedStatements(J.Block body) {
            List<Statement> statements = body.getStatements().stream()
                    .filter(it -> !isExpectedExceptionMethodInvocation(it))
                    .collect(Collectors.toList());

            StringBuilder printedStatements = new StringBuilder();
            for (Statement stmt : statements) {
                printedStatements.append(stmt.print()).append(';');
            }

            return printedStatements.toString();
        }

        private static boolean isExpectedExceptionMethodInvocation(Statement statement) {
            if (!(statement instanceof J.MethodInvocation)) {
                return false;
            }
            J.MethodInvocation m = (J.MethodInvocation) statement;
            if (m.getType() == null) {
                return false;
            }

            return TypeUtils.isOfClassType(m.getType().getDeclaringType(), EXPECTED_EXCEPTION_FQN);
        }
    }
}
