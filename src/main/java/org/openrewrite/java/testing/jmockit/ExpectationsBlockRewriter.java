/*
 * Copyright 2024 the original author or authors.
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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

class ExpectationsBlockRewriter {

    private static final String WHEN_TEMPLATE_PREFIX = "when(#{any()}).";
    private static final String RETURN_TEMPLATE_PREFIX = "thenReturn(";
    private static final String THROW_TEMPLATE_PREFIX = "thenThrow(";
    private static final String PRIMITIVE_TEMPLATE_FIELD = "#{}";
    private static final String THROWABLE_TEMPLATE_FIELD = "#{any()}";

    private static String getObjectTemplateField(String fqn) {
        return "#{any(" + fqn + ")}";
    }

    private final JavaVisitor<ExecutionContext> visitor;
    private final ExecutionContext ctx;
    private final J.NewClass newExpectations;
    // index of the Expectations block in the method body
    private final int bodyStatementIndex;
    private J.Block methodBody;
    private JavaCoordinates nextStatementCoordinates;

    // keep track of the additional statements being added to the method body, which impacts the statement indices
    // used with bodyStatementIndex to obtain the coordinates of the next statement to be written
    private int numStatementsAdded = 0;

    ExpectationsBlockRewriter(JavaVisitor<ExecutionContext> visitor, ExecutionContext ctx, J.Block methodBody,
                              J.NewClass newExpectations, int bodyStatementIndex) {
        this.visitor = visitor;
        this.ctx = ctx;
        this.methodBody = methodBody;
        this.newExpectations = newExpectations;
        this.bodyStatementIndex = bodyStatementIndex;
        nextStatementCoordinates = newExpectations.getCoordinates().replace();
    }

    J.Block rewriteMethodBody() {
        visitor.maybeRemoveImport("mockit.Expectations");

        assert newExpectations.getBody() != null;
        J.Block expectationsBlock = (J.Block) newExpectations.getBody().getStatements().get(0);

        // rewrite the argument matchers in the expectations block
        ArgumentMatchersRewriter amr = new ArgumentMatchersRewriter(visitor, ctx, expectationsBlock);
        expectationsBlock = amr.rewriteExpectationsBlock();

        // iterate over the expectations statements and rebuild the method body
        List<Statement> expectationStatements = new ArrayList<>();
        for (Statement expectationStatement : expectationsBlock.getStatements()) {
            if (expectationStatement instanceof J.MethodInvocation) {
                // handle returns statements
                J.MethodInvocation invocation = (J.MethodInvocation) expectationStatement;
                if (invocation.getSelect() == null && invocation.getName().getSimpleName().equals("returns")) {
                    expectationStatements.add(expectationStatement);
                    continue;
                }
                // if a new method invocation is found, apply the template to the previous statements
                if (!expectationStatements.isEmpty()) {
                    // apply template to build new method body
                    rewriteMethodBody(expectationStatements);

                    // reset statements for next expectation
                    expectationStatements = new ArrayList<>();
                }
            }
            expectationStatements.add(expectationStatement);
        }

        // handle the last statement
        if (!expectationStatements.isEmpty()) {
            rewriteMethodBody(expectationStatements);
        }

        return methodBody;
    }

    private void rewriteMethodBody(List<Statement> expectationStatements) {
        final MockInvocationResults mockInvocationResults = buildMockInvocationResults(expectationStatements);
        if (mockInvocationResults == null || !(expectationStatements.get(0) instanceof J.MethodInvocation)) {
            // invalid Expectations block, cannot rewrite
            return;
        }
        J.MethodInvocation invocation = (J.MethodInvocation) expectationStatements.get(0);
        if (!mockInvocationResults.getResults().isEmpty()) {
            // rewrite the statement to mockito if there are results
            rewriteExpectationResult(mockInvocationResults.getResults(), invocation);
        } else if (nextStatementCoordinates.isReplacement()) {
            // if there are no results and the Expectations block is not yet replaced, remove it
            removeExpectationsStatement();
        }
        if (mockInvocationResults.getTimes() != null) {
            writeMethodVerification(invocation, mockInvocationResults.getTimes(), "times");
        }
        if (mockInvocationResults.getMinTimes() != null) {
            writeMethodVerification(invocation, mockInvocationResults.getMinTimes(), "atLeast");
        }
        if (mockInvocationResults.getMaxTimes() != null) {
            writeMethodVerification(invocation, mockInvocationResults.getMaxTimes(), "atMost");
        }
    }

    private void rewriteExpectationResult(List<Expression> results, J.MethodInvocation invocation) {
        String template = getMockitoStatementTemplate(results);
        if (template == null) {
            // invalid template, cannot rewrite
            return;
        }
        visitor.maybeAddImport("org.mockito.Mockito", "when");

        List<Object> templateParams = new ArrayList<>();
        templateParams.add(invocation);
        templateParams.addAll(results);

        methodBody = JavaTemplate.builder(template)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                .staticImports("org.mockito.Mockito.*")
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodBody),
                        nextStatementCoordinates,
                        templateParams.toArray()
                );
        if (!nextStatementCoordinates.isReplacement()) {
            numStatementsAdded += 1;
        }

        // the next statement coordinates are directly after the most recently written statement
        nextStatementCoordinates = methodBody.getStatements().get(bodyStatementIndex + numStatementsAdded)
                .getCoordinates().after();
    }

    private void removeExpectationsStatement() {
        methodBody = JavaTemplate.builder("")
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodBody),
                        nextStatementCoordinates
                );

        // the next statement coordinates are directly after the most recently added statement, or the first statement
        // of the test method body if the Expectations block was the first statement
        nextStatementCoordinates = bodyStatementIndex == 0 ? methodBody.getCoordinates().firstStatement() :
                methodBody.getStatements().get(bodyStatementIndex + numStatementsAdded).getCoordinates().after();
    }

    private void writeMethodVerification(J.MethodInvocation invocation, Expression times, String verificationMode) {
        String fqn = getInvocationSelectFullyQualifiedClassName(invocation);
        if (fqn == null) {
            // cannot write a verification statement for an invocation without a select field
            return;
        }
        visitor.maybeAddImport("org.mockito.Mockito", "verify");
        visitor.maybeAddImport("org.mockito.Mockito", verificationMode);

        String verifyTemplate = getVerifyTemplate(invocation.getArguments(), fqn, verificationMode);
        Object[] templateParams = new Object[] {
                invocation.getSelect(),
                times,
                invocation.getName().getSimpleName()
        };
        methodBody = JavaTemplate.builder(verifyTemplate)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                .staticImports("org.mockito.Mockito.*")
                .imports(fqn)
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodBody),
                        methodBody.getCoordinates().lastStatement(),
                        templateParams
                );
    }

    private static String getMockitoStatementTemplate(List<Expression> results) {
        boolean buildingResults = false;
        final StringBuilder templateBuilder = new StringBuilder(WHEN_TEMPLATE_PREFIX);
        for (Expression result : results) {
            JavaType resultType = result.getType();
            if (resultType instanceof JavaType.Primitive) {
                if (result instanceof J.Literal) {
                    appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX, PRIMITIVE_TEMPLATE_FIELD);
                } else {
                    appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX,
                            getPrimitiveTemplateField((JavaType.Primitive) resultType));
                }
            } else if (resultType instanceof JavaType.Class) {
                boolean isThrowable = TypeUtils.isAssignableTo(Throwable.class.getName(), resultType);
                if (isThrowable) {
                    appendToTemplate(templateBuilder, buildingResults, THROW_TEMPLATE_PREFIX, THROWABLE_TEMPLATE_FIELD);
                } else {
                    appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX,
                            getObjectTemplateField(((JavaType.Class) resultType).getFullyQualifiedName()));
                }
            } else if (resultType instanceof JavaType.Parameterized) {
                appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX,
                        getObjectTemplateField(((JavaType.Parameterized) resultType).getType().getFullyQualifiedName()));
            } else {
                // unhandled result type
                return null;
            }
            buildingResults = true;
        }
        templateBuilder.append(");");
        return templateBuilder.toString();
    }

    private static void appendToTemplate(StringBuilder templateBuilder, boolean buildingResults, String templatePrefix,
                                         String templateField) {
        if (!buildingResults) {
            templateBuilder.append(templatePrefix);
        } else {
            templateBuilder.append(", ");
        }
        templateBuilder.append(templateField);
    }

    private static String getVerifyTemplate(List<Expression> arguments, String fqn, String verificationMode) {
        if (arguments.isEmpty()) {
            return "verify(#{any(" + fqn + ")}, "
                    + verificationMode
                    + "(#{any(int)})).#{}();";
        }
        StringBuilder templateBuilder = new StringBuilder("verify(#{any(" + fqn + ")}, "
                + verificationMode
                + "(#{any(int)})).#{}(");
        boolean hasArgument = false;
        for (Expression argument : arguments) {
            if (argument instanceof J.Empty) {
                continue;
            } else if (argument instanceof J.Literal) {
                templateBuilder.append(((J.Literal) argument).getValueSource());
            } else {
                templateBuilder.append(argument);
            }
            hasArgument = true;
            templateBuilder.append(", ");
        }
        if (hasArgument) {
            templateBuilder.delete(templateBuilder.length() - 2, templateBuilder.length());
        }
        templateBuilder.append(");");
        return templateBuilder.toString();
    }

    private static MockInvocationResults buildMockInvocationResults(List<Statement> expectationStatements) {
        final MockInvocationResults resultWrapper = new MockInvocationResults();
        for (int i = 1; i < expectationStatements.size(); i++) {
            Statement expectationStatement = expectationStatements.get(i);
            if (expectationStatement instanceof J.MethodInvocation) {
                // handle returns statement
                J.MethodInvocation invocation = (J.MethodInvocation) expectationStatement;
                for (Expression argument : invocation.getArguments()) {
                    resultWrapper.addResult(argument);
                }
                continue;
            }
            J.Assignment assignment = (J.Assignment) expectationStatement;
            J.Identifier identifier = JMockitUtils.getVariableIdentifierFromAssignment(assignment);
            if (identifier == null) {
                // unhandled assignment variable type
                return null;
            }
            switch (identifier.getSimpleName()) {
                case "result":
                    resultWrapper.addResult(assignment.getAssignment());
                    break;
                case "times":
                    resultWrapper.setTimes(assignment.getAssignment());
                    break;
                case "minTimes":
                    resultWrapper.setMinTimes(assignment.getAssignment());
                    break;
                case "maxTimes":
                    resultWrapper.setMaxTimes(assignment.getAssignment());
                    break;
            }
        }
        return resultWrapper;
    }

    private static String getPrimitiveTemplateField(JavaType.Primitive primitiveType) {
        switch (primitiveType) {
            case Boolean:
                return "#{any(boolean)}";
            case Byte:
                return "#{any(byte)}";
            case Char:
                return "#{any(char)}";
            case Double:
                return "#{any(double)}";
            case Float:
                return "#{any(float)}";
            case Int:
                return "#{any(int)}";
            case Long:
                return "#{any(long)}";
            case Short:
                return "#{any(short)}";
            case String:
                return "#{}";
            case Null:
                return "#{any()}";
            default:
                return null;
        }
    }

    private static String getInvocationSelectFullyQualifiedClassName(J.MethodInvocation invocation) {
        Expression select = invocation.getSelect();
        if (select == null || select.getType() == null) {
            return null;
        }
        String fqn = null;
        if (select.getType() instanceof JavaType.FullyQualified) {
            fqn = ((JavaType.FullyQualified) select.getType()).getFullyQualifiedName();
        }
        return fqn;
    }

    private static class MockInvocationResults {
        private final List<Expression> results = new ArrayList<>();
        private Expression times;
        private Expression minTimes;
        private Expression maxTimes;

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

        private Expression getMinTimes() {
            return minTimes;
        }

        private void setMinTimes(Expression minTimes) {
            this.minTimes = minTimes;
        }

        private Expression getMaxTimes() {
            return maxTimes;
        }

        private void setMaxTimes(Expression maxTimes) {
            this.maxTimes = maxTimes;
        }
    }
}
