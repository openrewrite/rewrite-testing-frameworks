/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.jmockit;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Value
@EqualsAndHashCode(callSuper = false)
public class JMockitExpectationsToMockito extends Recipe {
    @Override
    public String getDisplayName() {
        return "Rewrite JMockit Expectations";
    }

    @Override
    public String getDescription() {
        return "Rewrites JMockit `Expectations` blocks to Mockito statements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("mockit.Expectations", false),
                new RewriteExpectationsVisitor());
    }

    private static class RewriteExpectationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String WHEN_TEMPLATE_PREFIX = "when(#{any()}).";
        private static final String RETURN_TEMPLATE_PREFIX = "thenReturn(";
        private static final String PRIMITIVE_TEMPLATE_PARAM = "#{}";
        private static final String THROW_TEMPLATE_PREFIX = "thenThrow(";
        private static final String THROWABLE_TEMPLATE_PARAM = "#{any()}";
        private int mockitoStatementIndex = 0;
        private JavaCoordinates nextStatementCoordinates;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }
            try {
                // rewrite the statements that are not mock expectations or verifications
                SetupStatementsRewriter ssr = new SetupStatementsRewriter(this, md.getBody());
                J.Block methodBody = ssr.rewrite();
                List<Statement> statements = methodBody.getStatements();

                // iterate over each statement in the method body, find Expectations blocks and rewrite them
                for (int bodyStatementIndex = 0; bodyStatementIndex < statements.size(); bodyStatementIndex++) {
                    if (!JMockitUtils.isExpectationsNewClassStatement(statements.get(bodyStatementIndex))) {
                        continue;
                    }
                    // we have a valid Expectations block, update imports and rewrite with Mockito statements
                    maybeRemoveImport("mockit.Expectations");

                    J.NewClass nc = (J.NewClass) statements.get(bodyStatementIndex);
                    assert nc.getBody() != null;
                    J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);

                    // rewrite the argument matchers
                    ArgumentMatchersRewriter amr = new ArgumentMatchersRewriter(this, expectationsBlock, ctx);
                    expectationsBlock = amr.rewrite();

                    // iterate over the expectations statements and rebuild the method body
                    List<Statement> expectationStatements = new ArrayList<>();
                    nextStatementCoordinates = nc.getCoordinates().replace();
                    mockitoStatementIndex = 0;
                    for (Statement expectationStatement : expectationsBlock.getStatements()) {
                        if (expectationStatement instanceof J.MethodInvocation) {
                            // handle returns statements
                            J.MethodInvocation invocation = (J.MethodInvocation) expectationStatement;
                            if (invocation.getSelect() == null && invocation.getName().getSimpleName().equals("returns")) {
                                expectationStatements.add(expectationStatement);
                                continue;
                            }
                            if (!expectationStatements.isEmpty()) {
                                // apply template to build new method body
                                methodBody = rewriteMethodBody(ctx, expectationStatements, methodBody, bodyStatementIndex);

                                // reset statements for next expectation
                                expectationStatements = new ArrayList<>();
                            }
                        }
                        expectationStatements.add(expectationStatement);
                    }

                    // handle the last statement
                    if (!expectationStatements.isEmpty()) {
                        methodBody = rewriteMethodBody(ctx, expectationStatements, methodBody, bodyStatementIndex);
                    }
                }
                return md.withBody(methodBody);
            } catch (Exception e) {
                System.err.println("DEBUG: Exception rewriting method body: " + e.getMessage() + "\n\n");
                e.printStackTrace();
                // if anything goes wrong, just return the original method declaration
                return md;
            }
        }

        private J.Block rewriteMethodBody(ExecutionContext ctx, List<Statement> expectationStatements,
                                          J.Block methodBody, int bodyStatementIndex) {
            J.MethodInvocation invocation = (J.MethodInvocation) expectationStatements.get(0);
            final MockInvocationResults mockInvocationResults = buildMockInvocationResults(expectationStatements);

            if (mockInvocationResults.getResults().isEmpty() && nextStatementCoordinates.isReplacement()) {
                // remove mock method invocations without expectations or verifications
                methodBody = removeExpectationsStatement(methodBody, bodyStatementIndex);
            }

            if (!mockInvocationResults.getResults().isEmpty()) {
                methodBody = rewriteExpectationResult(ctx, methodBody, bodyStatementIndex,
                        mockInvocationResults.getResults(), invocation);
            } else if (nextStatementCoordinates.isReplacement()) {
                methodBody = removeExpectationsStatement(methodBody, bodyStatementIndex);
            }
            if (mockInvocationResults.getTimes() != null) {
                String fqn = getInvocationSelectFullyQualifiedClassName(invocation);
                methodBody = writeMethodVerification(ctx, methodBody, fqn, invocation,
                        mockInvocationResults.getTimes());
            }

            return methodBody;
        }

        private J.Block rewriteExpectationResult(ExecutionContext ctx, J.Block methodBody, int bodyStatementIndex,
                                                 List<Expression> results, J.MethodInvocation invocation) {
            maybeAddImport("org.mockito.Mockito", "when");
            String template = getMockitoStatementTemplate(results);

            List<Object> templateParams = new ArrayList<>();
            templateParams.add(invocation);
            templateParams.addAll(results);

            methodBody = JavaTemplate.builder(template)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports("org.mockito.Mockito.*")
                    .build()
                    .apply(
                            new Cursor(getCursor(), methodBody),
                            nextStatementCoordinates,
                            templateParams.toArray()
                    );
            // move the statement index forward if one was added
            if (!nextStatementCoordinates.isReplacement()) {
                mockitoStatementIndex += 1;
            }
            nextStatementCoordinates = methodBody.getStatements().get(bodyStatementIndex + mockitoStatementIndex)
                    .getCoordinates().after();
            return methodBody;
        }

        private J.Block removeExpectationsStatement(J.Block methodBody, int bodyStatementIndex) {
            methodBody = JavaTemplate.builder("")
                    .javaParser(JavaParser.fromJavaVersion())
                    .build()
                    .apply(
                            new Cursor(getCursor(), methodBody),
                            nextStatementCoordinates
                    );
            nextStatementCoordinates = bodyStatementIndex == 0 ? methodBody.getCoordinates().firstStatement() :
                    methodBody.getStatements().get(bodyStatementIndex + mockitoStatementIndex).getCoordinates().after();
            return methodBody;
        }

        private J.Block writeMethodVerification(ExecutionContext ctx, J.Block methodBody, String fqn,
                                                J.MethodInvocation invocation, Expression times) {
            maybeAddImport("org.mockito.Mockito", "verify");
            maybeAddImport("org.mockito.Mockito", "times");
            String verifyTemplate = getVerifyTemplate(fqn, invocation.getArguments());
            Object[] templateParams = new Object[] {
                    invocation.getSelect(),
                    times,
                    invocation.getName().getSimpleName()
            };
            return JavaTemplate.builder(verifyTemplate)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports("org.mockito.Mockito.*")
                    .imports(fqn)
                    .build()
                    .apply(
                            new Cursor(getCursor(), methodBody),
                            methodBody.getCoordinates().lastStatement(),
                            templateParams
                    );
        }

        private static String getObjectTemplate(String fqn) {
            return "#{any(" + fqn + ")}";
        }

        private static String getMockitoStatementTemplate(List<Expression> results) {
            StringBuilder templateBuilder = new StringBuilder(WHEN_TEMPLATE_PREFIX);
            boolean buildingResults = false;
            for (Expression result : results) {
                JavaType resultType = result.getType();
                if (resultType instanceof JavaType.Primitive) {
                    buildingResults = appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX, PRIMITIVE_TEMPLATE_PARAM);
                } else if (resultType instanceof JavaType.Class) {
                    boolean isThrowable = TypeUtils.isAssignableTo(Throwable.class.getName(), resultType);
                    if (isThrowable) {
                        buildingResults = appendToTemplate(templateBuilder, buildingResults, THROW_TEMPLATE_PREFIX, THROWABLE_TEMPLATE_PARAM);
                    } else {
                        buildingResults = appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX,
                                getObjectTemplate(((JavaType.Class) resultType).getFullyQualifiedName()));
                    }
                } else if (resultType instanceof JavaType.Parameterized) {
                    buildingResults = appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX,
                            getObjectTemplate(((JavaType.Parameterized) resultType).getType().getFullyQualifiedName()));
                } else {
                    throw new IllegalStateException("Unexpected expression type for template: " + result.getType());
                }
            }
            templateBuilder.append(");");
            return templateBuilder.toString();
        }

        private static boolean appendToTemplate(StringBuilder templateBuilder, boolean buildingResults,
                                                String baseTemplate, String paramTemplate) {
            if (!buildingResults) {
                templateBuilder.append(baseTemplate);
            } else {
                templateBuilder.append(", ");
            }
            templateBuilder.append(paramTemplate);
            return true;
        }

        private static String getVerifyTemplate(String fqn, List<Expression> arguments) {
            if (arguments.isEmpty()) {
                return "verify(#{any(" + fqn + ")}, times(#{any(int)})).#{}();";
            }
            StringBuilder templateBuilder = new StringBuilder("verify(#{any(" + fqn + ")}, times(#{any(int)})).#{}(");
            for (Expression argument : arguments) {
                if (argument instanceof J.Literal) {
                    templateBuilder.append(((J.Literal) argument).getValueSource());
                } else {
                    templateBuilder.append(argument);
                }
                templateBuilder.append(", ");
            }
            templateBuilder.delete(templateBuilder.length() - 2, templateBuilder.length());
            templateBuilder.append(");");
            return templateBuilder.toString();
        }

        private static MockInvocationResults buildMockInvocationResults(List<Statement> expectationStatements) {
            int numResults = 0;
            boolean hasTimes = false;
            MockInvocationResults resultWrapper = new MockInvocationResults();
            for (int i = 1; i < expectationStatements.size(); i++) {
                Statement expectationStatement = expectationStatements.get(i);
                if (expectationStatement instanceof J.MethodInvocation) {
                    if (hasTimes) {
                        throw new IllegalStateException("times statement must be last in expectation");
                    }
                    // handle returns statement
                    J.MethodInvocation invocation = (J.MethodInvocation) expectationStatement;
                    for (Expression argument : invocation.getArguments()) {
                        numResults += 1;
                        resultWrapper.addResult(argument);
                    }
                    continue;
                }
                J.Assignment assignment = (J.Assignment) expectationStatement;
                if (!(assignment.getVariable() instanceof J.Identifier)) {
                    throw new IllegalStateException("Unexpected assignment variable type: " + assignment.getVariable());
                }
                J.Identifier identifier = (J.Identifier) assignment.getVariable();
                boolean isResult = identifier.getSimpleName().equals("result");
                boolean isTimes = identifier.getSimpleName().equals("times");
                if (isResult) {
                    if (hasTimes) {
                        throw new IllegalStateException("times statement must be last in expectation");
                    }
                    numResults += 1;
                    resultWrapper.addResult(assignment.getAssignment());
                } else if (isTimes) {
                    hasTimes = true;
                    if (numResults > 1) {
                        throw new IllegalStateException("multiple results cannot be used with times statement");
                    }
                    resultWrapper.setTimes(assignment.getAssignment());
                }
            }
            return resultWrapper;
        }

        private static String getInvocationSelectFullyQualifiedClassName(J.MethodInvocation invocation) {
            Expression select = invocation.getSelect();
            if (select == null || select.getType() == null) {
                throw new IllegalStateException("Missing type information for invocation select field: " + select);
            }
            String fqn = ""; // default to empty string to support method invocations
            if (select instanceof J.Identifier) {
                fqn = ((JavaType.FullyQualified) Objects.requireNonNull(select.getType())).getFullyQualifiedName();
            }
            return fqn;
        }

        private static class MockInvocationResults {
            private final List<Expression> results = new ArrayList<>();
            private Expression times;

            private List<Expression> getResults() {
                return results;
            }
            private void addResult(Expression result) {
                results.add(result);
            }
            private Expression getTimes() {
                return times;
            }
            private void setTimes(Expression times) {
                this.times = times;
            }
        }
    }
}
