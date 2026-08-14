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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.ParenthesizeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Getter
    final String displayName = "JUnit 4 `ExpectedException` To JUnit Jupiter's `assertThrows()`";

    @Getter
    final String description = "Replace usages of JUnit 4's `@Rule ExpectedException` with JUnit 5's `Assertions.assertThrows()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.rules.ExpectedException", false), new ExpectedExceptionToAssertThrowsVisitor());
    }

    private static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String FIRST_EXPECTED_EXCEPTION_METHOD_INVOCATION = "firstExpectedExceptionMethodInvocation";
        private static final String STATEMENTS_BEFORE_EXPECT_EXCEPTION = "statementsBeforeExpectException";
        private static final String STATEMENTS_AFTER_EXPECT_EXCEPTION = "statementsAfterExpectException";
        private static final String HAS_MATCHER = "hasMatcher";
        private static final String EXCEPTION_CLASS = "exceptionClass";

        private static final MethodMatcher EXPECTED_EXCEPTION_ALL_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expect*(..)");
        private static final MethodMatcher EXPECTED_EXCEPTION_CLASS_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expect(java.lang.Class)");
        private static final MethodMatcher EXPECTED_MESSAGE_STRING_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expectMessage(java.lang.String)");
        private static final MethodMatcher EXPECTED_MESSAGE_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expectMessage(org.hamcrest.Matcher)");
        private static final MethodMatcher EXPECTED_EXCEPTION_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expect(org.hamcrest.Matcher)");
        private static final MethodMatcher EXPECTED_EXCEPTION_CAUSE_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expectCause(org.hamcrest.Matcher)");
        // *Matchers wildcard: isA/instanceOf/is are all reachable from both CoreMatchers and the consolidated Matchers.
        private static final MethodMatcher IS_A_MATCHER = new MethodMatcher("org.hamcrest.*Matchers isA(java.lang.Class)");
        private static final MethodMatcher INSTANCE_OF_MATCHER = new MethodMatcher("org.hamcrest.*Matchers instanceOf(java.lang.Class)");
        private static final MethodMatcher IS_MATCHER_CORE_MATCHERS = new MethodMatcher("org.hamcrest.*Matchers is(..)");
        private static final MethodMatcher IS_MATCHER_CORE_IS = new MethodMatcher("org.hamcrest.core.Is is(..)");

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
                    List<Statement> statementsBeforeExpect = getCursor().pollMessage(STATEMENTS_BEFORE_EXPECT_EXCEPTION);
                    if (statementsBeforeExpect != null && statementsBeforeExpect.stream().anyMatch(this::statementThrowsCheckedException)) {
                        return m;
                    }
                    assert m.getBody() != null;
                    return m.withBody(m.getBody().withPrefix(thrown.get(0).getPrefix())).withThrows(emptyList());
                }
            }
            return m;
        }

        private boolean statementThrowsCheckedException(Statement statement) {
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                    if (found.get()) {
                        return method;
                    }
                    JavaType.Method methodType = method.getMethodType();
                    if (methodType != null) {
                        for (JavaType thrownException : methodType.getThrownExceptions()) {
                            if (isCheckedException(thrownException)) {
                                found.set(true);
                                return method;
                            }
                        }
                    }
                    return super.visitMethodInvocation(method, found);
                }

                @Override
                public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                    if (found.get()) {
                        return newClass;
                    }
                    JavaType.Method constructorType = newClass.getConstructorType();
                    if (constructorType != null) {
                        for (JavaType thrownException : constructorType.getThrownExceptions()) {
                            if (isCheckedException(thrownException)) {
                                found.set(true);
                                return newClass;
                            }
                        }
                    }
                    return super.visitNewClass(newClass, found);
                }
            }.reduce(statement, new AtomicBoolean(false)).get();
        }

        private boolean isCheckedException(JavaType exceptionType) {
            return !TypeUtils.isAssignableTo("java.lang.RuntimeException", exceptionType) &&
                   !TypeUtils.isAssignableTo("java.lang.Error", exceptionType);
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block simplified = inlineSingleUseReassignedLocals(block);
            updateCursor(simplified);
            J.Block b = super.visitBlock(simplified, ctx);
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
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "hamcrest-3"))
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
            b = b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                if (statement instanceof J.MethodInvocation) {
                    J.MethodInvocation invocation = (J.MethodInvocation) statement;
                    if (EXPECTED_EXCEPTION_ALL_MATCHER.matches(invocation)) {
                        removeStatement.set(true);
                        Optional<Statement> instanceOfAssertion = maybeAssertInstanceOfForCause(
                                invocation, ctx, new Cursor(updateCursor, statement), exceptionIdentifier);
                        if (instanceOfAssertion.isPresent()) {
                            return instanceOfAssertion.get();
                        }
                        return getExpectExceptionTemplate(invocation, ctx)
                                .<J.MethodInvocation>map(t -> t.apply(
                                        new Cursor(updateCursor, statement),
                                        statement.getCoordinates().replace(),
                                        exceptionIdentifier,
                                        invocation.getArguments().get(0)))
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
            getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance).putMessage("hasExpectException", true);
            Cursor blockCursor = getCursor().dropParentUntil(J.Block.class::isInstance);
            blockCursor.computeMessageIfAbsent(FIRST_EXPECTED_EXCEPTION_METHOD_INVOCATION, k -> method);

            List<Statement> predecessorStatements = findPredecessorStatements(getCursor());
            getCursor().dropParentUntil(J.MethodDeclaration.class::isInstance)
                    .computeMessageIfAbsent(STATEMENTS_BEFORE_EXPECT_EXCEPTION, k -> predecessorStatements);

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
         * From the current cursor point find all preceding statements in the method body.
         */
        private List<Statement> findPredecessorStatements(Cursor cursor) {
            J.MethodDeclaration methodDecl = cursor.firstEnclosing(J.MethodDeclaration.class);
            if (methodDecl == null || methodDecl.getBody() == null) {
                return emptyList();
            }
            List<Statement> predecessorStatements = new ArrayList<>();
            Statement currentStatement = cursor.firstEnclosing(Statement.class);
            for (Statement statement : methodDecl.getBody().getStatements()) {
                if (statement == currentStatement) {
                    break;
                }
                predecessorStatements.add(statement);
            }
            return predecessorStatements;
        }

        /**
         * From the current cursor point find all the next statements that can be executed in the current path.
         */
        private List<Statement> findSuccessorStatements(Cursor cursor) {
            if (cursor.firstEnclosing(J.MethodDeclaration.class) == null) {
                return emptyList();
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

        /**
         * Narrow case: a local reassigned exactly once before expect*() (RHS not self-referencing),
         * not read again before expect*(), and read exactly once among the statements moving into
         * the assertThrows(...) lambda -- inline the RHS at that use site instead of leaving a
         * non-effectively-final capture. Chains and multi-use reads are left untouched.
         */
        private static J.Block inlineSingleUseReassignedLocals(J.Block block) {
            for (boolean changed = true; changed; ) {
                changed = false;
                List<Statement> statements = block.getStatements();
                int expectIndex = findExpectIndex(statements);
                if (expectIndex <= 0) {
                    return block;
                }
                for (int i = 0; i < expectIndex && !changed; i++) {
                    if (!(statements.get(i) instanceof J.Assignment)) {
                        continue;
                    }
                    J.Assignment assignment = (J.Assignment) statements.get(i);
                    JavaType.Variable local = localVariable(assignment.getVariable());
                    if (local == null) {
                        continue;
                    }
                    Expression rhs = assignment.getAssignment();
                    List<Statement> between = statements.subList(i + 1, expectIndex);
                    boolean selfReferencing = countReferences(rhs, local) > 0;
                    boolean reassignedOnce = countAssignments(statements.subList(0, expectIndex), local) == 1;
                    boolean touchedBetween = countReferences(between, local) > 0;
                    if (selfReferencing || !reassignedOnce || touchedBetween || !safeToDefer(rhs, between)) {
                        continue;
                    }
                    J.Block updated = inlineAtSingleUse(block, statements, i, expectIndex, local, rhs);
                    if (updated != null) {
                        block = updated;
                        changed = true;
                    }
                }
            }
            return block;
        }

        private static int findExpectIndex(List<Statement> statements) {
            for (int i = 0; i < statements.size(); i++) {
                if (statements.get(i) instanceof J.MethodInvocation &&
                        EXPECTED_EXCEPTION_ALL_MATCHER.matches((J.MethodInvocation) statements.get(i))) {
                    return i;
                }
            }
            return -1;
        }

        // Only local variables are inlined; a field assignment can be observed outside this method.
        private static JavaType.@Nullable Variable localVariable(Expression target) {
            if (target instanceof J.Identifier) {
                JavaType.Variable fieldType = ((J.Identifier) target).getFieldType();
                if (fieldType != null && fieldType.getOwner() instanceof JavaType.Method) {
                    return fieldType;
                }
            }
            return null;
        }

        private static boolean isReferenceTo(@Nullable Expression expression, JavaType.Variable variable) {
            return expression instanceof J.Identifier &&
                    ((J.Identifier) expression).getFieldType() != null &&
                    TypeUtils.isOfType(((J.Identifier) expression).getFieldType(), variable);
        }

        private static int countAssignments(J tree, JavaType.Variable variable) {
            return new JavaIsoVisitor<AtomicInteger>() {
                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, AtomicInteger acc) {
                    if (isReferenceTo(assignment.getVariable(), variable)) {
                        acc.incrementAndGet();
                    }
                    return super.visitAssignment(assignment, acc);
                }

                @Override
                public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicInteger acc) {
                    if (isReferenceTo(assignOp.getVariable(), variable)) {
                        acc.incrementAndGet();
                    }
                    return super.visitAssignmentOperation(assignOp, acc);
                }

                @Override
                public J.Unary visitUnary(J.Unary unary, AtomicInteger acc) {
                    if (unary.getOperator().isModifying() && isReferenceTo(unary.getExpression(), variable)) {
                        acc.incrementAndGet();
                    }
                    return super.visitUnary(unary, acc);
                }
            }.reduce(tree, new AtomicInteger()).get();
        }

        private static int countAssignments(List<? extends J> trees, JavaType.Variable variable) {
            int total = 0;
            for (J tree : trees) {
                total += countAssignments(tree, variable);
            }
            return total;
        }

        private static int countReferences(J tree, JavaType.Variable variable) {
            return new JavaIsoVisitor<AtomicInteger>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger acc) {
                    if (isReferenceTo(identifier, variable)) {
                        acc.incrementAndGet();
                    }
                    return super.visitIdentifier(identifier, acc);
                }
            }.reduce(tree, new AtomicInteger()).get();
        }

        private static int countReferences(List<? extends J> trees, JavaType.Variable variable) {
            int total = 0;
            for (J tree : trees) {
                total += countReferences(tree, variable);
            }
            return total;
        }

        /**
         * Inlining moves the right hand side past the statements between the assignment and expect*(),
         * so only defer values those statements can not change: literals and local variables that are
         * not themselves reassigned in between, without any side effects of their own.
         */
        private static boolean safeToDefer(Expression rhs, List<Statement> between) {
            if (between.isEmpty()) {
                return true;
            }
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean acc) {
                    acc.set(false);
                    return method;
                }

                @Override
                public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean acc) {
                    acc.set(false);
                    return newClass;
                }

                @Override
                public J.ArrayAccess visitArrayAccess(J.ArrayAccess arrayAccess, AtomicBoolean acc) {
                    acc.set(false);
                    return arrayAccess;
                }

                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean acc) {
                    acc.set(false);
                    return assignment;
                }

                @Override
                public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean acc) {
                    acc.set(false);
                    return assignOp;
                }

                @Override
                public J.Unary visitUnary(J.Unary unary, AtomicBoolean acc) {
                    if (unary.getOperator().isModifying()) {
                        acc.set(false);
                        return unary;
                    }
                    return super.visitUnary(unary, acc);
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean acc) {
                    JavaType.Variable fieldType = identifier.getFieldType();
                    if (fieldType != null &&
                            (!(fieldType.getOwner() instanceof JavaType.Method) || countAssignments(between, fieldType) > 0)) {
                        acc.set(false);
                    }
                    return identifier;
                }
            }.reduce(rhs, new AtomicBoolean(true)).get();
        }

        // Deletes the reassignment at statements[assignmentIndex] and splices its RHS in place of
        // the single successor statement reading the variable; null if there isn't exactly one read.
        private static J.@Nullable Block inlineAtSingleUse(J.Block block, List<Statement> statements, int assignmentIndex,
                int expectIndex, JavaType.Variable variable, Expression rhs) {
            int afterIndex = -1;
            int uses = 0;
            for (int j = expectIndex + 1; j < statements.size(); j++) {
                Statement statement = statements.get(j);
                if (countAssignments(statement, variable) > 0) {
                    return null;
                }
                int hits = countReferences(statement, variable);
                uses += hits;
                if (hits > 0) {
                    afterIndex = j;
                }
            }
            if (uses != 1) {
                return null;
            }
            Statement rewritten = inlineReferenceInStatement(statements.get(afterIndex), variable, rhs);
            int finalAfterIndex = afterIndex;
            return block.withStatements(ListUtils.map(statements, (i, s) -> {
                if (i == assignmentIndex) {
                    return null;
                }
                return i == finalAfterIndex ? rewritten : s;
            }));
        }

        // Replaces the sole reference to `variable` in `statement` with `replacement`.
        private static Statement inlineReferenceInStatement(Statement statement, JavaType.Variable variable,
                Expression replacement) {
            return (Statement) new JavaVisitor<Integer>() {
                @Override
                public J visitIdentifier(J.Identifier identifier, Integer p) {
                    if (!isReferenceTo(identifier, variable)) {
                        return identifier;
                    }
                    return ParenthesizeVisitor.maybeParenthesize(replacement.withPrefix(Space.EMPTY), getCursor())
                            .withPrefix(identifier.getPrefix());
                }
            }.visit(statement, 0);
        }

        private Optional<JavaTemplate> getExpectExceptionTemplate(J.MethodInvocation method, ExecutionContext ctx) {
            String template;
            if (EXPECTED_MESSAGE_STRING_MATCHER.matches(method)) {
                maybeAddImport("org.hamcrest.CoreMatchers", "containsString");
                template = "assertThat(#{any(java.lang.Throwable)}.getMessage(), containsString(#{any(java.lang.String)}))";
            } else if (EXPECTED_MESSAGE_MATCHER.matches(method)) {
                template = "assertThat(#{any(java.lang.Throwable)}.getMessage(), #{any(org.hamcrest.Matcher)})";
            } else if (EXPECTED_EXCEPTION_MATCHER.matches(method)) {
                template = "assertThat(#{any(java.lang.Throwable)}, #{any(org.hamcrest.Matcher)})";
            } else if (EXPECTED_EXCEPTION_CAUSE_MATCHER.matches(method)) {
                template = "assertThat(#{any(java.lang.Throwable)}.getCause(), #{any(org.hamcrest.Matcher)})";
            } else {
                return Optional.empty();
            }
            maybeAddImport("org.hamcrest.MatcherAssert", "assertThat");
            return Optional.of(JavaTemplate.builder(template)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "hamcrest-3"))
                    .staticImports("org.hamcrest.MatcherAssert.assertThat", "org.hamcrest.CoreMatchers.containsString")
                    .build());
        }

        // expectCause(isA/instanceOf/is(X.class)) becomes the type-safe assertInstanceOf(X.class, exception.getCause()).
        private Optional<Statement> maybeAssertInstanceOfForCause(
                J.MethodInvocation invocation, ExecutionContext ctx, Cursor cursor, J.Identifier exceptionIdentifier) {
            if (!EXPECTED_EXCEPTION_CAUSE_MATCHER.matches(invocation)) {
                return Optional.empty();
            }
            Expression matcherArg = invocation.getArguments().get(0);
            if (!(matcherArg instanceof J.MethodInvocation)) {
                return Optional.empty();
            }
            Expression classArg = extractClassLiteralFromInstanceOfMatcher((J.MethodInvocation) matcherArg);
            if (classArg == null) {
                return Optional.empty();
            }
            maybeRemoveImport("org.hamcrest.Matchers.isA");
            maybeRemoveImport("org.hamcrest.CoreMatchers.isA");
            maybeRemoveImport("org.hamcrest.Matchers.instanceOf");
            maybeRemoveImport("org.hamcrest.CoreMatchers.instanceOf");
            maybeRemoveImport("org.hamcrest.Matchers.is");
            maybeRemoveImport("org.hamcrest.CoreMatchers.is");
            maybeRemoveImport("org.hamcrest.core.Is.is");
            maybeAddImport("org.junit.jupiter.api.Assertions", "assertInstanceOf");
            return Optional.of(JavaTemplate.builder("assertInstanceOf(#{any()}, #{any(java.lang.Throwable)}.getCause())")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                    .staticImports("org.junit.jupiter.api.Assertions.assertInstanceOf")
                    .build()
                    .apply(cursor, invocation.getCoordinates().replace(), classArg, exceptionIdentifier));
        }

        // Only is(X.class) is treated as an instanceof check, to avoid false positives on is("string") or is(someVariable).
        private Expression extractClassLiteralFromInstanceOfMatcher(J.MethodInvocation matcherCall) {
            if (matcherCall.getArguments().isEmpty()) {
                return null;
            }
            Expression arg = matcherCall.getArguments().get(0);
            if (IS_A_MATCHER.matches(matcherCall) || INSTANCE_OF_MATCHER.matches(matcherCall)) {
                return arg;
            }
            if (isHamcrestIs(matcherCall) && arg instanceof J.FieldAccess && "class".equals(((J.FieldAccess) arg).getName().getSimpleName())) {
                return arg;
            }
            return null;
        }

        private boolean isHamcrestIs(J.MethodInvocation method) {
            return IS_MATCHER_CORE_MATCHERS.matches(method) || IS_MATCHER_CORE_IS.matches(method);
        }
    }
}
