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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.junit5.SystemRules.*;

public class ExpectedSystemExitToCatchSystemExit extends Recipe {
    private static final String EXPECTED_SYSTEM_EXIT = PACKAGE + ".ExpectedSystemExit";
    private static final String ASSERTION = PACKAGE + ".Assertion";
    private static final String SYSTEM_STUBS = "uk.org.webcompere.systemstubs.SystemStubs";
    private static final String ASSERTIONS = "org.junit.jupiter.api.Assertions";

    private static final MethodMatcher EXPECT_EXIT = new MethodMatcher(EXPECTED_SYSTEM_EXIT + " expectSystemExit()");
    private static final MethodMatcher EXPECT_EXIT_WITH_STATUS = new MethodMatcher(EXPECTED_SYSTEM_EXIT + " expectSystemExitWithStatus(int)");
    private static final MethodMatcher CHECK_ASSERTION_AFTERWARDS = new MethodMatcher(EXPECTED_SYSTEM_EXIT + " checkAssertionAfterwards(..)");

    private static final String CANNOT_MIGRATE_COMMENT = " TODO Migrate by hand to System Stubs' `catchSystemExit(..)`: an expectation is set outside of the test method body, or the code after it can not move into a lambda.";

    @Getter
    final String displayName = "Migrate System Rules `ExpectedSystemExit` to System Stubs `catchSystemExit(..)`";

    @Getter
    final String description = "Replaces System Rules' `ExpectedSystemExit` rule with System Stubs' `catchSystemExit(..)`, which runs the rest of the test in a lambda and returns the exit status for an `assertEquals(..)`. Assertions registered through `checkAssertionAfterwards(..)` are inlined after it. Rules that other classes may use, or that set expectations outside of the test method body, get a `TODO` comment instead.";

    // Dependencies are only added once a later cycle scans the migrated code
    @Override
    public boolean causesAnotherCycle() {
        return true;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(jupiterClassesUsing(EXPECTED_SYSTEM_EXIT), new JavaVisitor<ExecutionContext>() {
            private final Set<JavaType.Variable> migrated = new HashSet<>();
            private final Map<JavaType.Variable, String> todos = new HashMap<>();
            private final Map<UUID, ExitPlan> plans = new HashMap<>();

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                migrated.clear();
                todos.clear();
                plans.clear();
                Cursor root = new Cursor(null, Cursor.ROOT_VALUE);
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations field, Integer p) {
                        JavaType.Variable variable = field.getVariables().get(0).getVariableType();
                        if (variable != null && EXPECTED_SYSTEM_EXIT.equals(fieldTypeName(field)) && isRuleField(field) &&
                            getCursor().getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration) {
                            List<Cursor> references = references(cu, root, variable);
                            if (mayBeUsedElsewhere(field, getCursor()) ||
                                references.isEmpty() && !field.hasModifier(J.Modifier.Type.Private)) {
                                todos.put(variable, MAY_BE_USED_ELSEWHERE_COMMENT);
                                return field;
                            }
                            Map<UUID, ExitPlan> fieldPlans = plan(variable, field, references);
                            if (fieldPlans == null) {
                                todos.put(variable, CANNOT_MIGRATE_COMMENT);
                            } else {
                                migrated.add(variable);
                                plans.putAll(fieldPlans);
                            }
                        }
                        return field;
                    }
                }.visit(cu, 0, root);
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public @Nullable J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.Variable variable = vd.getVariables().get(0).getVariableType();
                if (variable == null || !(getCursor().getParentTreeCursor().getValue() instanceof J.Block)) {
                    return vd;
                }
                if (migrated.contains(variable)) {
                    String supertype = declaredSupertype(vd);
                    if (supertype != null) {
                        maybeRemoveImport(supertype);
                    }
                    maybeRemoveImport(EXPECTED_SYSTEM_EXIT);
                    maybeRemoveImport(ASSERTION);
                    maybeRemoveImport(RULE);
                    return null;
                }
                if (todos.containsKey(variable)) {
                    return withTodo(vd, todos.get(variable));
                }
                return vd;
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                ExitPlan plan = plans.get(method.getId());
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                if (plan == null || m.getBody() == null) {
                    return m;
                }

                List<Statement> statements = m.getBody().getStatements();
                Statement expectation = statements.get(plan.first);
                List<Statement> exiting = statements.subList(plan.last + 1, statements.size());
                J.Block body = m.getBody().withStatements(ListUtils.concat(statements.subList(0, plan.first), expectation));

                J lambdaBody = exiting.size() == 1 && exiting.get(0) instanceof Expression ?
                        exiting.get(0).withPrefix(Space.EMPTY) :
                        new J.Block(randomId(), Space.EMPTY, Markers.EMPTY, new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
                                emptyList(), Space.format("\n")).withStatements(exiting);

                String status = VariableNameUtils.generateVariableName("status", new Cursor(getCursor(), body), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                maybeAddImport(SYSTEM_STUBS, "catchSystemExit");
                Cursor bodyCursor = new Cursor(getCursor(), body);
                if (plan.status == null) {
                    body = JavaTemplate.builder("catchSystemExit(() -> #{any()});")
                            .staticImports(SYSTEM_STUBS + ".catchSystemExit")
                            .javaParser(parser(ctx))
                            .build()
                            .apply(bodyCursor, expectation.getCoordinates().replace(), lambdaBody);
                } else {
                    maybeAddImport(ASSERTIONS, "assertEquals");
                    body = JavaTemplate.builder("int " + status + " = catchSystemExit(() -> #{any()});")
                            .staticImports(SYSTEM_STUBS + ".catchSystemExit")
                            .javaParser(parser(ctx))
                            .build()
                            .apply(bodyCursor, expectation.getCoordinates().replace(), lambdaBody);
                    J.VariableDeclarations caught = (J.VariableDeclarations) body.getStatements().get(body.getStatements().size() - 1);
                    body = JavaTemplate.builder("assertEquals(#{any(int)}, #{any(int)});")
                            .staticImports(ASSERTIONS + ".assertEquals")
                            .javaParser(parser(ctx))
                            .build()
                            .apply(new Cursor(getCursor(), body), caught.getCoordinates().after(),
                                    plan.status, caught.getVariables().get(0).getName().withPrefix(Space.EMPTY));
                }

                Space linePrefix = expectation.getPrefix();
                List<Statement> inlined = new ArrayList<>();
                for (J.Lambda assertion : plan.assertions) {
                    if (assertion.getBody() instanceof J.Block) {
                        for (Statement statement : ((J.Block) assertion.getBody()).getStatements()) {
                            inlined.add(ShiftFormat.indent(statement, new Cursor(getCursor(), body), -1));
                        }
                    } else {
                        inlined.add(((Statement) assertion.getBody()).withPrefix(linePrefix));
                    }
                }
                m = m.withBody(body.withStatements(ListUtils.concatAll(body.getStatements(), inlined)));
                return withThrowsException(m);
            }

            private J.MethodDeclaration withThrowsException(J.MethodDeclaration m) {
                List<NameTree> thrown = m.getThrows() == null ? emptyList() : m.getThrows();
                for (NameTree exception : thrown) {
                    if (TypeUtils.isOfClassType(exception.getType(), "java.lang.Exception") ||
                        TypeUtils.isOfClassType(exception.getType(), "java.lang.Throwable")) {
                        return m;
                    }
                }
                List<NameTree> kept = new ArrayList<>();
                for (NameTree exception : thrown) {
                    if (TypeUtils.isAssignableTo("java.lang.Exception", exception.getType())) {
                        JavaType.FullyQualified type = TypeUtils.asFullyQualified(exception.getType());
                        if (type != null) {
                            maybeRemoveImport(type.getFullyQualifiedName());
                        }
                    } else {
                        kept.add(exception);
                    }
                }
                J.Identifier exception = new J.Identifier(randomId(), Space.SINGLE_SPACE,
                        Markers.EMPTY, emptyList(), "Exception", JavaType.ShallowClass.build("java.lang.Exception"), null);
                List<NameTree> throwsList = ListUtils.concat(kept, exception);
                if (thrown.isEmpty()) {
                    return m.getPadding().withThrows(JContainer.build(Space.SINGLE_SPACE,
                            JRightPadded.withElements(emptyList(), throwsList), Markers.EMPTY));
                }
                return m.withThrows(throwsList);
            }
        });
    }

    private static class ExitPlan {
        final int first;
        final int last;
        final @Nullable Expression status;
        final List<J.Lambda> assertions;

        ExitPlan(int first, int last, @Nullable Expression status, List<J.Lambda> assertions) {
            this.first = first;
            this.last = last;
            this.status = status;
            this.assertions = assertions;
        }
    }

    private static @Nullable Map<UUID, ExitPlan> plan(JavaType.Variable rule, J.VariableDeclarations field, List<Cursor> references) {
        if (field.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), CLASS_RULE))) {
            return null;
        }
        Map<UUID, ExitPlan> plans = new HashMap<>();
        for (Cursor reference : references) {
            J.MethodInvocation invocation = invokedOn(reference);
            if (invocation == null) {
                return null;
            }
            Cursor block = reference.getParentTreeCursor().getParentTreeCursor();
            if (!(block.getValue() instanceof J.Block) || !(block.getParentTreeCursor().getValue() instanceof J.MethodDeclaration)) {
                return null;
            }
            J.MethodDeclaration method = block.getParentTreeCursor().getValue();
            if (!plans.containsKey(method.getId())) {
                ExitPlan plan = planMethod(rule, method);
                if (plan == null) {
                    return null;
                }
                plans.put(method.getId(), plan);
            }
        }
        return plans;
    }

    private static @Nullable ExitPlan planMethod(JavaType.Variable rule, J.MethodDeclaration method) {
        List<Statement> statements = Objects.requireNonNull(method.getBody()).getStatements();
        int first = -1;
        int last = -1;
        boolean expectsExit = false;
        Expression status = null;
        List<J.Lambda> assertions = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (!(statement instanceof J.MethodInvocation) || !rule.equals(referencedField(((J.MethodInvocation) statement).getSelect()))) {
                continue;
            }
            if (last >= 0 && last != i - 1) {
                return null;
            }
            first = first < 0 ? i : first;
            last = i;
            J.MethodInvocation invocation = (J.MethodInvocation) statement;
            if (EXPECT_EXIT.matches(invocation)) {
                expectsExit = true;
            } else if (EXPECT_EXIT_WITH_STATUS.matches(invocation)) {
                expectsExit = true;
                status = invocation.getArguments().get(0);
            } else if (CHECK_ASSERTION_AFTERWARDS.matches(invocation) && isInlinable(invocation.getArguments().get(0))) {
                assertions.add((J.Lambda) invocation.getArguments().get(0));
            } else {
                return null;
            }
        }
        List<Statement> exiting = statements.subList(last + 1, statements.size());
        if (!expectsExit || exiting.isEmpty() || capturesReassignedLocal(method, exiting)) {
            return null;
        }
        return new ExitPlan(first, last, status, assertions);
    }

    private static boolean isInlinable(Expression assertion) {
        if (!(assertion instanceof J.Lambda)) {
            return false;
        }
        J body = ((J.Lambda) assertion).getBody();
        if (body instanceof J.Block) {
            return !new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.Return visitReturn(J.Return aReturn, AtomicBoolean found) {
                    found.set(true);
                    return aReturn;
                }

                @Override
                public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                    return lambda;
                }

                @Override
                public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                    return newClass;
                }
            }.reduce(body, new AtomicBoolean()).get();
        }
        return body instanceof Statement;
    }

    private static boolean capturesReassignedLocal(J.MethodDeclaration method, List<Statement> moved) {
        Set<JavaType.Variable> declaredInMoved = new HashSet<>();
        Set<JavaType.Variable> read = new HashSet<>();
        for (Statement statement : moved) {
            new JavaIsoVisitor<Integer>() {
                @Override
                public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
                    if (variable.getVariableType() != null) {
                        declaredInMoved.add(variable.getVariableType());
                    }
                    return super.visitVariable(variable, p);
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, Integer p) {
                    JavaType.Variable variable = identifier.getFieldType();
                    if (variable != null && variable.getOwner() instanceof JavaType.Method) {
                        read.add(variable);
                    }
                    return identifier;
                }
            }.visit(statement, 0);
        }
        read.removeAll(declaredInMoved);
        if (read.isEmpty()) {
            return false;
        }
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, AtomicBoolean found) {
                found.compareAndSet(false, read.contains(referencedField(assignment.getVariable())));
                return super.visitAssignment(assignment, found);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, AtomicBoolean found) {
                found.compareAndSet(false, read.contains(referencedField(assignOp.getVariable())));
                return super.visitAssignmentOperation(assignOp, found);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, AtomicBoolean found) {
                if (unary.getOperator().isModifying()) {
                    found.compareAndSet(false, read.contains(referencedField(unary.getExpression())));
                }
                return super.visitUnary(unary, found);
            }
        }.reduce(method, new AtomicBoolean()).get();
    }

    private static JavaParser.Builder<?, ?> parser(ExecutionContext ctx) {
        return JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-core-2", "junit-jupiter-api-5");
    }
}
