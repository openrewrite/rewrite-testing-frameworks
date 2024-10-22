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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.Collections;
import java.util.List;

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
                return m.withBody(m.getBody().withStatements(ListUtils.flatMap(m.getBody().getStatements(), methodStatement -> {
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
                    return ListUtils.map(lambdaStatements, (idx, lambdaStatement) -> {
                        if (idx < lambdaStatements.size() - 1) {
                            return lambdaStatement.withPrefix(methodStatement.getPrefix().withComments(Collections.emptyList()));
                        }

                        J.MethodInvocation newAssertThrows = methodInvocation.withArguments(
                                ListUtils.map(arguments, (argIdx, argument) -> {
                                    if (argIdx == 1) {
                                        // Only retain the last statement in the lambda block
                                        return lambda.withBody(body.withStatements(singletonList(lambdaStatement)));
                                    }
                                    return argument;
                                })
                        );

                        if (assertThrowsWithVarDec == null) {
                            return newAssertThrows;
                        }

                        J.VariableDeclarations.NamedVariable newAssertThrowsVar = assertThrowsVar.withInitializer(newAssertThrows);
                        return assertThrowsWithVarDec.withVariables(singletonList(newAssertThrowsVar));
                    });
                })));
            }
        });
    }
}
