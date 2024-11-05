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

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.java.testing.jmockit.JMockitBlockType.*;

class JMockitBlockRewriter {

    private static final String WHEN_TEMPLATE_PREFIX = "when(#{any()}).";
    private static final String VERIFY_TEMPLATE_PREFIX = "verify(#{any()}";
    private static final String VERIFY_NO_INTERACTIONS_TEMPLATE_PREFIX = "verifyNoMoreInteractions(";
    private static final String VERIFY_IN_ORDER_TEMPLATE_PREFIX = "inOrder(";
    //private static final String VERIFY_IN_ORDER_TEMPLATE_PREFIX = "InOrder inOrder = inOrder(";
    private static final String LENIENT_TEMPLATE_PREFIX = "lenient().";
    private static final String RETURN_TEMPLATE_PREFIX = "thenReturn(";
    private static final String THROW_TEMPLATE_PREFIX = "thenThrow(";
    private static final String LITERAL_TEMPLATE_FIELD = "#{}";
    private static final String ANY_TEMPLATE_FIELD = "#{any()}";
    private static final String MOCKITO_IMPORT_FQN_PREFX = "org.mockito.Mockito";
    private static final String IN_ORDER_IMPORT_FQN = "org.mockito.InOrder";

    private static String getObjectTemplateField(String fqn) {
        return "#{any(" + fqn + ")}";
    }

    private final JavaVisitor<ExecutionContext> visitor;
    private final ExecutionContext ctx;
    private final J.NewClass newExpectations;
    private final JMockitBlockType blockType;
    private final List<JMockitBlockType> blockTypes;
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
                         J.NewClass newExpectations, int bodyStatementIndex, JMockitBlockType blockType, List<JMockitBlockType> blockTypes) {
        this.visitor = visitor;
        this.ctx = ctx;
        this.methodBody = methodBody;
        this.newExpectations = newExpectations;
        this.bodyStatementIndex = bodyStatementIndex;
        this.blockType = blockType;
        this.blockTypes = blockTypes;
        this.nextStatementCoordinates = newExpectations.getCoordinates().replace();
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
        List<J.Identifier> uniqueMocks = new ArrayList<>();
        int methodInvocationIdx = -1;
        for (Statement jmockitBlockStatement : jmockitBlock.getStatements()) {
            if (jmockitBlockStatement instanceof J.MethodInvocation) {
                J.MethodInvocation invocation = (J.MethodInvocation) jmockitBlockStatement;
                Expression select = invocation.getSelect();
                if (select instanceof J.Identifier) {
                    J.Identifier mockObj = (J.Identifier) select;
                    // ensure it's not a returns statement, we add that later to related statements
                    if (!invocation.getName().getSimpleName().equals("returns")) {
                        methodInvocationIdx++;
                        methodInvocationsToRewrite.add(new ArrayList<>());
                    }
                    if ((isFullVerifications() || isVerificationsInOrder()) && uniqueMocks.stream().noneMatch(mock -> mock.getSimpleName().equals(mockObj.getSimpleName()))) {
                        uniqueMocks.add(mockObj);
                    }
                }
            }

            // add the statements corresponding to the method invocation
            if (methodInvocationIdx != -1) {
                methodInvocationsToRewrite.get(methodInvocationIdx).add(jmockitBlockStatement);
            }
        }

        // remove the jmockit block
        if (nextStatementCoordinates.isReplacement()) {
            removeBlock();
        }

        List<Object> mocks = new ArrayList<>(uniqueMocks);
        if (isVerificationsInOrder()) {
            rewriteInOrderVerify(mocks);
        }

        // now rewrite
        methodInvocationsToRewrite.forEach(this::rewriteMethodInvocation);

        if (isFullVerifications()) {
            rewriteFullVerify(mocks);
        }
        return methodBody;
    }

    private boolean isFullVerifications() {
        return this.blockType == FullVerifications;
    }

    private boolean isVerificationsInOrder() {
        return this.blockType == VerificationsInOrder;
    }

    private void rewriteMethodInvocation(List<Statement> statementsToRewrite) {
        final MockInvocationResults mockInvocationResults = buildMockInvocationResults(statementsToRewrite);
        if (mockInvocationResults == null) {
            // invalid block, cannot rewrite
            this.rewriteFailed = true;
            return;
        }

        J.MethodInvocation invocation = (J.MethodInvocation) statementsToRewrite.get(0);
        boolean hasResults = !mockInvocationResults.getResults().isEmpty();
        boolean hasTimes = mockInvocationResults.hasAnyTimes();
        if (hasResults) {
            rewriteResult(invocation, mockInvocationResults.getResults(), hasTimes);
        }

        if (!hasResults && !hasTimes && (this.blockType == JMockitBlockType.Expectations || this.blockType.isVerifications())) {
            rewriteVerify(invocation, null, "");
            return;
        }
        if (mockInvocationResults.getTimes() != null) {
            rewriteVerify(invocation, mockInvocationResults.getTimes(), "times");
        }
        if (mockInvocationResults.getMinTimes() != null) {
            rewriteVerify(invocation, mockInvocationResults.getMinTimes(), "atLeast");
        }
        if (mockInvocationResults.getMaxTimes() != null) {
            rewriteVerify(invocation, mockInvocationResults.getMaxTimes(), "atMost");
        }
    }

    private void removeBlock() {
        methodBody = JavaTemplate.builder("")
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(new Cursor(visitor.getCursor(), methodBody), nextStatementCoordinates);
        setNextStatementCoordinates(0);
    }

    private void rewriteResult(J.MethodInvocation invocation, List<Expression> results, boolean hasTimes) {
        boolean lenient = this.blockType == NonStrictExpectations && !hasTimes;
        String template = getWhenTemplate(results, lenient);
        if (template == null) {
            // invalid template, cannot rewrite
            this.rewriteFailed = true;
            return;
        }

        List<Object> templateParams = new ArrayList<>();
        templateParams.add(invocation);
        templateParams.addAll(results);
        rewriteTemplate(template, templateParams, nextStatementCoordinates);
        if (this.rewriteFailed) {
            return;
        }

        setNextStatementCoordinates(++numStatementsAdded);
        // do this last making sure rewrite worked and specify onlyifReferenced=false because framework cannot find static
        // reference for when method invocation when another static mockit reference is added
        visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "when", false);
        if (lenient) {
            visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "lenient");
        }
    }

    private void rewriteVerify(J.MethodInvocation invocation, @Nullable Expression times, String verificationMode) {
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
        JavaCoordinates verifyCoordinates;
        if (this.blockType.isVerifications()) {
            // for Verifications, replace the Verifications block
            verifyCoordinates = nextStatementCoordinates;
        } else {
            // for Expectations put verify at the end of the method
            verifyCoordinates = methodBody.getCoordinates().lastStatement();
        }
        rewriteTemplate(verifyTemplate, templateParams, verifyCoordinates);
        if (this.rewriteFailed) {
            return;
        }

        if (this.blockType.isVerifications()) {
            setNextStatementCoordinates(++numStatementsAdded); // for Expectations, verify statements added to end of method
        }

        // do this last making sure rewrite worked and specify onlyifReferenced=false because framework cannot find the
        // static reference to verify when another static mockit reference is added
        visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "verify", false);
        if (!verificationMode.isEmpty()) {
            visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, verificationMode);
        }
    }

    private void rewriteFullVerify(ArrayList<Object> mocks) {
        if (!mocks.isEmpty()) {
            StringBuilder sb = new StringBuilder(VERIFY_NO_INTERACTIONS_TEMPLATE_PREFIX);
            mocks.forEach(mock -> sb.append(ANY_TEMPLATE_FIELD).append(",")); // verifyNoMoreInteractions(mock1, mock2 ...
            sb.deleteCharAt(sb.length() - 1);
            sb.append(")");
            rewriteTemplate(sb.toString(), mocks, nextStatementCoordinates);
            if (!this.rewriteFailed) {
                setNextStatementCoordinates(++numStatementsAdded);
                visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "verifyNoMoreInteractions", false);
            }
        }
    }

    private void rewriteInOrderVerify(ArrayList<Object> mocks) {
        if (!mocks.isEmpty()) {
            StringBuilder sb = new StringBuilder(VERIFY_IN_ORDER_TEMPLATE_PREFIX);
            mocks.forEach(mock -> sb.append(ANY_TEMPLATE_FIELD).append(", ")); // InOrder inOrder = inOrder(mock1, mock2 ...
            sb.delete(sb.length() - 2, sb.length());
            sb.append(");");
            rewriteTemplate(sb.toString(), mocks, nextStatementCoordinates);
            if (!this.rewriteFailed) {
                setNextStatementCoordinates(++numStatementsAdded);
                visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "inOrder", false);
                visitor.maybeAddImport(IN_ORDER_IMPORT_FQN);
            }
        }
    }

    private void setNextStatementCoordinates(int numStatementsAdded) {
        if (numStatementsAdded <= 0 && bodyStatementIndex == 0) {
            nextStatementCoordinates = methodBody.getCoordinates().firstStatement();
            return;
        }

        // the next statement coordinates are directly after the most recently written statement, calculated by
        // subtracting the removed jmockit block
        int lastStatementIdx = bodyStatementIndex + numStatementsAdded - 1;
        if (lastStatementIdx >= this.methodBody.getStatements().size()) {
            this.rewriteFailed = true;
            return;
        }

        this.nextStatementCoordinates = this.methodBody.getStatements().get(lastStatementIdx).getCoordinates().after();
    }

    private void rewriteTemplate(String template, List<Object> templateParams, JavaCoordinates
            rewriteCoords) {
        int numStatementsBefore = methodBody.getStatements().size();
        methodBody = JavaTemplate.builder(template)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                .staticImports("org.mockito.Mockito.*")
                .imports(IN_ORDER_IMPORT_FQN)
                .build()
                .apply(
                        new Cursor(visitor.getCursor(), methodBody),
                        rewriteCoords,
                        templateParams.toArray()
                );
        this.rewriteFailed = methodBody.getStatements().size() <= numStatementsBefore;
    }

    private @Nullable String getWhenTemplate(List<Expression> results, boolean lenient) {
        boolean buildingResults = false;
        StringBuilder templateBuilder = new StringBuilder();
        if (lenient) {
            templateBuilder.append(LENIENT_TEMPLATE_PREFIX);
        }
        templateBuilder.append(WHEN_TEMPLATE_PREFIX);
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

    private static void appendToTemplate(StringBuilder templateBuilder, boolean buildingResults, String templatePrefix,
                                         String templateField) {
        if (!buildingResults) {
            templateBuilder.append(templatePrefix);
        } else {
            templateBuilder.append(", ");
        }
        templateBuilder.append(templateField);
    }

    private String getVerifyTemplate(List<Expression> arguments, String verificationMode, List<Object> templateParams) {
        StringBuilder templateBuilder = new StringBuilder();
        if (isVerificationsInOrder()) {
            templateBuilder.append("inOrder.");
        }
        templateBuilder.append(VERIFY_TEMPLATE_PREFIX); // eg verify(object
        if (!verificationMode.isEmpty()) {
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

    private static @Nullable MockInvocationResults buildMockInvocationResults(List<Statement> expectationStatements) {
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

    private static @Nullable String getVariableNameFromAssignment(J.Assignment assignment) {
        if (assignment.getVariable() instanceof J.Identifier) {
            return ((J.Identifier) assignment.getVariable()).getSimpleName();
        } else if (assignment.getVariable() instanceof J.FieldAccess) {
            J.FieldAccess fieldAccess = (J.FieldAccess) assignment.getVariable();
            if (fieldAccess.getTarget() instanceof J.Identifier) {
                return fieldAccess.getSimpleName();
            }
        }
        return null;
    }

    private static @Nullable String getPrimitiveTemplateField(JavaType.Primitive primitiveType) {
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

    @Data
    private static class MockInvocationResults {
        @Setter(AccessLevel.NONE)
        private final List<Expression> results = new ArrayList<>();
        private Expression times;
        private Expression minTimes;
        private Expression maxTimes;

        private void addResult(Expression result) {
            results.add(result);
        }

        private boolean hasAnyTimes() {
            return times != null || minTimes != null || maxTimes != null;
        }
    }
}
