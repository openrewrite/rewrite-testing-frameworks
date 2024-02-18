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
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class JMockitMockedVariableToMockito extends Recipe {
    @Override
    public String getDisplayName() {
        return "Rewrite JMockit Mocked Variable";
    }

    @Override
    public String getDescription() {
        return "Rewrites JMockit `Mocked Variable` to Mockito statements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("mockit.Mocked", false),
                new RewriteMockedVariableVisitor());
    }

    private static class RewriteMockedVariableVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, @NotNull ExecutionContext ctx) {
            System.out.println(TreeVisitingPrinter.printTree(getCursor()));

            List<Statement> parameters = methodDeclaration.getParameters();
            if (!parameters.isEmpty()) {
                this.maybeRemoveImport("mockit.Mocked");
                this.maybeAddImport("org.mockito.Mockito");

                // Create lists to store the mocked parameters and the new type parameters
                List<J.VariableDeclarations> mockedParameter = new ArrayList<>(parameters.size());
                List<Statement> newTypeParameterList = new ArrayList<>(parameters.size());
                parameters.forEach(parameter -> {
                    if (parameter instanceof J.VariableDeclarations) {
                        J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) parameter;
                        // Check if the parameter has the annotation "mockit.Mocked"
                        if (variableDeclarations.getLeadingAnnotations().stream().anyMatch(annotation -> annotation.getType() != null && annotation.getType().toString().equals("mockit.Mocked"))) {
                            mockedParameter.add(variableDeclarations);
                        } else {
                            newTypeParameterList.add(variableDeclarations);
                        }
                    }
                });

                // Check if there are mocked parameters
                if (!mockedParameter.isEmpty()) {
                    // Update the method declaration with the new type parameters
                    methodDeclaration = methodDeclaration.withParameters(newTypeParameterList);
                    JavaTemplate addStatementsTemplate = JavaTemplate.builder("#{} #{} = Mockito.mock(#{}.class);\n")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                            .imports("org.mockito.Mockito")
                            .contextSensitive()
                            .build();
                    for (int i = mockedParameter.size() - 1; i >= 0; i--) {
                        J.VariableDeclarations variableDeclarations = mockedParameter.get(i);
                        // Apply the template and update the method declaration
                        methodDeclaration = maybeAutoFormat(
                                methodDeclaration, addStatementsTemplate.apply(updateCursor(methodDeclaration),
                                        methodDeclaration.getBody().getCoordinates().firstStatement(),
                                        variableDeclarations.getTypeExpression().toString(),
                                        variableDeclarations.getVariables().get(0).getSimpleName(),
                                        variableDeclarations.getTypeExpression().toString()),
                                ctx
                        );
                    }
                }
            }
            return methodDeclaration;
        }
    }
}
