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
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        private static String getObjectTemplate(String fqn) {
            return "when(#{any()}).thenReturn(#{any(" + fqn + ")});";
        }
        private String getVerifyTemplate(ExecutionContext ctx, String fqn, List<Expression> arguments) {
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

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }
            SetupStatementsRewriter ssr = new SetupStatementsRewriter(this, md.getBody());
            J.Block newBody = ssr.rewrite();

            // the LST element that is being updated when applying a java template
            Object cursorLocation = newBody;
            List<Statement> statements = newBody.getStatements();

            try {
                // iterate over each statement in the method body, find Expectations blocks and rewrite them
                for (int bodyStatementIndex = 0; bodyStatementIndex < statements.size(); bodyStatementIndex++) {
                    if (!JMockitUtils.isExpectationsNewClassStatement(statements.get(bodyStatementIndex))) {
                        continue;
                    }
                    // we have a valid Expectations block, update imports and rewrite with Mockito statements
                    maybeRemoveImport("mockit.Expectations");

                    // the first coordinates are the coordinates of the Expectations block, replacing it
                    J.NewClass nc = (J.NewClass) statements.get(bodyStatementIndex);
                    assert nc.getBody() != null;
                    J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);

                    // then rewrite the argument matchers
                    ArgumentMatchersRewriter amr = new ArgumentMatchersRewriter(this, expectationsBlock, ctx);
                    expectationsBlock = amr.rewrite();

                    // iterate over the expectations statements and rebuild the method body
                    List<Object> templateParams = new ArrayList<>();
                    JavaCoordinates coordinates = nc.getCoordinates().replace();
                    int mockitoStatementIndex = 0;
                    for (Statement expectationStatement : expectationsBlock.getStatements()) {
                        if (expectationStatement instanceof J.MethodInvocation && !templateParams.isEmpty()) {
                            // apply template to build new method body
                            newBody = rewriteMethodBody(ctx, templateParams, cursorLocation, coordinates);

                            if (templateParams.size() == 1 && mockitoStatementIndex > 0) {
                                // method being removed, so decrement the statement index
                                mockitoStatementIndex -= 1;
                            }

                            // next statement coordinates are immediately after the statement just added
                            int newStatementIndex = bodyStatementIndex + mockitoStatementIndex;
                            coordinates = newBody.getStatements().get(newStatementIndex).getCoordinates().after();

                            // cursor location is now the new body
                            cursorLocation = newBody;

                            // reset template params for next expectation
                            templateParams = new ArrayList<>();
                            mockitoStatementIndex += 1;
                        }
                        templateParams.add(expectationStatement);
                    }

                    // handle the last statement
                    if (!templateParams.isEmpty()) {
                        newBody = rewriteMethodBody(ctx, templateParams, cursorLocation, coordinates);
                    }
                }
            } catch (Exception e) {
                System.err.println("DEBUG: Exception rewriting method body: " + e.getMessage() + "\n\n");
                e.printStackTrace();
//                System.out.println(TreeVisitingPrinter.printTree(getCursor()));
                // if anything goes wrong, just return the original method declaration
                return md;
            }

            return md.withBody(newBody);
        }

        private J.Block rewriteMethodBody(ExecutionContext ctx, List<Object> templateParams, Object cursorLocation,
                                          JavaCoordinates coordinates) {
            Expression result = null, times = null;
            J.Assignment assignment;
            System.out.println("DEBUG: templateParams before: " + templateParams);

            // TODO: refactor duplicate code
            if (templateParams.size() > 3) {
                throw new IllegalStateException("Unexpected number of template params: " + templateParams.size());
            }
            if (templateParams.size() == 2) {
                assignment = (J.Assignment) templateParams.get(1);
                if (!(assignment.getVariable() instanceof J.Identifier)) {
                    throw new IllegalStateException("Unexpected assignment variable type: " + assignment.getVariable());
                }
                J.Identifier identifier = (J.Identifier) assignment.getVariable();
                if (identifier.getSimpleName().equals("result")) {
                    result = assignment.getAssignment();
                    templateParams.set(1, result);
                } else if (identifier.getSimpleName().equals("times")) {
                    times = assignment.getAssignment();
                    templateParams.remove(1);
                }
            } else if (templateParams.size() == 3) {
                int expressionIndex = 1;
                J.Assignment firstAssignment = (J.Assignment) templateParams.get(1);
                if (!(firstAssignment.getVariable() instanceof J.Identifier)) {
                    throw new IllegalStateException("Unexpected assignment variable type: " +
                            firstAssignment.getVariable());
                }
                J.Identifier identifier = (J.Identifier) firstAssignment.getVariable();
                if (identifier.getSimpleName().equals("result")) {
                    result = firstAssignment.getAssignment();
                    templateParams.set(expressionIndex, result);
                    expressionIndex = 2;
                } else if (identifier.getSimpleName().equals("times")) {
                    times = firstAssignment.getAssignment();
                    templateParams.remove(expressionIndex);
                }

                J.Assignment secondAssignment = (J.Assignment) templateParams.get(expressionIndex);
                identifier = (J.Identifier) secondAssignment.getVariable();
                if (identifier.getSimpleName().equals("result")) {
                    result = secondAssignment.getAssignment();
                    templateParams.set(expressionIndex, result);
                } else if (identifier.getSimpleName().equals("times")) {
                    times = secondAssignment.getAssignment();
                    templateParams.remove(expressionIndex);
                }
            }
            J.MethodInvocation invocation = (J.MethodInvocation) templateParams.get(0);
            Expression select = invocation.getSelect();
            if (select == null || select.getType() == null) {
                throw new IllegalStateException("Unexpected invocation select type: " + select);
            }
            String fqn = ""; // default to empty string to support method invocations
            if (select instanceof J.Identifier) {
                fqn = ((JavaType.FullyQualified) Objects.requireNonNull(select.getType())).getFullyQualifiedName();
            }
            if (templateParams.size() == 1) {
                // no result or verification, just remove the statement
                templateParams = new ArrayList<>();
            }
            System.out.println("DEBUG: templateParams after: " + templateParams);
            String template = "";
            if (result != null) {
                maybeAddImport("org.mockito.Mockito", "when");
                template = getMockitoStatementTemplate(result);
            }
            System.out.println("DEBUG: template: " + template);
            J.Block newBody = JavaTemplate.builder(template)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports("org.mockito.Mockito.*")
                    .build()
                    .apply(
                            new Cursor(getCursor(), cursorLocation),
                            coordinates,
                            templateParams.toArray()
                    );
            cursorLocation = newBody;
            coordinates = newBody.getCoordinates().lastStatement();

            if (times != null) {
                maybeAddImport("org.mockito.Mockito", "verify");
                maybeAddImport("org.mockito.Mockito", "times");

                String verifyTemplate = getVerifyTemplate(ctx, fqn, invocation.getArguments());
                templateParams = new ArrayList<>();
                templateParams.add(select);
                templateParams.add(times);
                templateParams.add(invocation.getName().getSimpleName());

                newBody = JavaTemplate.builder(verifyTemplate)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .staticImports("org.mockito.Mockito.*")
                        .imports(fqn)
                        .build()
                        .apply(
                                new Cursor(getCursor(), cursorLocation),
                                coordinates,
                                templateParams.toArray()
                        );
            }

            return newBody;
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
    }

    private static class InjectMocksToSpyVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final J.Identifier spy;

        private InjectMocksToSpyVisitor(J.Identifier spy) {
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
                maybeRemoveImport("org.mockito.InjectMocks");

                List<J.Annotation> newAnnotations = new ArrayList<>(mv.getLeadingAnnotations().size());
                for (J.Annotation annotation : mv.getLeadingAnnotations()) {
                    if (!TypeUtils.isAssignableTo("org.mockito.InjectMocks", annotation.getType())) {
                        newAnnotations.add(annotation);
                        continue;
                    }
                    String template = "@Spy";
                    newAnnotations.add(JavaTemplate.builder(template)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                            .imports("org.mockito.Spy")
                            .build()
                            .apply(new Cursor(getCursor(), annotation), annotation.getCoordinates().replace()));
                }
                mv = mv.withLeadingAnnotations(newAnnotations);
            }
            return mv;
        }
    }
}
