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
import java.util.Comparator;
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

        private static final String PRIMITIVE_RESULT_TEMPLATE = "when(#{any()}).thenReturn(#{});";
        private static final String THROWABLE_RESULT_TEMPLATE = "when(#{any()}).thenThrow(#{any()});";
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
                    for (Expression argument : nc.getArguments()) {
                        if (argument instanceof J.Identifier) {
                            // add @Spy annotation
                            doAfterVisit(new AddSpyAnnotationVisitor((J.Identifier) argument));
                        }
                    }
                    J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);

                    // rewrite the argument matchers
                    ArgumentMatchersRewriter amr = new ArgumentMatchersRewriter(this, expectationsBlock, ctx);
                    expectationsBlock = amr.rewrite();

                    // iterate over the expectations statements and rebuild the method body
                    List<Statement> expectationStatements = new ArrayList<>();
                    nextStatementCoordinates = nc.getCoordinates().replace();
                    mockitoStatementIndex = 0;
                    for (Statement expectationStatement : expectationsBlock.getStatements()) {
                        if (expectationStatement instanceof J.MethodInvocation && !expectationStatements.isEmpty()) {
                            // apply template to build new method body
                            methodBody = rewriteMethodBody(ctx, expectationStatements, methodBody, bodyStatementIndex);

                            // reset statements for next expectation
                            expectationStatements = new ArrayList<>();
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

        private J.Block rewriteMethodBody(ExecutionContext ctx, List<Statement> expectationStatements, J.Block methodBody, int bodyStatementIndex) {
            J.MethodInvocation invocation = (J.MethodInvocation) expectationStatements.get(0);
            final List<MockInvocationResult> mockInvocationResults = buildMockInvocationResults(expectationStatements);

            if (mockInvocationResults.isEmpty() && nextStatementCoordinates.isReplacement()) {
                // remove mock method invocations without expectations or verifications
                methodBody = removeExpectationsStatement(methodBody, bodyStatementIndex);
                return methodBody;
            }

            for (MockInvocationResult mockInvocationResult : mockInvocationResults) {
                if (mockInvocationResult.getResult() != null) {
                    methodBody = rewriteExpectationResult(ctx, methodBody, bodyStatementIndex, mockInvocationResult, invocation);
                } else if (nextStatementCoordinates.isReplacement()) {
                    methodBody = removeExpectationsStatement(methodBody, bodyStatementIndex);
                }
                if (mockInvocationResult.getTimes() != null) {
                    String fqn = getInvocationSelectFullyQualifiedClassName(invocation);
                    methodBody = writeMethodVerification(ctx, methodBody, fqn, invocation, mockInvocationResult.getTimes());
                }
            }

            return methodBody;
        }

        private J.Block rewriteExpectationResult(ExecutionContext ctx, J.Block methodBody, int bodyStatementIndex, MockInvocationResult mockInvocationResult, J.MethodInvocation invocation) {
            maybeAddImport("org.mockito.Mockito", "when");
            String template = getMockitoStatementTemplate(mockInvocationResult.getResult());
            Object[] templateParams = new Object[] {invocation, mockInvocationResult.getResult() };

            methodBody = JavaTemplate.builder(template)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports("org.mockito.Mockito.*")
                    .build()
                    .apply(
                            new Cursor(getCursor(), methodBody),
                            nextStatementCoordinates,
                            templateParams
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
            Object[] templateParams = new Object[] { invocation.getSelect(), times, invocation.getName().getSimpleName() };
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
            return "when(#{any()}).thenReturn(#{any(" + fqn + ")});";
        }

        private static String getMockitoStatementTemplate(Expression result) {
            String template;
            JavaType resultType = result.getType();
            if (resultType instanceof JavaType.Primitive) {
                template = PRIMITIVE_RESULT_TEMPLATE;
            } else if (resultType instanceof JavaType.Class) {
                template = TypeUtils.isAssignableTo(Throwable.class.getName(), resultType)
                        ? THROWABLE_RESULT_TEMPLATE
                        : getObjectTemplate(((JavaType.Class) resultType).getFullyQualifiedName());
            } else if (resultType instanceof JavaType.Parameterized) {
                template = getObjectTemplate(((JavaType.Parameterized) resultType).getType().getFullyQualifiedName());
            } else {
                throw new IllegalStateException("Unexpected expression type for template: " + result.getType());
            }
            return template;
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

        private static List<MockInvocationResult> buildMockInvocationResults(List<Statement> expectationStatements) {
            List<MockInvocationResult> mockInvocationResults = new ArrayList<>();
            boolean hasResult = false, hasTimes = false;
            MockInvocationResult newResult = new MockInvocationResult();
            for (int i = 1; i < expectationStatements.size(); i++) {
                J.Assignment assignment = (J.Assignment) expectationStatements.get(i);
                if (!(assignment.getVariable() instanceof J.Identifier)) {
                    throw new IllegalStateException("Unexpected assignment variable type: " + assignment.getVariable());
                }
                J.Identifier identifier = (J.Identifier) assignment.getVariable();
                boolean isResult = identifier.getSimpleName().equals("result");
                boolean isTimes = identifier.getSimpleName().equals("times");
                if (hasResult && isResult) {
                    mockInvocationResults.add(newResult);
                    hasResult = false;
                    hasTimes = false;
                    newResult = new MockInvocationResult();
                    newResult.setResult(assignment.getAssignment());
                } else if (isResult) {
                    newResult.setResult(assignment.getAssignment());
                    hasResult = true;
                } else if (isTimes) {
                    newResult.setTimes(assignment.getAssignment());
                    hasTimes = true;
                }
            }
            if (hasResult || hasTimes) {
                mockInvocationResults.add(newResult);
            }
            return mockInvocationResults;
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

        private static class MockInvocationResult {
            private Expression result;
            private Expression times;

            private Expression getResult() {
                return result;
            }
            private Expression getTimes() {
                return times;
            }
            private void setResult(Expression result) {
                this.result = result;
            }
            private void setTimes(Expression times) {
                this.times = times;
            }
        }
    }

    private static class AddSpyAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final J.Identifier spy;

        private AddSpyAnnotationVisitor(J.Identifier spy) {
            this.spy = spy;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations mv = super.visitVariableDeclarations(multiVariable, ctx);
            for (J.VariableDeclarations.NamedVariable variable : mv.getVariables()) {
                if (!variable.getName().equals(spy)) {
                    continue;
                }
                maybeAddImport("org.mockito.Spy");
                String template = "@Spy";
                List<J.Annotation> newAnnotations = new ArrayList<>(mv.getLeadingAnnotations());
                newAnnotations.add(JavaTemplate.builder(template)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .imports("org.mockito.Spy")
                        .build()
                        .apply(getCursor(), mv.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))));
                mv = mv.withLeadingAnnotations(newAnnotations);
            }
            return mv;
        }
    }
}
