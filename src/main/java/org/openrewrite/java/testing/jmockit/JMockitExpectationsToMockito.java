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

import java.util.*;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

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
        private static String getVoidResultTemplate(String fqn, List<Expression> arguments) {
            if (arguments.isEmpty()) {
                return "doNothing().when(#{any(" + fqn + ")}).#{}();";
            }
            StringBuilder templateBuilder = new StringBuilder("doNothing().when(#{any(" + fqn + ")}).#{}(");
            boolean hasArguments = false;
            for (Expression argument : arguments) {
                if (argument instanceof J.Empty) {
                    continue;
                }
                hasArguments = true;
                templateBuilder.append(argument);
                templateBuilder.append(", ");
            }
            if (hasArguments) {
                templateBuilder.delete(templateBuilder.length() - 2, templateBuilder.length());
            }
            templateBuilder.append(");");
            return templateBuilder.toString();
        }
        private static String getObjectTemplate(String fqn) {
            return "when(#{any()}).thenReturn(#{any(" + fqn + ")});";
        }
        private String getVerifyTemplate(ExecutionContext ctx, String fqn, List<Expression> arguments) {
            if (arguments.isEmpty()) {
                return "verify(#{any(" + fqn + ")}, times(#{any(int)})).#{}();";
            }
            StringBuilder templateBuilder = new StringBuilder("verify(#{any(" + fqn + ")}, times(#{any(int)})).#{}(");
            for (Expression argument : arguments) {
                templateBuilder.append(rewriteMethodArgument(ctx, argument));
                templateBuilder.append(", ");
            }
            templateBuilder.delete(templateBuilder.length() - 2, templateBuilder.length());
            templateBuilder.append(");");
            return templateBuilder.toString();
        }
        private static final Set<String> JMOCKIT_ARGUMENT_MATCHERS = new HashSet<>();
        static {
            JMOCKIT_ARGUMENT_MATCHERS.add("anyString");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyInt");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyLong");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyDouble");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyFloat");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyBoolean");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyByte");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyChar");
            JMOCKIT_ARGUMENT_MATCHERS.add("anyShort");
            JMOCKIT_ARGUMENT_MATCHERS.add("any");
        }
        private static final Map<String, String> MOCKITO_COLLECTION_MATCHERS = new HashMap<>();
        static {
            MOCKITO_COLLECTION_MATCHERS.put("java.util.List", "anyList");
            MOCKITO_COLLECTION_MATCHERS.put("java.util.Set", "anySet");
            MOCKITO_COLLECTION_MATCHERS.put("java.util.Collection", "anyCollection");
            MOCKITO_COLLECTION_MATCHERS.put("java.util.Iterable", "anyIterable");
            MOCKITO_COLLECTION_MATCHERS.put("java.util.Map", "anyMap");
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }
            // the LST element that is being updated when applying a java template
            Object cursorLocation = md.getBody();
            J.Block newBody = md.getBody();
            List<Statement> statements = md.getBody().getStatements();

            try {
                // iterate over each statement in the method body, find Expectations blocks and rewrite them
                for (int bodyStatementIndex = 0; bodyStatementIndex < statements.size(); bodyStatementIndex++) {
                    Statement s = statements.get(bodyStatementIndex);
                    if (!(s instanceof J.NewClass)) {
                        continue;
                    }
                    J.NewClass nc = (J.NewClass) s;
                    if (!(nc.getClazz() instanceof J.Identifier)) {
                        continue;
                    }
                    J.Identifier clazz = (J.Identifier) nc.getClazz();
                    if (!TypeUtils.isAssignableTo("mockit.Expectations", clazz.getType())) {
                        continue;
                    }
                    // empty Expectations block is considered invalid
                    assert nc.getBody() != null
                            && !nc.getBody().getStatements().isEmpty() : "Expectations block is empty";
                    // Expectations block should be composed of a block within another block
                    assert nc.getBody().getStatements().size() == 1 : "Expectations block is malformed";

                    // we have a valid Expectations block, update imports and rewrite with Mockito statements
                    maybeRemoveImport("mockit.Expectations");

                    // the first coordinates are the coordinates of the Expectations block, replacing it
                    JavaCoordinates coordinates = nc.getCoordinates().replace();
                    J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);
                    List<Object> templateParams = new ArrayList<>();

                    // iterate over the expectations statements and rebuild the method body
                    int mockitoStatementIndex = 0;
                    for (Statement expectationStatement : expectationsBlock.getStatements()) {
                        if (expectationStatement instanceof J.MethodInvocation && !templateParams.isEmpty()) {
                            // apply template to build new method body
                            newBody = rewriteMethodBody(ctx, templateParams, cursorLocation, coordinates);

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
                // if anything goes wrong, just return the original method declaration
                throw e;
            }

            return md.withBody(newBody);
        }

        private J.Block rewriteMethodBody(ExecutionContext ctx, List<Object> templateParams, Object cursorLocation,
                                          JavaCoordinates coordinates) {
            Expression result = null, times = null;
            J.Assignment assignment;

            // TODO: refactor duplicate code
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
                } else {
                    // ignore other assignments
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
                } else {
                    // ignore other assignments
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
                } else {
                    // ignore other assignments
                    templateParams.remove(expressionIndex);
                }
            } else if (templateParams.size() > 3) {
                throw new IllegalStateException("Unexpected number of template params: " + templateParams.size());
            }
            J.MethodInvocation invocation = (J.MethodInvocation) templateParams.get(0);
            J.Identifier select = (J.Identifier) invocation.getSelect();
            if (select == null || select.getType() == null) {
                throw new IllegalStateException("Unexpected invocation select type: " + select);
            }
            String fqn = ((JavaType.FullyQualified) select.getType()).getFullyQualifiedName();
            String methodName = "when";
            List<Expression> mockArguments = new ArrayList<>();
            if (templateParams.size() == 1) {
                methodName = "doNothing";
                templateParams.set(0, select);
                templateParams.add(invocation.getName().getSimpleName());
                mockArguments = rewriteMethodArgumentMatchers(ctx, invocation.getArguments());
            } else {
                templateParams.set(0,
                        invocation.withArguments(rewriteMethodArgumentMatchers(ctx, invocation.getArguments())));
            }
            maybeAddImport("org.mockito.Mockito", methodName);

            J.Block newBody = JavaTemplate.builder(getMockitoStatementTemplate(result, fqn, mockArguments))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports("org.mockito.Mockito.*")
                    .build()
                    .apply(
                            new Cursor(getCursor(), cursorLocation),
                            coordinates,
                            templateParams.toArray()
                    );

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
                                new Cursor(getCursor(), newBody),
                                newBody.getCoordinates().lastStatement(),
                                templateParams.toArray()
                        );
            }

            return newBody;
        }

        private List<Expression> rewriteMethodArgumentMatchers(ExecutionContext ctx, List<Expression> arguments) {
            List<Expression> newArguments = new ArrayList<>(arguments.size());
            for (Expression methodArgument : arguments) {
                newArguments.add(rewriteMethodArgument(ctx, methodArgument));
            }
            return newArguments;
        }

        private Expression rewriteMethodArgument(ExecutionContext ctx, Expression methodArgument) {
            if (!isArgumentMatcher(methodArgument)) {
                return methodArgument;
            }
            String argumentMatcher, template;
            List<Object> argumentTemplateParams = new ArrayList<>();
            if (!(methodArgument instanceof J.TypeCast)) {
                argumentMatcher = ((J.Identifier) methodArgument).getSimpleName();
                template = argumentMatcher + "()";
                return rewriteMethodArgument(ctx, argumentMatcher, template, methodArgument, argumentTemplateParams);
            }
            J.TypeCast tc = (J.TypeCast) methodArgument;
            argumentMatcher = ((J.Identifier) tc.getExpression()).getSimpleName();
            String className, fqn;
            JavaType typeCastType = tc.getType();
            if (typeCastType instanceof JavaType.Parameterized) {
                // strip the raw type from the parameterized type
                className = ((JavaType.Parameterized) typeCastType).getType().getClassName();
                fqn = ((JavaType.Parameterized) typeCastType).getType().getFullyQualifiedName();
            } else if (typeCastType instanceof JavaType.FullyQualified) {
                className = ((JavaType.FullyQualified) typeCastType).getClassName();
                fqn = ((JavaType.FullyQualified) typeCastType).getFullyQualifiedName();
            } else {
                throw new IllegalStateException("Unexpected J.TypeCast type: " + typeCastType);
            }
            if (MOCKITO_COLLECTION_MATCHERS.containsKey(fqn)) {
                // mockito has specific argument matchers for collections
                argumentMatcher = MOCKITO_COLLECTION_MATCHERS.get(fqn);
                template = argumentMatcher + "()";
            } else {
                // rewrite parameter from ((<type>) any) to <type>.class
                argumentTemplateParams.add(JavaTemplate.builder("#{}.class")
                        .javaParser(JavaParser.fromJavaVersion())
                        .build()
                        .apply(
                                new Cursor(getCursor(), tc),
                                tc.getCoordinates().replace(),
                                className
                        ));
                template = argumentMatcher + "(#{any(java.lang.Class)})";
            }
            return rewriteMethodArgument(ctx, argumentMatcher, template, methodArgument, argumentTemplateParams);
        }

        private Expression rewriteMethodArgument(ExecutionContext ctx, String argumentMatcher, String template,
                                                 Expression methodArgument, List<Object> templateParams) {
            maybeAddImport("org.mockito.Mockito", argumentMatcher);
            return JavaTemplate.builder(template)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports("org.mockito.Mockito." + argumentMatcher)
                    .build()
                    .apply(
                            new Cursor(getCursor(), methodArgument),
                            methodArgument.getCoordinates().replace(),
                            templateParams.toArray()
                    );
        }

        private static boolean isArgumentMatcher(Expression expression) {
            if (expression instanceof J.TypeCast) {
                expression = ((J.TypeCast) expression).getExpression();
            }
            if (!(expression instanceof J.Identifier)) {
                return false;
            }
            J.Identifier identifier = (J.Identifier) expression;
            return JMOCKIT_ARGUMENT_MATCHERS.contains(identifier.getSimpleName());
        }

        private static String getMockitoStatementTemplate(Expression result, String fqn,
                                                          List<Expression> mockArguments) {
            if (result == null) {
                return getVoidResultTemplate(fqn, mockArguments);
            }
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
}
