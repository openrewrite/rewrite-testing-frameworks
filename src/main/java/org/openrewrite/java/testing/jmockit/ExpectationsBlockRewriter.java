package org.openrewrite.java.testing.jmockit;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class ExpectationsBlockRewriter {

    private static final String WHEN_TEMPLATE_PREFIX = "when(#{any()}).";
    private static final String RETURN_TEMPLATE_PREFIX = "thenReturn(";
    private static final String PRIMITIVE_TEMPLATE_PARAM = "#{}";
    private static final String THROW_TEMPLATE_PREFIX = "thenThrow(";
    private static final String THROWABLE_TEMPLATE_PARAM = "#{any()}";

    private final JavaVisitor<ExecutionContext> visitor;
    private final ExecutionContext ctx;
    private final List<Statement> bodyStatements;
    private final int bodyStatementIndex;
    private J.Block methodBody;
    private JavaCoordinates nextStatementCoordinates;
    private int mockitoStatementIndex = 0;

    ExpectationsBlockRewriter(JavaVisitor<ExecutionContext> visitor, ExecutionContext ctx, J.Block methodBody,
                              List<Statement> bodyStatements, int bodyStatementIndex) {
        this.visitor = visitor;
        this.ctx = ctx;
        this.methodBody = methodBody;
        this.bodyStatements = bodyStatements;
        this.bodyStatementIndex = bodyStatementIndex;
    }

    J.Block rewriteMethodBody() {
        visitor.maybeRemoveImport("mockit.Expectations");

        J.NewClass nc = (J.NewClass) bodyStatements.get(bodyStatementIndex);
        assert nc.getBody() != null;
        J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);

        // rewrite the argument matchers in the expectations block
        ArgumentMatchersRewriter amr = new ArgumentMatchersRewriter(visitor, ctx, expectationsBlock);
        expectationsBlock = amr.rewriteExpectationsBlock();

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

        return methodBody;
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
        visitor.maybeAddImport("org.mockito.Mockito", "when");
        String template = getMockitoStatementTemplate(results);

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
                        new Cursor(visitor.getCursor(), methodBody),
                        nextStatementCoordinates
                );
        nextStatementCoordinates = bodyStatementIndex == 0 ? methodBody.getCoordinates().firstStatement() :
                methodBody.getStatements().get(bodyStatementIndex + mockitoStatementIndex).getCoordinates().after();
        return methodBody;
    }

    private J.Block writeMethodVerification(ExecutionContext ctx, J.Block methodBody, String fqn,
                                            J.MethodInvocation invocation, Expression times) {
        visitor.maybeAddImport("org.mockito.Mockito", "verify");
        visitor.maybeAddImport("org.mockito.Mockito", "times");
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
                        new Cursor(visitor.getCursor(), methodBody),
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
