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
package org.openrewrite.java.testing.easymock;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static java.lang.String.join;
import static java.util.Collections.nCopies;

public class EasyMockVerifyToMockitoVerify extends Recipe {

    private static final MethodMatcher VERIFY_MATCHER = new MethodMatcher("org.easymock.EasyMock verify(..)", true);
    private static final MethodMatcher EXPECT_MATCHER = new MethodMatcher("org.easymock.EasyMock expect(..)");
    private static final MethodMatcher EXPECT_LAST_CALL_MATCHER = new MethodMatcher("org.easymock.EasyMock expectLastCall()");
    private static final MethodMatcher EXPECTATION_SETTERS = new MethodMatcher("org.easymock.IExpectationSetters *(..)");
    private static final int UNBOUNDED = Integer.MAX_VALUE;
    private static final List<AnnotationMatcher> SET_UP_ANNOTATIONS = Arrays.asList(
            new AnnotationMatcher("@org.junit.Before"),
            new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach"),
            new AnnotationMatcher("@org.testng.annotations.BeforeMethod"));

    @Getter
    final String displayName = "Replace EasyMock `verify` calls with Mockito `verify` calls";

    @Getter
    final String description = "Replace `EasyMock.verify(dependency, ..)` with a `Mockito.verify(dependency).method()` call for " +
            "each call expected of each of the mocks, with `expect(..)` or `expectLastCall()` earlier in the same method. " +
            "An expected number of calls becomes `times(n)`, `atLeastOnce()` or `atLeast(n)`, and stubs and calls expected " +
            "`anyTimes()` are not verified. The same call expected more than once, also in a method that runs before each test, " +
            "is verified once for the number of times it is expected in all. A `verify` for which no expected call is found is removed, as Mockito has no " +
            "equivalent of verifying all the expectations of a mock.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(VERIFY_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                for (int i = 0; i < b.getStatements().size(); i++) {
                    Statement statement = b.getStatements().get(i);
                    if (!(statement instanceof J.MethodInvocation) || !VERIFY_MATCHER.matches((J.MethodInvocation) statement)) {
                        continue;
                    }
                    List<Statement> statementsAbove = statementsAbove(b.getStatements().subList(0, i));
                    List<Expectation> expectations = new ArrayList<>();
                    for (Expression mock : ((J.MethodInvocation) statement).getArguments()) {
                        if (!(mock instanceof J.Empty)) {
                            expectations.addAll(combined(expectedCalls(mock, statementsAbove)));
                        }
                    }

                    maybeRemoveImport("org.easymock.EasyMock.verify");
                    if (expectations.isEmpty()) {
                        // Nothing that a Mockito `verify` can check, and a bare `Mockito.verify(mock)` fails the test
                        int verifyIndex = i;
                        b = b.withStatements(ListUtils.map(b.getStatements(), (j, s) -> j == verifyIndex ? null : s));
                        i--;
                        continue;
                    }

                    maybeAddImport("org.mockito.Mockito", "verify");
                    for (int e = 0; e < expectations.size(); e++) {
                        Expectation expectation = expectations.get(e);
                        List<Expression> arguments = new ArrayList<>(expectation.call.getArguments());
                        if (arguments.size() == 1 && arguments.get(0) instanceof J.Empty) {
                            arguments.clear();
                        }
                        List<Object> parameters = new ArrayList<>();
                        parameters.add(expectation.mock);
                        String mode = mode(expectation, parameters);
                        parameters.addAll(arguments);
                        String code = "verify(#{any()}" + mode + ")." + expectation.call.getSimpleName() +
                                "(" + join(", ", nCopies(arguments.size(), "#{any()}")) + ")";
                        JavaCoordinates coordinates = e == 0 ?
                                b.getStatements().get(i).getCoordinates().replace() :
                                b.getStatements().get(i).getCoordinates().after();
                        b = JavaTemplate.builder(code)
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                                .staticImports("org.mockito.Mockito.verify", "org.mockito.Mockito.times",
                                        "org.mockito.Mockito.atLeast", "org.mockito.Mockito.atLeastOnce")
                                .build()
                                .apply(updateCursor(b), coordinates, parameters.toArray());
                        if (e != 0) {
                            i++;
                        }
                    }
                }
                return b;
            }

            private String mode(Expectation expectation, List<Object> parameters) {
                if (expectation.timesExpression != null) {
                    maybeAddImport("org.mockito.Mockito", "times");
                    parameters.add(expectation.timesExpression);
                    return ", times(#{any(int)})";
                }
                if (expectation.min == expectation.max) {
                    if (expectation.min == 1) {
                        return "";
                    }
                    maybeAddImport("org.mockito.Mockito", "times");
                    return ", times(" + expectation.min + ")";
                }
                if (expectation.min == 1) {
                    maybeAddImport("org.mockito.Mockito", "atLeastOnce");
                    return ", atLeastOnce()";
                }
                maybeAddImport("org.mockito.Mockito", "atLeast");
                return ", atLeast(" + expectation.min + ")";
            }

            /**
             * The statements above the `verify` in its own block, preceded by those above the blocks it is nested in,
             * up to the method it is in.
             */
            private List<Statement> statementsAbove(List<Statement> aboveInBlock) {
                Deque<List<Statement>> above = new ArrayDeque<>();
                above.push(aboveInBlock);
                Object child = getCursor().getValue();
                for (Cursor c = getCursor().getParentTreeCursor(); !(c.getValue() instanceof J.MethodDeclaration ||
                                                                     c.getValue() instanceof J.ClassDeclaration ||
                                                                     c.getValue() instanceof J.Lambda ||
                                                                     c.getValue() instanceof J.CompilationUnit); c = c.getParentTreeCursor()) {
                    if (c.getValue() instanceof J.Block) {
                        List<Statement> statements = ((J.Block) c.getValue()).getStatements();
                        int index = statements.indexOf(child);
                        if (index >= 0) {
                            above.push(statements.subList(0, index));
                        }
                    }
                    child = c.getValue();
                }
                List<Statement> statements = new ArrayList<>(statementsOfSetUp());
                above.forEach(statements::addAll);
                return statements;
            }

            /**
             * The statements of the methods that run before each test, as the calls they expect add to those of the test.
             */
            private List<Statement> statementsOfSetUp() {
                J.MethodDeclaration test = getCursor().firstEnclosing(J.MethodDeclaration.class);
                J.ClassDeclaration testClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                List<Statement> statements = new ArrayList<>();
                if (test == null || testClass == null || isSetUp(test)) {
                    return statements;
                }
                for (Statement member : testClass.getBody().getStatements()) {
                    if (member instanceof J.MethodDeclaration && isSetUp((J.MethodDeclaration) member) &&
                            ((J.MethodDeclaration) member).getBody() != null) {
                        statements.addAll(((J.MethodDeclaration) member).getBody().getStatements());
                    }
                }
                return statements;
            }

            private boolean isSetUp(J.MethodDeclaration method) {
                return method.getLeadingAnnotations().stream().anyMatch(a -> SET_UP_ANNOTATIONS.stream().anyMatch(m -> m.matches(a))) ||
                       "setUp".equals(method.getSimpleName()) && method.getParameters().get(0) instanceof J.Empty;
            }

            /**
             * The same call expected more than once, as in `expect(mock.call()).andReturn(1); expect(mock.call()).andReturn(2);`,
             * is verified once, for the number of times it is expected in all.
             */
            private List<Expectation> combined(List<Expectation> expectations) {
                List<Expectation> combined = new ArrayList<>();
                for (Expectation expectation : expectations) {
                    int same = -1;
                    for (int i = 0; i < combined.size() && same < 0; i++) {
                        if (isSameCall(combined.get(i), expectation)) {
                            same = i;
                        }
                    }
                    if (same < 0) {
                        combined.add(expectation);
                    } else {
                        Expectation first = combined.get(same);
                        combined.set(same, new Expectation(first.mock, first.call,
                                add(first.min, expectation.min), add(first.max, expectation.max), null));
                    }
                }
                return combined;
            }

            private boolean isSameCall(Expectation a, Expectation b) {
                if (a.timesExpression != null || b.timesExpression != null ||
                        !a.call.getSimpleName().equals(b.call.getSimpleName()) ||
                        a.call.getArguments().size() != b.call.getArguments().size()) {
                    return false;
                }
                for (int i = 0; i < a.call.getArguments().size(); i++) {
                    if (!SemanticallyEqual.areEqual(a.call.getArguments().get(i), b.call.getArguments().get(i))) {
                        return false;
                    }
                }
                return true;
            }

            private List<Expectation> expectedCalls(Expression mock, List<Statement> statementsAbove) {
                List<Expectation> expectedCalls = new ArrayList<>();
                for (int i = 0; i < statementsAbove.size(); i++) {
                    Statement statement = statementsAbove.get(i);
                    if (!(statement instanceof J.MethodInvocation)) {
                        continue;
                    }
                    List<J.MethodInvocation> setters = new ArrayList<>();
                    J.MethodInvocation root = rootOfSetters((J.MethodInvocation) statement, setters);
                    if (root != null && EXPECT_MATCHER.matches(root) && root.getArguments().get(0) instanceof J.MethodInvocation) {
                        // expect(mock.call(..)).andReturn(..).times(..)
                        J.MethodInvocation call = (J.MethodInvocation) root.getArguments().get(0);
                        if (isCallOn(call, mock)) {
                            addExpectation(expectedCalls, mock, call, setters);
                        }
                    } else if (root == null && isCallOn((J.MethodInvocation) statement, mock) && i + 1 < statementsAbove.size() &&
                               statementsAbove.get(i + 1) instanceof J.MethodInvocation) {
                        // mock.call(..); expectLastCall().andThrow(..).times(..)
                        List<J.MethodInvocation> lastCallSetters = new ArrayList<>();
                        J.MethodInvocation next = rootOfSetters((J.MethodInvocation) statementsAbove.get(i + 1), lastCallSetters);
                        if (next != null && EXPECT_LAST_CALL_MATCHER.matches(next)) {
                            addExpectation(expectedCalls, mock, (J.MethodInvocation) statement, lastCallSetters);
                        }
                    }
                }
                return expectedCalls;
            }

            private boolean isCallOn(J.MethodInvocation call, Expression mock) {
                return call.getSelect() != null && SemanticallyEqual.areEqual(mock, call.getSelect());
            }

            /**
             * @return The `expect(..)` or `expectLastCall()` the method invocation is a chain of expectation setters on,
             * with the setters collected in the order they are called.
             */
            private J.@Nullable MethodInvocation rootOfSetters(J.MethodInvocation mi, List<J.MethodInvocation> setters) {
                J.MethodInvocation current = mi;
                while (EXPECTATION_SETTERS.matches(current) && current.getSelect() instanceof J.MethodInvocation) {
                    setters.add(0, current);
                    current = (J.MethodInvocation) current.getSelect();
                }
                if (EXPECT_MATCHER.matches(current) || EXPECT_LAST_CALL_MATCHER.matches(current)) {
                    return current;
                }
                setters.clear();
                return null;
            }

            private void addExpectation(List<Expectation> expectations, Expression mock, J.MethodInvocation call,
                                        List<J.MethodInvocation> setters) {
                // Each behavior is expected once, unless a count follows it
                int min = 0;
                int max = 0;
                int segmentMin = 1;
                int segmentMax = 1;
                int segments = 0;
                Expression timesExpression = null;
                for (J.MethodInvocation setter : setters) {
                    String name = setter.getSimpleName();
                    if (name.startsWith("and")) {
                        // a behavior ends the one before it, along with the count that was set for it
                        if (segments++ > 0) {
                            min = add(min, segmentMin);
                            max = add(max, segmentMax);
                        }
                        boolean stub = name.startsWith("andStub");
                        segmentMin = stub ? 0 : 1;
                        segmentMax = stub ? UNBOUNDED : 1;
                    } else if ("asStub".equals(name)) {
                        segmentMin = 0;
                        segmentMax = UNBOUNDED;
                    } else if ("once".equals(name)) {
                        segmentMin = 1;
                        segmentMax = 1;
                    } else if ("atLeastOnce".equals(name)) {
                        segmentMin = 1;
                        segmentMax = UNBOUNDED;
                    } else if ("anyTimes".equals(name)) {
                        segmentMin = 0;
                        segmentMax = UNBOUNDED;
                    } else if ("times".equals(name)) {
                        List<Expression> arguments = setter.getArguments();
                        Integer from = intLiteral(arguments.get(0));
                        Integer to = arguments.size() == 2 ? intLiteral(arguments.get(1)) : from;
                        if (from == null) {
                            if (arguments.size() != 1 || setters.size() > 2) {
                                return;
                            }
                            timesExpression = arguments.get(0);
                        } else {
                            segmentMin = from;
                            segmentMax = to == null ? UNBOUNDED : to;
                        }
                    } else {
                        return;
                    }
                }
                min = add(min, segmentMin);
                max = add(max, segmentMax);
                if (timesExpression != null || min > 0) {
                    expectations.add(new Expectation(mock, call, min, max, timesExpression));
                }
            }

            private int add(int a, int b) {
                return a == UNBOUNDED || b == UNBOUNDED ? UNBOUNDED : a + b;
            }

            private @Nullable Integer intLiteral(Expression expression) {
                if (expression instanceof J.Literal && ((J.Literal) expression).getValue() instanceof Integer) {
                    return (Integer) ((J.Literal) expression).getValue();
                }
                return null;
            }
        });
    }

    private static class Expectation {
        final Expression mock;
        final J.MethodInvocation call;
        final int min;
        final int max;
        final @Nullable Expression timesExpression;

        Expectation(Expression mock, J.MethodInvocation call, int min, int max, @Nullable Expression timesExpression) {
            this.mock = mock;
            this.call = call;
            this.min = min;
            this.max = max;
            this.timesExpression = timesExpression;
        }
    }
}
