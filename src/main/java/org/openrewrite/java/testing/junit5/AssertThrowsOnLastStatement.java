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
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;


public class AssertThrowsOnLastStatement extends Recipe {

    @Override
    public String getDisplayName() {
        return "Applies JUnit 5 `assertThrows` on last statement in lambda block only";
    }

    @Override
    public String getDescription() {
        return "Applies JUnit 5 `assertThrows` on last statement in lambda block only. " +
                "In rare cases may cause compilation errors if the lambda uses effectively non final variables. " +
                "In some cases, tests might fail if earlier statements in the lambda block throw exceptions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher assertThrowsMatcher = new MethodMatcher(
                "org.junit.jupiter.api.Assertions assertThrows(java.lang.Class, org.junit.jupiter.api.function.Executable, ..)");
        return Preconditions.check(new UsesMethod<>(assertThrowsMatcher), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);
                if (m.getBody() == null) {
                    return m;
                }
                doAfterVisit(new LambdaBlockToExpression().getVisitor());
                J.MethodDeclaration methodDeclaration = m.withBody(m.getBody().withStatements(ListUtils.flatMap(m.getBody().getStatements(), methodStatement -> {
                    J statementToCheck = methodStatement;
                    final J.VariableDeclarations assertThrowsWithVarDec;
                    final J.VariableDeclarations.NamedVariable assertThrowsVar;

                    if (methodStatement instanceof J.VariableDeclarations) {
                        assertThrowsWithVarDec = (J.VariableDeclarations) methodStatement;
                        List<J.VariableDeclarations.NamedVariable> assertThrowsNamedVars = assertThrowsWithVarDec.getVariables();
                        if (assertThrowsNamedVars.size() != 1) {
                            return methodStatement;
                        }

                        // has variable declaration for assertThrows eg Throwable ex = assertThrows(....)
                        assertThrowsVar = assertThrowsNamedVars.get(0);
                        statementToCheck = assertThrowsVar.getInitializer();
                    } else {
                        assertThrowsWithVarDec = null;
                        assertThrowsVar = null;
                    }

                    if (!(statementToCheck instanceof J.MethodInvocation)) {
                        return methodStatement;
                    }

                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statementToCheck;
                    if (!assertThrowsMatcher.matches(methodInvocation)) {
                        return methodStatement;
                    }

                    List<Expression> arguments = methodInvocation.getArguments();
                    if (arguments.size() <= 1) {
                        return methodStatement;
                    }

                    Expression arg = arguments.get(1);
                    if (!(arg instanceof J.Lambda)) {
                        return methodStatement;
                    }

                    J.Lambda lambda = (J.Lambda) arg;
                    if (!(lambda.getBody() instanceof J.Block)) {
                        return methodStatement;
                    }

                    J.Block body = (J.Block) lambda.getBody();
                    List<Statement> lambdaStatements = body.getStatements();
                    if (lambdaStatements.size() <= 1) {
                        return methodStatement;
                    }

                    // TODO Check to see if last line in lambda does not use a non-final variable

                    // move all the statements from the body into before the method invocation, except last one
                    return ListUtils.flatMap(lambdaStatements, (idx, lambdaStatement) -> {
                        if (idx < lambdaStatements.size() - 1) {
                            return lambdaStatement.withPrefix(methodStatement.getPrefix().withComments(emptyList()));
                        }

                        List<Statement> variableAssignments = new ArrayList<>();
                        final Statement newLambdaStatement = extractExpressionArguments(methodStatement, lambdaStatement, variableAssignments);
                        J.MethodInvocation newAssertThrows = methodInvocation.withArguments(
                                ListUtils.map(arguments, (argIdx, argument) -> {
                                    if (argIdx == 1) {
                                        // Only retain the last statement in the lambda block
                                        return lambda.withBody(body.withStatements(singletonList(newLambdaStatement)));
                                    }
                                    return argument;
                                })
                        );

                        if (assertThrowsWithVarDec == null) {
                            variableAssignments.add(newAssertThrows);
                            return variableAssignments;
                        }

                        J.VariableDeclarations.NamedVariable newAssertThrowsVar = assertThrowsVar.withInitializer(newAssertThrows);
                        variableAssignments.add(assertThrowsWithVarDec.withVariables(singletonList(newAssertThrowsVar)));
                        return variableAssignments;
                    });
                })));
                updateCursor(methodDeclaration);
                return methodDeclaration;
            }

            private Statement extractExpressionArguments(Statement methodStatement, Statement lambdaStatement, List<Statement> statements) {
                if (lambdaStatement instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) lambdaStatement;
                    List<Expression> lambdaArguments = new ArrayList<>(mi.getArguments().size());
                    for (Expression e : mi.getArguments()) {
                        if (e instanceof J.Identifier || e instanceof J.Literal || e instanceof J.Empty) {
                            lambdaArguments.add(e);
                            continue;
                        }

                        JavaTemplate.Builder builder = JavaTemplate.builder("#{} " + getVariableName(e) + " = #{any()};\n")
                                .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()).logCompilationWarningsAndErrors(true));

                        Object type = "Object";
                        if (e.getType() instanceof JavaType.Primitive) {
                            type = e.getType().toString();
                        } else if (e.getType() != null && TypeUtils.asClass(e.getType()) != null) {
                            type = TypeUtils.asClass(e.getType()).getClassName();
                            maybeAddImport(TypeUtils.asFullyQualified(e.getType()).getFullyQualifiedName(), false);
                        }

                        J.VariableDeclarations varDecl = new J.VariableDeclarations(Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                Collections.emptyList(),
                                Collections.emptyList(),
                                new J.Identifier(Tree.randomId(),
                                        Space.EMPTY,
                                        Markers.EMPTY,
                                        Collections.emptyList(),
                                        type.toString(),
                                        e.getType(),
                                        null),
                                null,
                                Collections.emptyList(),
                                Arrays.asList(JRightPadded.build(new J.VariableDeclarations.NamedVariable(Tree.randomId(),
                                        Space.SINGLE_SPACE,
                                        Markers.EMPTY,
                                        new J.Identifier(Tree.randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                Collections.emptyList(),
                                                getVariableName(e),
                                                e.getType(),
                                                new JavaType.Variable(null, 0, getVariableName(e), null, e.getType(), null)),
                                        Collections.emptyList(),
                                        JLeftPadded.build((Expression)e.withPrefix(Space.SINGLE_SPACE)).withBefore(Space.SINGLE_SPACE),
                                        new JavaType.Variable(null, 0, getVariableName(e), null, e.getType(), null)))));

                        J.Identifier name = varDecl.getVariables().get(0).getName();
                        statements.add(varDecl.withPrefix(methodStatement.getPrefix().withComments(emptyList())));
                        lambdaArguments.add(name);
                    }
                    lambdaStatement = mi.withArguments(lambdaArguments);
                }
                return lambdaStatement;
            }

            private String getVariableName(Expression e) {
                if (e instanceof J.MethodInvocation) {
                    String name = ((J.MethodInvocation) e).getSimpleName();
                    name = name.replaceAll("^get", "");
                    name = StringUtils.uncapitalize(name);
                    return VariableNameUtils.generateVariableName(!name.isEmpty() ? name : "x", new Cursor(getCursor(), e), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                }
                return VariableNameUtils.generateVariableName("x", new Cursor(getCursor(), e), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
            }
        });
    }
}
