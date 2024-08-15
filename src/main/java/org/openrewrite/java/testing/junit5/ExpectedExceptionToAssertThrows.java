/*
 * Copyright 2021 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.List;

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

    @Override
    public String getDisplayName() {
        return "JUnit 4 `ExpectedException` To JUnit Jupiter's `assertThrows()`";
    }

    @Override
    public String getDescription() {
        return "Replace usages of JUnit 4's `@Rule ExpectedException` with JUnit 5's `Assertions.assertThrows()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.rules.ExpectedException", false), new ExpectedExceptionToAssertThrowsVisitor());
    }

    public static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private JavaParser.@Nullable Builder<?, ?> javaParser;

        private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
            if (javaParser == null) {
                javaParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "junit-jupiter-api-5.9", "hamcrest-2.2");
            }
            return javaParser;

        }

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
                if (!isExpectArgAMatcher && (argType == null || !"java.lang.Class".equals(argType.getFullyQualifiedName()))) {
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
                    "Throwable exception = " : "";

            Object expectedExceptionParam = (expectMethodInvocation == null || isExpectArgAMatcher) ?
                    "Exception.class" : expectMethodInvocation.getArguments().get(0);

            String templateString = expectedExceptionParam instanceof String ? "#{}assertThrows(#{}, () -> #{any()});" : "#{}assertThrows(#{any()}, () -> #{any()});";

            Statement statement = bodyWithoutExpectedExceptionCalls.getStatements().size() == 1 &&
                                  !(bodyWithoutExpectedExceptionCalls.getStatements().get(0) instanceof J.Throw) ?
                    bodyWithoutExpectedExceptionCalls.getStatements().get(0) : bodyWithoutExpectedExceptionCalls;
            m = JavaTemplate.builder(templateString)
                    .contextSensitive()
                    .javaParser(javaParser(ctx))
                    .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                    .build()
                    .apply(
                            updateCursor(m),
                            m.getCoordinates().replaceBody(),
                            exceptionDeclParam,
                            expectedExceptionParam,
                            statement
                    );

            // Clear out any declared thrown exceptions
            List<NameTree> thrown = m.getThrows();
            if (thrown != null && !thrown.isEmpty()) {
                assert m.getBody() != null;
                m = m.withBody(m.getBody().withPrefix(thrown.get(0).getPrefix())).withThrows(Collections.emptyList());
            }

            maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");

            if (expectMessageMethodInvocation != null && !isExpectMessageArgAMatcher && m.getBody() != null) {
                m = JavaTemplate.builder("assertTrue(exception.getMessage().contains(#{any(java.lang.String)}));")
                        .contextSensitive()
                        .javaParser(javaParser(ctx))
                        .staticImports("org.junit.jupiter.api.Assertions.assertTrue")
                        .build()
                        .apply(
                                updateCursor(m),
                                m.getBody().getCoordinates().lastStatement(),
                                expectMessageMethodInvocation.getArguments().get(0)
                        );
                maybeAddImport("org.junit.jupiter.api.Assertions", "assertTrue");
            }

            JavaTemplate assertThatTemplate = JavaTemplate.builder("assertThat(#{}, #{any()});")
                    .contextSensitive()
                    .javaParser(javaParser(ctx))
                    .staticImports("org.hamcrest.MatcherAssert.assertThat")
                    .build();

            assert m.getBody() != null;
            if (isExpectArgAMatcher) {
                m = assertThatTemplate.apply(updateCursor(m), m.getBody().getCoordinates().lastStatement(),
                        "exception", expectMethodInvocation.getArguments().get(0));
                maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            }

            assert m.getBody() != null;
            if (isExpectMessageArgAMatcher) {
                m = assertThatTemplate.apply(updateCursor(m), m.getBody().getCoordinates().lastStatement(),
                        "exception.getMessage()", expectMessageMethodInvocation.getArguments().get(0));
                maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            }

            assert m.getBody() != null;
            if (isExpectedCauseArgAMatcher) {
                m = assertThatTemplate.apply(updateCursor(m), m.getBody().getCoordinates().lastStatement(),
                        "exception.getCause()", expectCauseMethodInvocation.getArguments().get(0));
                maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            }

            return m;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getMethodType() != null && "org.junit.rules.ExpectedException".equals(method.getMethodType().getDeclaringType().getFullyQualifiedName())) {
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
                   method.getMethodType() != null &&
                   TypeUtils.isOfClassType(method.getMethodType().getDeclaringType(), "org.hamcrest.Matchers");
        }

        private static boolean isExpectedExceptionMethodInvocation(Statement statement) {
            if (!(statement instanceof J.MethodInvocation)) {
                return false;
            }

            J.MethodInvocation m = (J.MethodInvocation) statement;
            if (m.getMethodType() == null) {
                return false;
            }

            return TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.rules.ExpectedException");
        }
    }
}
