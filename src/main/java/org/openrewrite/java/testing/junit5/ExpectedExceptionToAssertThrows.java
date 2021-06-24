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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5 Assertions.
 * <p>
 * Supported ExpectedException methods:
 * expect(java.lang.Class)
 * expect(org.hamcrest.Matcher)
 * expectMessage(java.lang.String)
 * expectMessage(org.hamcrest.Matcher)
 * expectCause(org.hamcrest.Matcher)
 * <p>
 * Does not currently support migration of ExpectedException.isAnyExceptionExpected().
 */
public class ExpectedExceptionToAssertThrows extends Recipe {
    private static final ThreadLocal<JavaParser> ASSERTIONS_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion().dependsOn(
                    Stream.concat(
                            Parser.Input.fromResource("/META-INF/rewrite/JupiterAssertions.java", "---").stream(),
                            Stream.of(
                                    Parser.Input.fromString(
                                            "package org.junit.jupiter.api.function;" +
                                                    "public interface Executable {" +
                                                    "void execute() throws Throwable;" +
                                                    "}"),
                                    Parser.Input.fromString(
                                            "package org.hamcrest;\n" +
                                                    "public interface Matcher<T> {\n" +
                                                    "    boolean matches(Object var1);\n" +
                                                    "}"),
                                    Parser.Input.fromString(
                                            "package org.hamcrest;\n" +
                                                    "public class MatcherAssert {\n" +
                                                    "    public static <T> void assertThat(T actual, Matcher<? super T> matcher) {}\n" +
                                                    "    public static <T> void assertThat(String reason, T actual, Matcher<? super T> matcher) {}\n" +
                                                    "    public static void assertThat(String reason, boolean assertion) {}\n" +
                                                    "}"))
                    ).collect(Collectors.toList())
            ).build());

    @Override
    public String getDisplayName() {
        return "JUnit 4 `ExpectedException` To JUnit Jupiter's `assertThrows()`";
    }

    @Override
    public String getDescription() {
        return "Replace usages of JUnit 4's `@Rule ExpectedException` with JUnit 5's `Assertions.assertThrows()`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.junit.rules.ExpectedException");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ExpectedExceptionToAssertThrowsVisitor();
    }

    public static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                if (statement instanceof J.VariableDeclarations) {
                    //noinspection ConstantConditions
                    if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
                            "org.junit.rules.ExpectedException")) {
                        maybeRemoveImport("org.junit.Rule");
                        maybeRemoveImport("org.junit.rules.ExpectedException");
                        return null;
                    }
                }
                return statement;
            })));

            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);

            J.MethodInvocation expectMethodInvocation = getCursor().pollMessage("expectedExceptionMethodInvocation");
            J.MethodInvocation expectMessageMethodInvocation = getCursor().pollMessage("expectedExceptionMethodMessageInvocation");
            J.MethodInvocation expectCauseMethodInvocation = getCursor().pollMessage("expectCauseMethodInvocation");

            if (expectMethodInvocation == null &&
                    expectMessageMethodInvocation == null &&
                    expectCauseMethodInvocation == null) {
                return m;
            }

            assert m.getBody() != null;
            J.Block bodyWithoutExpectedExceptionCalls = m.getBody().withStatements(ListUtils.map(m.getBody().getStatements(),
                    statement -> isExpectedExceptionMethodInvocation(statement) ? null : statement));

            boolean isExpectArgAMatcher = false;
            if (expectMethodInvocation != null) {
                List<Expression> args = expectMethodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                Expression expectMethodArg = args.get(0);
                isExpectArgAMatcher = isHamcrestMatcher(expectMethodArg);
                JavaType.FullyQualified argType = TypeUtils.asFullyQualified(expectMethodArg.getType());
                if (!isExpectArgAMatcher && (argType == null || !argType.getFullyQualifiedName().equals("java.lang.Class"))) {
                    return m;
                }
            }

            boolean isExpectMessageArgAMatcher = false;
            if (expectMessageMethodInvocation != null) {
                List<Expression> args = expectMessageMethodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                final Expression expectMessageMethodArg = args.get(0);
                isExpectMessageArgAMatcher = isHamcrestMatcher(expectMessageMethodArg);
                if (!isExpectMessageArgAMatcher && !TypeUtils.isString(expectMessageMethodArg.getType())) {
                    return m;
                }
            }

            boolean isExpectedCauseArgAMatcher = false;
            if (expectCauseMethodInvocation != null) {
                List<Expression> args = expectCauseMethodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                final Expression expectCauseMethodArg = args.get(0);
                isExpectedCauseArgAMatcher = isHamcrestMatcher(expectCauseMethodArg);
                if (!isExpectedCauseArgAMatcher) {
                    return m;
                }
            }

            String exceptionDeclParam = ((isExpectArgAMatcher || isExpectMessageArgAMatcher || isExpectedCauseArgAMatcher)
                    || expectMessageMethodInvocation != null) ?
                    "Throwable exception =" : "";

            Object expectedExceptionParam = (expectMethodInvocation == null || isExpectArgAMatcher) ?
                    "Exception.class" : expectMethodInvocation.getArguments().get(0);

            String templateString = expectedExceptionParam instanceof String ? "#{} assertThrows(#{}, () -> #{});" : "#{} assertThrows(#{any()}, () -> #{});";

            m = m.withTemplate(
                    template(templateString)
                            .javaParser(ASSERTIONS_PARSER::get)
                            .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                            .build(),
                    m.getCoordinates().replaceBody(),
                    exceptionDeclParam,
                    expectedExceptionParam,
                    bodyWithoutExpectedExceptionCalls
            );

            maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");

            if (expectMessageMethodInvocation != null && !isExpectMessageArgAMatcher && m.getBody() != null) {
                m = m.withTemplate(
                        template("assertTrue(exception.getMessage().contains(#{any(java.lang.String)});")
                                .javaParser(ASSERTIONS_PARSER::get)
                                .staticImports("org.junit.jupiter.api.Assertions.assertTrue")
                                .build(),
                        m.getBody().getCoordinates().lastStatement(),
                        expectMessageMethodInvocation.getArguments().get(0)
                );
                maybeAddImport("org.junit.jupiter.api.Assertions", "assertTrue");
            }

            JavaTemplate assertThatTemplate = template("assertThat(#{}, #{any()});")
                    .javaParser(ASSERTIONS_PARSER::get)
                    .staticImports("org.hamcrest.MatcherAssert.assertThat")
                    .build();

            assert m.getBody() != null;
            if (isExpectArgAMatcher) {
                m = m.withTemplate(assertThatTemplate, m.getBody().getCoordinates().lastStatement(),
                        "exception", expectMethodInvocation.getArguments().get(0));
                maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            }

            assert m.getBody() != null;
            if (isExpectMessageArgAMatcher) {
                m = m.withTemplate(assertThatTemplate, m.getBody().getCoordinates().lastStatement(),
                        "exception.getMessage()", expectMessageMethodInvocation.getArguments().get(0));
                maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            }

            assert m.getBody() != null;
            if (isExpectedCauseArgAMatcher) {
                m = m.withTemplate(assertThatTemplate, m.getBody().getCoordinates().lastStatement(),
                        "exception.getCause()", expectCauseMethodInvocation.getArguments().get(0));
                maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            }

            return m;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getType() != null && method.getType().getDeclaringType().getFullyQualifiedName().equals("org.junit.rules.ExpectedException")) {
                switch (method.getSimpleName()) {
                    case "expect":
                        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "expectedExceptionMethodInvocation", method);
                        break;
                    case "expectMessage":
                        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "expectedExceptionMethodMessageInvocation", method);
                        break;
                    case "expectCause":
                        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "expectCauseMethodInvocation", method);
                        break;
                }
            }
            return method;
        }

        private boolean isHamcrestMatcher(J j) {
            if (!(j instanceof J.MethodInvocation)) {
                return false;
            }

            final J.MethodInvocation method = (J.MethodInvocation) j;
            return method.getArguments().size() == 1 &&
                    method.getType() != null &&
                    TypeUtils.isOfClassType(method.getType().getDeclaringType(), "org.hamcrest.Matchers");
        }

        private static boolean isExpectedExceptionMethodInvocation(Statement statement) {
            if (!(statement instanceof J.MethodInvocation)) {
                return false;
            }

            J.MethodInvocation m = (J.MethodInvocation) statement;
            if (m.getType() == null) {
                return false;
            }

            return TypeUtils.isOfClassType(m.getType().getDeclaringType(), "org.junit.rules.ExpectedException");
        }
    }
}
