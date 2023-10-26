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
import org.openrewrite.java.tree.Space;
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

        private Object cursorLocation;
        private JavaCoordinates coordinates;
        private Space prefix;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }
            cursorLocation = md.getBody();
            List<Statement> statements = md.getBody().getStatements();
            J.Block newBody = md.getBody();
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

                maybeAddImport("org.mockito.Mockito", "when");
                maybeRemoveImport("mockit.Expectations");

                // prepare the statements for moving
                J.Block innerBlock = (J.Block) nc.getBody().getStatements().get(0);

                prefix = nc.getPrefix();
                coordinates = nc.getCoordinates().replace();
                List<Statement> expectationStatements = innerBlock.getStatements();
                List<Object> templateParams = new ArrayList<>();

                for (Statement expectationStatement : expectationStatements) {
                    // TODO: handle void methods (including final statement)

                    // TODO: handle additional jmockit expectations features

                    if (expectationStatement instanceof J.MethodInvocation && !templateParams.isEmpty()) {
                        newBody = buildNewBody(ctx, templateParams, i);

                        // reset for next statement
                        cursorLocation = newBody;
                        templateParams = new ArrayList<>();
                        templateParams.add(expectationStatement);
                    } else if (expectationStatement instanceof J.MethodInvocation) {
                        // fresh expectation
                        templateParams.add(expectationStatement);
                    } else {
                        // assignment
                        templateParams.add(((J.Assignment) expectationStatement).getAssignment());
                    }
                }

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
                    // next statement should go immediately after one just added
                    coordinates = s.getCoordinates().after();
                }
                newStatements.add(s.withPrefix(prefix));
            }
            return newBody.withStatements(newStatements);
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
