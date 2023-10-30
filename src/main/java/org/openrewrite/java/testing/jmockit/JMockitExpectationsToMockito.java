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
import java.util.regex.Pattern;

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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

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

        private static final String VOID_RESULT_TEMPLATE = "doNothing().when(#{any(java.lang.String)});";
        private static final String PRIMITIVE_RESULT_TEMPLATE = "when(#{any()}).thenReturn(#{});";
        private static final String OBJECT_RESULT_TEMPLATE = "when(#{any()}).thenReturn(#{any(java.lang.String)});";
        private static final String THROWABLE_RESULT_TEMPLATE = "when(#{any()}).thenThrow(#{any()});";
        private static final Pattern EXPECTATIONS_PATTERN = Pattern.compile("^mockit.Expectations$");
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
                if (clazz.getType() == null || !clazz.getType().isAssignableFrom(EXPECTATIONS_PATTERN)) {
                    continue;
                }
                // empty Expectations block is considered invalid
                assert nc.getBody() != null && !nc.getBody().getStatements().isEmpty() : "Expectations block is empty";
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
                    // TODO: handle additional jmockit expectations features

                    if (expectationStatement instanceof J.MethodInvocation) {
                        if (!templateParams.isEmpty()) {
                            // apply template to build new method body
                            newBody = applyTemplate(ctx, templateParams, cursorLocation, coordinates);

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
                    } else {
                        // assignment
                        templateParams.add(((J.Assignment) expectationStatement).getAssignment());
                    }
                }

                // handle the last statement
                if (!templateParams.isEmpty()) {
                    newBody = applyTemplate(ctx, templateParams, cursorLocation, coordinates);
                }
            }

            return md.withBody(newBody);
        }

        private J.Block applyTemplate(ExecutionContext ctx, List<Object> templateParams, Object cursorLocation, JavaCoordinates coordinates) {
            Expression result = null;
            String staticImport;
            if (templateParams.size() > 1) {
                maybeAddImport("org.mockito.Mockito", "when");
                staticImport = "org.mockito.Mockito.when";
                result = (Expression) templateParams.get(1);
            } else {
                maybeAddImport("org.mockito.Mockito", "doNothing");
                staticImport = "org.mockito.Mockito.doNothing";
            }
            rewriteArgumentMatchers(ctx, templateParams);
            return JavaTemplate.builder(getTemplate(result))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports(staticImport)
                    .build()
                    .apply(
                            new Cursor(getCursor(), cursorLocation),
                            coordinates,
                            templateParams.toArray()
                    );
        }

        private void rewriteArgumentMatchers(ExecutionContext ctx, List<Object> templateParams) {
            J.MethodInvocation invocation = (J.MethodInvocation) templateParams.get(0);
            List<Expression> newArguments = new ArrayList<>(invocation.getArguments().size());
            for (Expression methodArgument : invocation.getArguments()) {
                if (!isArgumentMatcher(methodArgument)) {
                    newArguments.add(methodArgument);
                    continue;
                }
                String argumentMatcher = ((J.Identifier) methodArgument).getSimpleName();
                maybeAddImport("org.mockito.Mockito", argumentMatcher);
                newArguments.add(JavaTemplate.builder(argumentMatcher + "()")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .staticImports("org.mockito.Mockito." + argumentMatcher)
                        .build()
                        .apply(
                                new Cursor(getCursor(), methodArgument),
                                methodArgument.getCoordinates().replace()
                        ));
            }
            templateParams.set(0, invocation.withArguments(newArguments));
        }

        private static boolean isArgumentMatcher(Expression expression) {
            if (!(expression instanceof J.Identifier)) {
                return false;
            }
            J.Identifier identifier = (J.Identifier) expression;
            return JMOCKIT_ARGUMENT_MATCHERS.contains(identifier.getSimpleName());
        }

        /*
         * Based on the result type, we need to use a different template.
         */
        private static String getTemplate(Expression result) {
            if (result == null) {
                return VOID_RESULT_TEMPLATE;
            }
            String template;
            JavaType resultType = Objects.requireNonNull(result.getType());
            if (resultType instanceof JavaType.Primitive) {
                template = PRIMITIVE_RESULT_TEMPLATE;
            } else if (resultType instanceof JavaType.Class) {
                Class<?> resultClass;
                try {
                    resultClass = Class.forName(((JavaType.Class) resultType).getFullyQualifiedName());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                template = Throwable.class.isAssignableFrom(resultClass) ? THROWABLE_RESULT_TEMPLATE : OBJECT_RESULT_TEMPLATE;
            } else {
                throw new IllegalStateException("Unexpected value: " + result.getType());
            }
            return template;
        }
    }
}
