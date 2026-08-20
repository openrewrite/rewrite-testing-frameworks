/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoveDoNothingForDefaultMocks extends Recipe {

    @Getter
    final String displayName = "Remove `doNothing()` for void methods on `@Mock` fields";

    @Getter
    final String description = "Remove unnecessary `doNothing()` stubbings for void methods on `@Mock` fields. " +
            "Mockito mocks already do nothing for void methods by default, making these stubbings redundant " +
            "and triggering strict stubbing violations in Mockito 3+.";

    private static final MethodMatcher DO_NOTHING_MATCHER = new MethodMatcher("org.mockito.Mockito doNothing()");
    private static final MethodMatcher STUBBER_WHEN_MATCHER = new MethodMatcher("org.mockito.stubbing.Stubber when(..)");
    private static final MethodMatcher CAPTURE_MATCHER = new MethodMatcher("org.mockito.ArgumentCaptor capture()");
    private static final MethodMatcher SPY_MATCHER = new MethodMatcher("org.mockito.Mockito spy(..)");
    private static final AnnotationMatcher MOCK_ANNOTATION_MATCHER = new AnnotationMatcher("@org.mockito.Mock");
    private static final String CALLS_REAL_METHODS = "CALLS_REAL_METHODS";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(DO_NOTHING_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        Set<String> mockFieldNames = new HashSet<>();
                        for (Statement stmt : classDecl.getBody().getStatements()) {
                            if (stmt instanceof J.VariableDeclarations) {
                                J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                                if (vd.getLeadingAnnotations().stream().anyMatch(this::isDefaultAnswerMock)) {
                                    for (J.VariableDeclarations.NamedVariable var : vd.getVariables()) {
                                        mockFieldNames.add(var.getSimpleName());
                                    }
                                }
                            }
                        }
                        if (!mockFieldNames.isEmpty()) {
                            mockFieldNames.removeAll(namesAssignedFromSpy(classDecl));
                        }
                        getCursor().putMessage("mockFieldNames", mockFieldNames);
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    /**
                     * A `@Mock(answer = Answers.CALLS_REAL_METHODS)` field is a partial mock that runs real code for
                     * void methods, so `doNothing()` on it changes behavior just like it does for a spy.
                     */
                    private boolean isDefaultAnswerMock(J.Annotation annotation) {
                        if (!MOCK_ANNOTATION_MATCHER.matches(annotation)) {
                            return false;
                        }
                        List<Expression> arguments = annotation.getArguments();
                        if (arguments != null) {
                            for (Expression argument : arguments) {
                                if (argument instanceof J.Assignment &&
                                        CALLS_REAL_METHODS.equals(simpleName(((J.Assignment) argument).getAssignment()))) {
                                    return false;
                                }
                            }
                        }
                        return true;
                    }

                    private @Nullable String simpleName(Expression expression) {
                        if (expression instanceof J.FieldAccess) {
                            return ((J.FieldAccess) expression).getSimpleName();
                        }
                        if (expression instanceof J.Identifier) {
                            return ((J.Identifier) expression).getSimpleName();
                        }
                        return null;
                    }

                    /**
                     * Fields declared `@Mock` can still be replaced by a spy before the stubbing runs.
                     */
                    private Set<String> namesAssignedFromSpy(J.ClassDeclaration classDecl) {
                        return new JavaIsoVisitor<Set<String>>() {
                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, Set<String> acc) {
                                if (SPY_MATCHER.matches(assignment.getAssignment())) {
                                    String name = simpleName(assignment.getVariable());
                                    if (name != null) {
                                        acc.add(name);
                                    }
                                }
                                return super.visitAssignment(assignment, acc);
                            }

                            @Override
                            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> acc) {
                                if (SPY_MATCHER.matches(variable.getInitializer())) {
                                    acc.add(variable.getSimpleName());
                                }
                                return super.visitVariable(variable, acc);
                            }
                        }.reduce(classDecl.getBody(), new HashSet<>());
                    }

                    @Override
                    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (isDoNothingOnMockField(mi)) {
                            // Retain because if removed would leave a dangling -> producing uncompilable code
                            Object value = getCursor().getParentTreeCursor().getValue();
                            if (value instanceof J.Lambda || value instanceof J.Case && ((J.Case) value).getStatements().isEmpty()) {
                                return mi;
                            }
                            maybeRemoveImport("org.mockito.Mockito.doNothing");
                            return null;
                        }
                        return mi;
                    }

                    private boolean isDoNothingOnMockField(J.MethodInvocation mi) {
                        // Pattern: doNothing().when(mock).someVoidMethod(args)
                        if (!(mi.getSelect() instanceof J.MethodInvocation)) {
                            return false;
                        }
                        J.MethodInvocation whenCall = (J.MethodInvocation) mi.getSelect();
                        if (!STUBBER_WHEN_MATCHER.matches(whenCall)) {
                            return false;
                        }
                        // Ensure doNothing() is standalone (not chained after doThrow() etc.)
                        if (!(whenCall.getSelect() instanceof J.MethodInvocation)) {
                            return false;
                        }
                        J.MethodInvocation doNothingCall = (J.MethodInvocation) whenCall.getSelect();
                        if (!DO_NOTHING_MATCHER.matches(doNothingCall) || doNothingCall.getSelect() != null) {
                            return false;
                        }
                        // Check that the when() argument references a @Mock field
                        if (whenCall.getArguments().isEmpty() || !(whenCall.getArguments().get(0) instanceof J.Identifier)) {
                            return false;
                        }
                        J.Identifier mock = (J.Identifier) whenCall.getArguments().get(0);
                        // A local variable or parameter shadowing a `@Mock` field may well hold a spy instead
                        if (mock.getFieldType() != null && !(mock.getFieldType().getOwner() instanceof JavaType.FullyQualified)) {
                            return false;
                        }
                        Set<String> mockFieldNames = getCursor().getNearestMessage("mockFieldNames");
                        if (mockFieldNames == null || !mockFieldNames.contains(mock.getSimpleName())) {
                            return false;
                        }
                        // Preserve stubbings whose arguments include ArgumentCaptor.capture(),
                        // which registers a matcher used to capture later real invocations.
                        return !containsCapture(mi.getArguments());
                    }

                    private boolean containsCapture(List<Expression> arguments) {
                        AtomicBoolean found = new AtomicBoolean();
                        JavaIsoVisitor<AtomicBoolean> visitor = new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean acc) {
                                if (CAPTURE_MATCHER.matches(method)) {
                                    acc.set(true);
                                    return method;
                                }
                                return super.visitMethodInvocation(method, acc);
                            }
                        };
                        for (Expression arg : arguments) {
                            visitor.visit(arg, found);
                            if (found.get()) {
                                return true;
                            }
                        }
                        return false;
                    }
                }
        );
    }
}
