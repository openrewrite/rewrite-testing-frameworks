/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.junit5;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

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

    private static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String FIRST_EXPECTED_EXCEPTION_METHOD_INVOCATION = "firstExpectedExceptionMethodInvocation";
        private static final String STATEMENTS_AFTER_EXPECT_EXCEPTION = "statementsAfterExpectException";
        private static final String HAS_MATCHER = "hasMatcher";
        private static final String EXCEPTION_CLASS = "exceptionClass";

        private static final MethodMatcher EXPECTED_EXCEPTION_ALL_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expect*(..)");
        private static final MethodMatcher EXPECTED_EXCEPTION_CLASS_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expect(java.lang.Class)");
        private static final MethodMatcher EXPECTED_MESSAGE_STRING_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expectMessage(java.lang.String)");
        private static final MethodMatcher EXPECTED_MESSAGE_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expectMessage(org.hamcrest.Matcher)");
        private static final MethodMatcher EXPECTED_EXCEPTION_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expect(org.hamcrest.Matcher)");
        private static final MethodMatcher EXPECTED_EXCEPTION_CAUSE_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expectCause(org.hamcrest.Matcher)");

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
            doAfterVisit(new LambdaBlockToExpression().getVisitor());
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            if (getCursor().pollMessage("hasExpectException") != null) {
                List<NameTree> thrown = m.getThrows();
                if (thrown != null && !thrown.isEmpty()) {
                    assert m.getBody() != null;
                    return m.withBody(m.getBody().withPrefix(thrown.get(0).getPrefix())).withThrows(Collections.emptyList());
                }
            }
            return m;
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            List<Statement> statementsAfterExpectException = getCursor().pollMessage(STATEMENTS_AFTER_EXPECT_EXCEPTION);
            if (statementsAfterExpectException == null) {
                return b;
            }
            J.Block statementsAfterExpectExceptionBlock = new J.Block(randomId(), Space.EMPTY,
                    Markers.EMPTY, new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
                    emptyList(), Space.format(" ")).withStatements(statementsAfterExpectException);
            String exceptionDeclParam = getCursor().pollMessage(HAS_MATCHER) != null ? "Throwable exception = " : "";
            Object exceptionClass = getCursor().pollMessage(EXCEPTION_CLASS);
            if (exceptionClass == null) {
                exceptionClass = "Exception.class";
            }

            maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows", false);
            Statement firstExpectedExceptionMethodInvocation = getCursor().getMessage(FIRST_EXPECTED_EXCEPTION_METHOD_INVOCATION);
            String templateString = exceptionClass instanceof String ? "#{}assertThrows(#{}, () -> #{any()});" : "#{}assertThrows(#{any()}, () -> #{any()});";
            b = JavaTemplate.builder(templateString)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5", "hamcrest-3"))
                    .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                    .build()
                    .apply(
                            updateCursor(b),
                            firstExpectedExceptionMethodInvocation.getCoordinates().before(),
                            exceptionDeclParam,
                            exceptionClass,
                            statementsAfterExpectExceptionBlock
                    );
            Cursor updateCursor = updateCursor(b);
            AtomicBoolean removeStatement = new AtomicBoolean(false);
            J.Identifier exceptionIdentifier = new J.Identifier(Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    emptyList(),
                    "exception",
                    JavaType.ShallowClass.build("java.lang.Throwable"),
                    null);
            b =  b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                if (statement instanceof J.MethodInvocation) {
                    if (EXPECTED_EXCEPTION_ALL_MATCHER.matches((J.MethodInvocation) statement)) {
                        removeStatement.set(true);
                        return getExpectExceptionTemplate((J.MethodInvocation) statement, ctx)
                                .<J.MethodInvocation>map(t -> t.apply(
                                        new Cursor(updateCursor, statement),
                                        statement.getCoordinates().replace(), exceptionIdentifier,
                                        ((J.MethodInvocation) statement).getArguments().get(0)))
                                .orElse(null);
                    }
                }
                return removeStatement.get() ? null : statement;
            }));
            Statement lastStatement = b.getStatements().get(b.getStatements().size() - 1);
            if (!findSuccessorStatements(new Cursor(updateCursor(b), lastStatement)).isEmpty()) {
                J.Return returnStatement = new J.Return(randomId(), b.getStatements().get(b.getStatements().size() - 1).getPrefix().withComments(emptyList()), Markers.EMPTY, null);
                return b.withStatements(ListUtils.concat(b.getStatements(), returnStatement));
            }
            return b;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!EXPECTED_EXCEPTION_ALL_MATCHER.matches(method)) {
                return method;
            }
            getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance).putMessage("hasExpectException", true );
            getCursor().dropParentUntil(J.Block.class::isInstance).computeMessageIfAbsent(FIRST_EXPECTED_EXCEPTION_METHOD_INVOCATION, k -> method);
            List<Statement> successorStatements = findSuccessorStatements(getCursor());
            getCursor().putMessageOnFirstEnclosing(J.Block.class, STATEMENTS_AFTER_EXPECT_EXCEPTION, successorStatements);
            if (EXPECTED_EXCEPTION_CLASS_MATCHER.matches(method)) {
                getCursor().putMessageOnFirstEnclosing(J.Block.class, EXCEPTION_CLASS, method.getArguments().get(0));
            } else {
                getCursor().putMessageOnFirstEnclosing(J.Block.class, HAS_MATCHER, true);
            }
            return method;
        }

        /**
         * From the current cursor point find all the next statements that can be executed in the current path.
         */
        private List<Statement> findSuccessorStatements(Cursor cursor) {
            if (cursor.firstEnclosing(J.MethodDeclaration.class) == null) {
                return Collections.emptyList();
            }
            List<Statement> successorStatements = new ArrayList<>();
            Cursor cursorJustBeforeBlock = getCursor();
            while (!(cursor.getValue() instanceof J.MethodDeclaration)) {
                if (!(cursor.getValue() instanceof J.Block)) {
                    cursorJustBeforeBlock = cursor;
                    cursor = cursor.getParentTreeCursor();
                    continue;
                }
                J.Block block = cursor.getValue();
                boolean found = false;
                for (Statement statement : block.getStatements()) {
                    if (found) {
                        successorStatements.add(statement);
                    } else if (statement == cursorJustBeforeBlock.getValue()) {
                        found = true;
                    }
                }
                cursor = cursor.getParentTreeCursor();
            }
            return successorStatements;
        }

        private Optional<JavaTemplate> getExpectExceptionTemplate(J.MethodInvocation method, ExecutionContext ctx) {
            String template = null;
            if (EXPECTED_MESSAGE_STRING_MATCHER.matches(method)) {
                maybeAddImport("org.hamcrest.CoreMatchers", "containsString");
                template = "assertThat(#{any(java.lang.Throwable)}.getMessage(), containsString(#{any(java.lang.String)}))";
            } else if (EXPECTED_MESSAGE_MATCHER.matches(method)) {
                template = "assertThat(#{any(java.lang.Throwable)}.getMessage(), #{any(org.hamcrest.Matcher)})";
            } else if (EXPECTED_EXCEPTION_MATCHER.matches(method)) {
                template = "assertThat(#{any(java.lang.Throwable)}, #{any(org.hamcrest.Matcher)})";
            } else if (EXPECTED_EXCEPTION_CAUSE_MATCHER.matches(method)) {
                template = "assertThat(#{any(java.lang.Throwable)}.getCause(), #{any(org.hamcrest.Matcher)})";
            }
            if (template == null) {
                return Optional.empty();
            }
            maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            return Optional.of(JavaTemplate.builder(template).contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5", "hamcrest-3"))
                    .staticImports("org.hamcrest.MatcherAssert.assertThat", "org.hamcrest.CoreMatchers.containsString")
                    .build());

        }
    }
}
