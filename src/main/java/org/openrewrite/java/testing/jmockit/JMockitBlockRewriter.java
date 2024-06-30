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
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.java.testing.jmockit.JMockitBlockType.Verifications;

class JMockitBlockRewriter {

    private static final String WHEN_TEMPLATE_PREFIX = "when(#{any()}).";
    private static final String RETURN_TEMPLATE_PREFIX = "thenReturn(";
    private static final String THROW_TEMPLATE_PREFIX = "thenThrow(";
    private static final String LITERAL_TEMPLATE_FIELD = "#{}";
    private static final String ANY_TEMPLATE_FIELD = "#{any()}";
    private static final String MOCKITO_IMPORT_FQN_PREFX = "org.mockito.Mockito";

    private static String getObjectTemplateField(String fqn) {
        return "#{any(" + fqn + ")}";
    }

    private final JavaVisitor<ExecutionContext> visitor;
    private final ExecutionContext ctx;
    private final J.NewClass newExpectations;
    private final JMockitBlockType blockType;
    // index of the Expectations block in the method body
    private final int bodyStatementIndex;
    private J.Block methodBody;
    private JavaCoordinates nextStatementCoordinates;

    private boolean rewriteFailed = false;

    boolean isRewriteFailed() {
        return rewriteFailed;
    }

    // keep track of the additional statements being added to the method body, which impacts the statement indices
    // used with bodyStatementIndex to obtain the coordinates of the next statement to be written
    private int numStatementsAdded = 0;

    JMockitBlockRewriter(JavaVisitor<ExecutionContext> visitor, ExecutionContext ctx, J.Block methodBody,
                         J.NewClass newExpectations, int bodyStatementIndex, JMockitBlockType blockType) {
        this.visitor = visitor;
        this.ctx = ctx;
        this.methodBody = methodBody;
        this.newExpectations = newExpectations;
        this.bodyStatementIndex = bodyStatementIndex;
        this.blockType = blockType;
        nextStatementCoordinates = newExpectations.getCoordinates().replace();
    }

    J.Block rewriteMethodBody() {
        visitor.maybeRemoveImport(blockType.getFqn()); // eg mockit.Expectations

        assert newExpectations.getBody() != null;
        J.Block jmockitBlock = (J.Block) newExpectations.getBody().getStatements().get(0);
        if (jmockitBlock.getStatements().isEmpty()) {
            // empty Expectations block, remove it
            removeBlock();
            return methodBody;
        }

        // rewrite the argument matchers in the expectations block
        ArgumentMatchersRewriter amr = new ArgumentMatchersRewriter(visitor, ctx, jmockitBlock);
        jmockitBlock = amr.rewriteJMockitBlock();

        // iterate over the statements and build a list of grouped method invocations and related statements eg times
        List<List<Statement>> methodInvocationsToRewrite = new ArrayList<>();
        int methodInvocationIdx = -1;
        for (Statement jmockitBlockStatement : jmockitBlock.getStatements()) {
            if (jmockitBlockStatement instanceof J.MethodInvocation) {
                // ensure it's not a returns statement, we add that later to related statements
                J.MethodInvocation invocation = (J.MethodInvocation) jmockitBlockStatement;
                if (invocation.getSelect() != null && !invocation.getName().getSimpleName().equals("returns")) {
                    methodInvocationIdx++;
                    methodInvocationsToRewrite.add(new ArrayList<>());
                }
            }

            if (methodInvocationIdx != -1) {
                methodInvocationsToRewrite.get(methodInvocationIdx).add(jmockitBlockStatement);
            }
        }

        // remove the jmockit block
        if (nextStatementCoordinates.isReplacement()) {
            removeBlock();
        }

        // now rewrite
        methodInvocationsToRewrite.forEach(this::rewriteMethodInvocation);
        return methodBody;
    }

    private void rewriteMethodInvocation(List<Statement> statementsToRewrite) {
        final MockInvocationResults mockInvocationResults = buildMockInvocationResults(statementsToRewrite);
        if (mockInvocationResults == null) {
            // invalid block, cannot rewrite
            rewriteFailed = true;
            return;
        }

        J.MethodInvocation invocation = (J.MethodInvocation) statementsToRewrite.get(0);
        boolean hasResults = !mockInvocationResults.getResults().isEmpty();
        if (hasResults) {
            rewriteResult(invocation, mockInvocationResults.getResults());
        }

        boolean hasTimes = false;
        if (mockInvocationResults.getTimes() != null) {
            hasTimes = true;
            rewriteVerify(invocation, mockInvocationResults.getTimes(), "times");
        }
        if (mockInvocationResults.getMinTimes() != null) {
            hasTimes = true;
            rewriteVerify(invocation, mockInvocationResults.getMinTimes(), "atLeast");
        }
        if (mockInvocationResults.getMaxTimes() != null) {
            hasTimes = true;
            rewriteVerify(invocation, mockInvocationResults.getMaxTimes(), "atMost");
        }
        if (!hasResults && !hasTimes) {
            rewriteVerify(invocation, null, null);
        }
    }

    private void removeBlock() {
        methodBody = JavaTemplate.builder("")
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodBody),
                        nextStatementCoordinates
                );
        if (bodyStatementIndex == 0) {
            nextStatementCoordinates = methodBody.getCoordinates().firstStatement();
        } else {
            setNextCoordinatesAfterLastStatementAdded(0);
        }
    }

    private void rewriteResult(J.MethodInvocation invocation, List<Expression> results) {
        String template = getWhenTemplate(results);
        if (template == null) {
            // invalid template, cannot rewrite
            rewriteFailed = true;
            return;
        }
        visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "when");

        List<Object> templateParams = new ArrayList<>();
        templateParams.add(invocation);
        templateParams.addAll(results);

        methodBody = rewriteTemplate(template, templateParams, nextStatementCoordinates);
        setNextCoordinatesAfterLastStatementAdded(++numStatementsAdded);
    }

    private void rewriteVerify(J.MethodInvocation invocation, @Nullable Expression times, @Nullable String verificationMode) {
        if (invocation.getSelect() == null) {
            // cannot write a verification statement for an invocation without a select field
            return;
        }

        List<Object> templateParams = new ArrayList<>();
        templateParams.add(invocation.getSelect());
        if (times != null) {
            templateParams.add(times);
        }
        templateParams.add(invocation.getName().getSimpleName());
        String verifyTemplate = getVerifyTemplate(invocation.getArguments(), verificationMode, templateParams);
        if (verifyTemplate == null) {
            // invalid template, cannot rewrite
            rewriteFailed = true;
            return;
        }

        JavaCoordinates verifyCoordinates;
        if (this.blockType == Verifications) {
            // for Verifications, replace the Verifications block
            verifyCoordinates = nextStatementCoordinates;
        } else {
            // for Expectations put the verify at the end of the method
            verifyCoordinates = methodBody.getCoordinates().lastStatement();
        }

        methodBody = rewriteTemplate(verifyTemplate, templateParams, verifyCoordinates);
        if (this.blockType == Verifications) {
            setNextCoordinatesAfterLastStatementAdded(++numStatementsAdded);
        }

        // do this last making sure rewrite worked and specify hasReference=false because in verify case it cannot find
        // the static reference in AddImport class, and getSelect() returns not null
        visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "verify", false);
        if (verificationMode != null) {
            visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, verificationMode);
        }
    }

    private void setNextCoordinatesAfterLastStatementAdded(int numStatementsAdded) {
        // the next statement coordinates are directly after the most recently written statement, calculated by
        // subtracting the removed jmockit block
        int nextStatementIdx = bodyStatementIndex + numStatementsAdded - 1;
        if (nextStatementIdx >= this.methodBody.getStatements().size()) {
            rewriteFailed = true;
        } else {
            this.nextStatementCoordinates = this.methodBody.getStatements().get(nextStatementIdx).getCoordinates().after();
        }
    }

    private J.Block rewriteTemplate(String verifyTemplate, List<Object> templateParams, JavaCoordinates
            rewriteCoords) {
        JavaTemplate.Builder builder = JavaTemplate.builder(verifyTemplate)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                .staticImports("org.mockito.Mockito.*");
        return builder
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodBody),
                        rewriteCoords,
                        templateParams.toArray()
                );
    }

    private static String getWhenTemplate(List<Expression> results) {
        boolean buildingResults = false;
        final StringBuilder templateBuilder = new StringBuilder(WHEN_TEMPLATE_PREFIX);
        for (Expression result : results) {
            JavaType resultType = result.getType();
            if (result instanceof J.Literal) {
                appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX, LITERAL_TEMPLATE_FIELD);
            } else if (resultType instanceof JavaType.Primitive) {
                String primitiveTemplateField = getPrimitiveTemplateField((JavaType.Primitive) resultType);
                if (primitiveTemplateField == null) {
                    // unhandled primitive type
                    return null;
                }
                appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX, primitiveTemplateField);
            } else if (TypeUtils.isAssignableTo(Throwable.class.getName(), resultType)) {
                appendToTemplate(templateBuilder, buildingResults, THROW_TEMPLATE_PREFIX, ANY_TEMPLATE_FIELD);
            } else if (resultType instanceof JavaType.Class) {
                appendToTemplate(templateBuilder, buildingResults, RETURN_TEMPLATE_PREFIX,
                        getObjectTemplateField(((JavaType.Class) resultType).getFullyQualifiedName()));
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

    private static void appendToTemplate(StringBuilder templateBuilder, boolean buildingResults, String
            templatePrefix,
                                         String templateField) {
        if (!buildingResults) {
            templateBuilder.append(templatePrefix);
        } else {
            templateBuilder.append(", ");
        }
        templateBuilder.append(templateField);
    }

    private static String getVerifyTemplate(List<Expression> arguments, @Nullable String
            verificationMode, List<Object> templateParams) {
        StringBuilder templateBuilder = new StringBuilder("verify(#{any()}"); // eg verify(object
        if (verificationMode != null) {
            templateBuilder.append(", ").append(verificationMode).append("(#{any(int)})"); // eg verify(object, times(2)
        }
        templateBuilder.append(").#{}("); // eg verify(object, times(2)).method(

        if (arguments.isEmpty()) {
            templateBuilder.append(");"); // eg verify(object, times(2)).method(); <- no args
            return templateBuilder.toString();
        }

        boolean hasArgument = false;
        for (Expression argument : arguments) { // eg verify(object, times(2).method(anyLong(), anyInt()
            if (argument instanceof J.Empty) {
                continue;
            } else if (argument instanceof J.Literal) {
                templateBuilder.append(((J.Literal) argument).getValueSource());
            } else {
                templateBuilder.append(ANY_TEMPLATE_FIELD);
                templateParams.add(argument);
            }
            hasArgument = true;
            templateBuilder.append(", ");
        }
        if (hasArgument) {
            templateBuilder.delete(templateBuilder.length() - 2, templateBuilder.length());
        }
        templateBuilder.append(");"); // eg verify(object, times(2).method(anyLong(), anyInt());
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
            String variableName = getVariableNameFromAssignment(assignment);
            if (variableName == null) {
                // unhandled assignment variable type
                return null;
            }
            switch (variableName) {
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

    private static String getVariableNameFromAssignment(J.Assignment assignment) {
        String name = null;
        if (assignment.getVariable() instanceof J.Identifier) {
            name = ((J.Identifier) assignment.getVariable()).getSimpleName();
        } else if (assignment.getVariable() instanceof J.FieldAccess) {
            J.FieldAccess fieldAccess = (J.FieldAccess) assignment.getVariable();
            if (fieldAccess.getTarget() instanceof J.Identifier) {
                name = fieldAccess.getSimpleName();
            }
        }
        return name;
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
                return "#{any(String)}";
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
