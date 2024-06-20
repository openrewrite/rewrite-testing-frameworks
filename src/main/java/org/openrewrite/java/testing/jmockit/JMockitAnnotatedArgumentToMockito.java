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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
public class JMockitAnnotatedArgumentToMockito extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert JMockit `@Mocked` and `@Injectable` annotated arguments";
    }

    @Override
    public String getDescription() {
        return "Convert JMockit `@Mocked` and `@Injectable` annotated arguments into Mockito statements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("mockit.Mocked", false),
                        new UsesType<>("mockit.Injectable", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                        J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);

                        List<Statement> parameters = md.getParameters();
                        if (!parameters.isEmpty() && !(parameters.get(0) instanceof J.Empty)) {
                            maybeRemoveImport("mockit.Injectable");
                            maybeRemoveImport("mockit.Mocked");
                            maybeAddImport("org.mockito.Mockito");

                            // Create lists to store the mocked parameters and the new type parameters
                            List<J.VariableDeclarations> mockedParameter = new ArrayList<>();

                            // Remove any mocked parameters from the method declaration
                            md = md.withParameters(ListUtils.map(parameters, parameter -> {
                                if (parameter instanceof J.VariableDeclarations) {
                                    J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) parameter;
                                    // Check if the parameter has the annotation "mockit.Mocked or mockit.Injectable"
                                    if (!FindAnnotations.find(variableDeclarations, "mockit.Injectable").isEmpty() ||
                                        !FindAnnotations.find(variableDeclarations, "mockit.Mocked").isEmpty() ) {
                                        mockedParameter.add(variableDeclarations);
                                        return null;
                                    }
                                }
                                return parameter;
                            }));

                            // Add mocked parameters as statements to the method declaration
                            if (!mockedParameter.isEmpty()) {
                                JavaTemplate addStatementsTemplate = JavaTemplate.builder("#{} #{} = Mockito.mock(#{}.class);\n")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                                        .imports("org.mockito.Mockito")
                                        .contextSensitive()
                                        .build();
                                // Retain argument order by iterating in reverse
                                for (int i = mockedParameter.size() - 1; i >= 0; i--) {
                                    J.VariableDeclarations variableDeclarations = mockedParameter.get(i);
                                    // Apply the template and update the method declaration
                                    md = addStatementsTemplate.apply(updateCursor(md),
                                            md.getBody().getCoordinates().firstStatement(),
                                            variableDeclarations.getTypeExpression().toString(),
                                            variableDeclarations.getVariables().get(0).getSimpleName(),
                                            variableDeclarations.getTypeExpression().toString());
                                }
                            }
                        }
                        return md;
                    }
                }
        );
    }
}
