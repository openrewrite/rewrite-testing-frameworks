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
package org.openrewrite.java.testing.jmockit;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.marker.Markers;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.testing.jmockit.JMockitBlockType.*;
import static org.openrewrite.java.testing.jmockit.JMockitUtils.MOCKITO_ALL_IMPORT;
import static org.openrewrite.java.testing.jmockit.JMockitUtils.getJavaParser;

class JMockitBlockRewriter {

    private static final String WHEN_TEMPLATE_PREFIX = "when(#{any()}).";
    private static final String VERIFY_NO_INTERACTIONS_TEMPLATE_PREFIX = "verifyNoMoreInteractions(";
    private static final String VERIFY_IN_ORDER_TEMPLATE_PREFIX_1 = "InOrder inOrder";
    private static final String VERIFY_IN_ORDER_TEMPLATE_PREFIX_2 = " = inOrder(";
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
    private final int verificationsInOrderIdx;
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

    // Track setup statements that need to be preserved and wrapped in a block
    private final List<Statement> setupStatementsBeforeFirstMock = new ArrayList<>();
    private boolean hasSetupStatements = false;

    JMockitBlockRewriter(JavaVisitor<ExecutionContext> visitor, ExecutionContext ctx, J.Block methodBody,
                         J.NewClass newExpectations, int bodyStatementIndex, JMockitBlockType blockType, int verificationsInOrderIdx) {
        this.visitor = visitor;
        this.ctx = ctx;
        this.methodBody = methodBody;
        this.newExpectations = newExpectations;
        this.bodyStatementIndex = bodyStatementIndex;
        this.blockType = blockType;
        this.verificationsInOrderIdx = verificationsInOrderIdx;
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
            // Track setup statements (variable declarations) that appear before mock invocations
            if (isSetupStatement(jmockitBlockStatement)) {
                hasSetupStatements = true;
                if (methodInvocationIdx == -1) {
                    // Setup statement before the first mock invocation
                    setupStatementsBeforeFirstMock.add(jmockitBlockStatement);
                    continue;
                }
                // Setup statement after a mock invocation - will be handled by the group
            }

            if (jmockitBlockStatement instanceof J.MethodInvocation) {
                J.MethodInvocation invocation = (J.MethodInvocation) jmockitBlockStatement;
                Expression select = invocation.getSelect();
                if (select instanceof J.Identifier) {
                    J.Identifier mockObj = (J.Identifier) select;
                    // ensure it's not a returns statement, we add that later to related statements
                    if (!"returns".equals(invocation.getName().getSimpleName())) {
                        methodInvocationIdx++;
                        methodInvocationsToRewrite.add(new ArrayList<>());
                    }
                    if ((isFullVerifications() || isVerificationsInOrder()) &&
                            uniqueMocks.stream().noneMatch(mock -> mock.getSimpleName().equals(mockObj.getSimpleName()))) {
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

        // Output setup statements that come before the first mock invocation
        outputSetupStatements(setupStatementsBeforeFirstMock);

        List<Object> mocks = new ArrayList<>(uniqueMocks);
        if (isVerificationsInOrder()) {
            rewriteInOrderVerify(mocks);
        }

        // now rewrite
        methodInvocationsToRewrite.forEach(this::rewriteMethodInvocation);

        if (isFullVerifications()) {
            rewriteFullVerify(mocks);
        }

        // If there were setup statements, wrap the generated statements in a block
        // to avoid variable name conflicts with the rest of the method
        if (hasSetupStatements) {
            wrapStatementsInBlock();
        }

        return methodBody;
    }

    private void wrapStatementsInBlock() {
        List<Statement> statements = methodBody.getStatements();
        if (numStatementsAdded <= 0) {
            return;
        }

        // Get the statements that were added (from bodyStatementIndex to bodyStatementIndex + numStatementsAdded - 1)
        int startIdx = bodyStatementIndex;
        int endIdx = bodyStatementIndex + numStatementsAdded;

        if (startIdx < 0 || endIdx > statements.size()) {
            return;
        }

        // Extract the statements to wrap
        List<Statement> statementsToWrap = new ArrayList<>();
        for (int i = startIdx; i < endIdx; i++) {
            statementsToWrap.add(statements.get(i));
        }

        // Create a new block containing these statements
        J.Block wrapperBlock = new J.Block(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                JRightPadded.build(false),
                statementsToWrap.stream()
                        .map(JRightPadded::build)
                        .collect(toList()),
                Space.EMPTY
        );

        // Replace the statements with the block
        List<Statement> newStatements = new ArrayList<>();
        for (int i = 0; i < startIdx; i++) {
            newStatements.add(statements.get(i));
        }
        newStatements.add(wrapperBlock);
        for (int i = endIdx; i < statements.size(); i++) {
            newStatements.add(statements.get(i));
        }

        methodBody = methodBody.withStatements(newStatements);
    }

    private boolean isFullVerifications() {
        return this.blockType == FullVerifications;
    }

    private boolean isVerificationsInOrder() {
        return this.blockType == VerificationsInOrder;
    }

    private static boolean isSetupStatement(Statement statement) {
        // Variable declarations are setup statements
        return statement instanceof J.VariableDeclarations;
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
            // Output setup statements that follow this mock invocation
            outputSetupStatements(mockInvocationResults.getSetupStatements());
            return;
        }
        if (mockInvocationResults.getTimes() != null) {
            // Don't add times(1) since that's the default
            if (!J.Literal.isLiteralValue(mockInvocationResults.getTimes(), 1)) {
                rewriteVerify(invocation, mockInvocationResults.getTimes(), "times");
            } else {
                rewriteVerify(invocation, null, "");
            }
        }
        if (mockInvocationResults.getMinTimes() != null) {
            rewriteVerify(invocation, mockInvocationResults.getMinTimes(), "atLeast");
        }
        if (mockInvocationResults.getMaxTimes() != null) {
            rewriteVerify(invocation, mockInvocationResults.getMaxTimes(), "atMost");
        }

        // Output setup statements that follow this mock invocation
        outputSetupStatements(mockInvocationResults.getSetupStatements());
    }

    private void outputSetupStatements(List<Statement> setupStatements) {
        for (Statement setupStatement : setupStatements) {
            insertStatementAtCurrentPosition(setupStatement);
        }
    }

    private void removeBlock() {
        List<Statement> statements = new ArrayList<>(methodBody.getStatements());
        if (bodyStatementIndex >= 0 && bodyStatementIndex < statements.size()) {
            statements.remove(bodyStatementIndex);
            methodBody = methodBody.withStatements(statements);
        }
        setNextStatementCoordinates(0);
    }

    private void insertStatementAtCurrentPosition(Statement statement) {
        List<Statement> statements = new ArrayList<>(methodBody.getStatements());
        int insertIdx = bodyStatementIndex + numStatementsAdded;
        if (insertIdx < 0) {
            insertIdx = 0;
        }
        if (insertIdx > statements.size()) {
            insertIdx = statements.size();
        }
        statements.add(insertIdx, statement);
        methodBody = methodBody.withStatements(statements);
        setNextStatementCoordinates(++numStatementsAdded);
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

        // Build a template for verify() using placeholder types to avoid classpath dependencies
        // We'll template something like: verify("", times(1)).toString();
        // Then replace "" with the mock, 1 with the actual times, and toString() with the actual method invocation
        StringBuilder templateBuilder = new StringBuilder();

        if (isVerificationsInOrder()) {
            templateBuilder.append("inOrder");
            if (this.verificationsInOrderIdx > 0) {
                templateBuilder.append(this.verificationsInOrderIdx);
            }
            templateBuilder.append(".");
        }

        templateBuilder.append("verify(\"\"");
        if (!verificationMode.isEmpty()) {
            templateBuilder.append(", ").append(verificationMode).append("(1)");
        }
        templateBuilder.append(").toString();");

        JavaCoordinates verifyCoordinates;
        if (this.blockType.isVerifications()) {
            verifyCoordinates = nextStatementCoordinates;
        } else {
            verifyCoordinates = methodBody.getCoordinates().lastStatement();
        }

        // Apply template to add the verify wrapper
        int numStatementsBefore = methodBody.getStatements().size();
        methodBody = JavaTemplate.builder(templateBuilder.toString())
                .javaParser(getJavaParser(ctx))
                .staticImports(MOCKITO_ALL_IMPORT)
                .imports(IN_ORDER_IMPORT_FQN)
                .build()
                .apply(new Cursor(visitor.getCursor(), methodBody), verifyCoordinates);

        if (methodBody.getStatements().size() <= numStatementsBefore) {
            this.rewriteFailed = true;
            return;
        }

        // Find the newly added statement
        int newStatementIdx = this.blockType.isVerifications() ?
                bodyStatementIndex + numStatementsAdded :
                methodBody.getStatements().size() - 1;

        Statement newStatement = methodBody.getStatements().get(newStatementIdx);
        if (!(newStatement instanceof J.MethodInvocation)) {
            this.rewriteFailed = true;
            return;
        }

        // newStatement is something like: verify("").toString() or verify("", times(1)).toString()
        // We need to replace "" with the actual mock, 1 with the actual times, and toString() with the actual method invocation
        J.MethodInvocation toStringCall = (J.MethodInvocation) newStatement;

        // Get the verify() call
        Expression selectExpr = toStringCall.getSelect();
        if (!(selectExpr instanceof J.MethodInvocation)) {
            this.rewriteFailed = true;
            return;
        }

        J.MethodInvocation verifyCall = (J.MethodInvocation) selectExpr;

        // Replace the placeholders in verify()'s arguments
        // First argument is always the mock ("")
        // Second argument (if present) is times(1)
        List<Expression> verifyArgs = new ArrayList<>(verifyCall.getArguments());
        if (verifyArgs.isEmpty()) {
            this.rewriteFailed = true;
            return;
        }

        // Replace the mock placeholder
        verifyArgs.set(0, invocation.getSelect());

        // If there's a second argument, it's the times() call - replace its argument
        if (verifyArgs.size() > 1 && verifyArgs.get(1) instanceof J.MethodInvocation) {
            J.MethodInvocation timesCall = (J.MethodInvocation) verifyArgs.get(1);
            if (times != null) {
                // Replace the placeholder argument with the actual times value
                List<Expression> timesArgs = new ArrayList<>();
                timesArgs.add(times);
                timesCall = timesCall.withArguments(timesArgs);
                verifyArgs.set(1, timesCall);
            }
        }

        verifyCall = verifyCall.withArguments(verifyArgs);

        // Replace toString() with the actual method invocation
        J.MethodInvocation wrappedInvocation = invocation.withSelect(verifyCall);

        // Update the statement in the method body
        List<Statement> statements = new ArrayList<>(methodBody.getStatements());
        statements.set(newStatementIdx, wrappedInvocation);
        methodBody = methodBody.withStatements(statements);

        if (this.blockType.isVerifications()) {
            setNextStatementCoordinates(++numStatementsAdded);
        }

        visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "verify", false);
        if (!verificationMode.isEmpty()) {
            visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, verificationMode);
        }
    }

    private void rewriteFullVerify(List<Object> mocks) {
        if (rewriteMultipleMocks(mocks, VERIFY_NO_INTERACTIONS_TEMPLATE_PREFIX)) { // verifyNoMoreInteractions(mock1, mock2 ...
            visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "verifyNoMoreInteractions", false);
        }
    }

    private void rewriteInOrderVerify(List<Object> mocks) {
        StringBuilder sb = new StringBuilder(VERIFY_IN_ORDER_TEMPLATE_PREFIX_1); // InOrder inOrder
        if (verificationsInOrderIdx > 0) {
            sb.append(verificationsInOrderIdx); // InOrder inOrder1
        }
        sb.append(VERIFY_IN_ORDER_TEMPLATE_PREFIX_2); // InOrder inOrder1 = inOrder(
        if (rewriteMultipleMocks(mocks, sb.toString())) { // InOrder inOrder = inOrder(mock1, mock2 ..)
            visitor.maybeAddImport(MOCKITO_IMPORT_FQN_PREFX, "inOrder", false);
            visitor.maybeAddImport(IN_ORDER_IMPORT_FQN);
        }
    }

    private boolean rewriteMultipleMocks(List<Object> mocks, String template) {
        if (mocks.isEmpty()) {
            return false;
        }
        StringBuilder sb = new StringBuilder(template);
        mocks.forEach(mock -> sb.append(ANY_TEMPLATE_FIELD).append(", "));
        sb.delete(sb.length() - 2, sb.length());
        sb.append(");");
        rewriteTemplate(sb.toString(), mocks, nextStatementCoordinates);
        if (!this.rewriteFailed) {
            setNextStatementCoordinates(++numStatementsAdded);
        }
        return !this.rewriteFailed;
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
                .javaParser(getJavaParser(ctx))
                .staticImports(MOCKITO_ALL_IMPORT)
                .imports(IN_ORDER_IMPORT_FQN)
                .build()
                .apply(new Cursor(visitor.getCursor(), methodBody), rewriteCoords, templateParams.toArray());
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

    private static @Nullable MockInvocationResults buildMockInvocationResults
            (List<Statement> expectationStatements) {
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
            // Skip setup statements (variable declarations) - they're tracked separately
            if (isSetupStatement(expectationStatement)) {
                resultWrapper.addSetupStatement(expectationStatement);
                continue;
            }
            if (!(expectationStatement instanceof J.Assignment)) {
                // unhandled statement type, skip it
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
        }
        if (assignment.getVariable() instanceof J.FieldAccess) {
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
        @Setter(AccessLevel.NONE)
        private final List<Statement> setupStatements = new ArrayList<>();
        private @Nullable Expression times;
        private @Nullable Expression minTimes;
        private @Nullable Expression maxTimes;

        private void addResult(Expression result) {
            results.add(result);
        }

        private void addSetupStatement(Statement statement) {
            setupStatements.add(statement);
        }

        private boolean hasAnyTimes() {
            return times != null || minTimes != null || maxTimes != null;
        }
    }
}
