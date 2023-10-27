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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
public class JMockitExpectationsToMockitoWhen extends Recipe {
    @Override
    public String getDisplayName() {
        return "Rewrite JMockit Expectations";
    }

    @Override
    public String getDescription() {
        return "Rewrites JMockit `Expectations` to `Mockito.when`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("mockit.*", false),
                new RewriteExpectationsVisitor());
    }

    private static class RewriteExpectationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String PRIMITIVE_RESULT_TEMPLATE = "when(#{any()}).thenReturn(#{});";
        private static final String OBJECT_RESULT_TEMPLATE = "when(#{any()}).thenReturn(#{any(java.lang.String)});";
        private static final String EXCEPTION_RESULT_TEMPLATE = "when(#{any()}).thenThrow(#{any()});";
        private static final Pattern EXPECTATIONS_PATTERN = Pattern.compile("mockit.Expectations");

        // the LST element that is being updated when applying one of the java templates
        private Object cursorLocation;

        // the coordinates where the next statement should be inserted
        private JavaCoordinates coordinates;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }
            cursorLocation = md.getBody();
            J.Block newBody = md.getBody();
            List<Statement> statements = md.getBody().getStatements();

            // iterate over each statement in the method body, find Expectations blocks and rewrite them
            for (int i = 0; i < statements.size(); i++) {
                Statement s = statements.get(i);
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
                maybeAddImport("org.mockito.Mockito", "when");
                maybeRemoveImport("mockit.Expectations");

                // the first coordinates are the coordinates the Expectations block, replacing it
                coordinates = nc.getCoordinates().replace();
                J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);
                List<Statement> expectationStatements = expectationsBlock.getStatements();
                List<Object> templateParams = new ArrayList<>();

                // iterate over the expectations statements and rebuild the method body
                for (Statement expectationStatement : expectationStatements) {
                    // TODO: handle void methods (including final statement)

                    // TODO: handle additional jmockit expectations features

                    if (expectationStatement instanceof J.MethodInvocation) {
                        if (!templateParams.isEmpty()) {
                            // apply template to build new method body
                            newBody = buildNewBody(ctx, templateParams, i);

                            // reset template params for next expectation
                            templateParams = new ArrayList<>();
                        }
                        templateParams.add(expectationStatement);
                    } else {
                        // assignment
                        templateParams.add(((J.Assignment) expectationStatement).getAssignment());
                    }
                }

                // handle the last statement
                if (!templateParams.isEmpty()) {
                    newBody = buildNewBody(ctx, templateParams, i);
                }
            }

            return md.withBody(newBody);
        }

        private J.Block buildNewBody(ExecutionContext ctx, List<Object> templateParams, int newStatementIndex) {
            Expression result = (Expression) templateParams.get(1);
            String template = getTemplate(result);

            J.Block newBody = JavaTemplate.builder(template)
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .staticImports("org.mockito.Mockito.when")
                    .build()
                    .apply(
                            new Cursor(getCursor(), cursorLocation),
                            coordinates,
                            templateParams.toArray()
                    );

            List<Statement> newStatements = new ArrayList<>(newBody.getStatements().size());
            for (int i = 0; i < newBody.getStatements().size(); i++) {
                Statement s = newBody.getStatements().get(i);
                if (i == newStatementIndex) {
                    // next statement coordinates are immediately after the statement just added
                    coordinates = s.getCoordinates().after();
                }
                newStatements.add(s);
            }
            newBody = newBody.withStatements(newStatements);

            // cursor location is now the new body
            cursorLocation = newBody;

            return newBody;
        }

        /*
         * Based on the result type, we need to use a different template.
         */
        private static String getTemplate(Expression result) {
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
                template = Throwable.class.isAssignableFrom(resultClass) ? EXCEPTION_RESULT_TEMPLATE : OBJECT_RESULT_TEMPLATE;
            } else {
                throw new IllegalStateException("Unexpected value: " + result.getType());
            }
            return template;
        }
    }
}
