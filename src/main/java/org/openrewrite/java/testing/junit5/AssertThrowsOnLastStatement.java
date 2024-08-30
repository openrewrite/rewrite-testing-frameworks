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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.staticanalysis.LambdaBlockToExpression;

import java.util.List;


public class AssertThrowsOnLastStatement extends Recipe {

    private static final String ASSERTIONS_FQN = "org.junit.jupiter.api.Assertions";

    @Override
    public String getDisplayName() {
        return "Applies Junit 5 assertThrows on last statement in lamdba block only";
    }

    @Override
    public String getDescription() {
        return "Applies Junit 5 assertThrows on last statement in lambda block only.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // TODO change to uses method below?
        return Preconditions.check(new UsesType<>(ASSERTIONS_FQN, false), new AssertThrowsOnLastStatementVisitor());
    }

    private static class AssertThrowsOnLastStatementVisitor extends JavaIsoVisitor<ExecutionContext> {

        private JavaParser.@Nullable Builder<?, ?> javaParser;

        private JavaParser.Builder<?, ?> javaParser(ExecutionContext ctx) {
            if (javaParser == null) {
                javaParser = JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9");
            }
            return javaParser;

        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);
            List<Statement> methodStatements = m.getBody().getStatements();

            for (Statement methodStatement : methodStatements) {
                J statementToCheck = methodStatement;
                if (methodStatement instanceof J.VariableDeclarations) {
                    J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) methodStatement;
                    List<J.VariableDeclarations.NamedVariable> variables = variableDeclarations.getVariables();
                    if (variables.isEmpty()) {
                        continue;
                    }
                    statementToCheck = variables.get(0).getInitializer();
                }

                if (!(statementToCheck instanceof J.MethodInvocation)) {
                    continue;
                }

                J.MethodInvocation methodInvocation = (J.MethodInvocation) statementToCheck;
                if (methodInvocation.getMethodType() == null || !methodInvocation.getName().getSimpleName().equals("assertThrows")) {
                    continue;
                }

                if (!ASSERTIONS_FQN.equals(methodInvocation.getMethodType().getDeclaringType().getFullyQualifiedName())) {
                    continue;
                }

                List<Expression> arguments = methodInvocation.getArguments();
                if (arguments.size() <= 1) {
                    continue;
                }

                Expression arg = arguments.get(1);
                if (!(arg instanceof J.Lambda)) {
                    continue;
                }

                J.Lambda lambda = (J.Lambda) arg;
                if (!(lambda.getBody() instanceof J.Block)) {
                    continue;
                }

                J.Block body = (J.Block) lambda.getBody();
                if (body == null) {
                    continue;
                }

                List<Statement> lambdaStatements = body.getStatements();
                if (lambdaStatements.size() <= 1) {
                    continue;
                }

                // move all the statements from the body into before the method invocation, except last one
                JavaCoordinates beforeCoords = methodStatement.getCoordinates().before();
                int lastStatementIdx = lambdaStatements.size() - 1;
                Statement last = lambdaStatements.get(lastStatementIdx);
                lambdaStatements.remove(lastStatementIdx);
                for (Statement statement : lambdaStatements) {
                    // add the new statements before the assertThrows
                }
            }

            super.doAfterVisit(new LambdaBlockToExpression().getVisitor());
            return m;
        }
    }
}
