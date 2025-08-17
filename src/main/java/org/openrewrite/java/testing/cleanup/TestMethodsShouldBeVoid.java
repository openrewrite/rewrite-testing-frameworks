/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class TestMethodsShouldBeVoid extends Recipe {

    @Override
    public String getDisplayName() {
        return "Test methods should have void return type";
    }

    @Override
    public String getDescription() {
        return "Test methods annotated with `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@TestTemplate` " +
                "should have `void` return type. Non-void return types can cause test discovery issues, " +
                "especially in JUnit 5.13+. This recipe changes the return type to `void` and removes `return` statements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                // Check if method has a test annotation
                if (!hasTestAnnotation(m)) {
                    return m;
                }

                // Check if return type is already void
                JavaType.Primitive voidType = JavaType.Primitive.Void;
                if (m.getReturnTypeExpression() != null && TypeUtils.isOfType(m.getReturnTypeExpression().getType(), voidType)) {
                    return m;
                }

                // Change return type to void
                m = m.withReturnTypeExpression(new J.Primitive(
                        m.getReturnTypeExpression() != null ? m.getReturnTypeExpression().getId() : Tree.randomId(),
                        m.getReturnTypeExpression() != null ? m.getReturnTypeExpression().getPrefix() : Space.EMPTY,
                        m.getReturnTypeExpression() != null ? m.getReturnTypeExpression().getMarkers() : org.openrewrite.marker.Markers.EMPTY,
                        JavaType.Primitive.Void
                ));

                // Update method type
                if (m.getMethodType() != null) {
                    m = m.withMethodType(m.getMethodType().withReturnType(voidType));
                }

                // Remove return statements that are not in nested classes or lambdas
                m = m.withBody(removeReturnStatements(m.getBody()));

                return m;
            }

            private boolean hasTestAnnotation(J.MethodDeclaration method) {
                for (J.Annotation annotation : method.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.Test") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.params.ParameterizedTest") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.RepeatedTest") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.TestFactory") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.TestTemplate") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.Test") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.testng.annotations.Test")) {
                        return true;
                    }
                }
                return false;
            }

            private J.Block removeReturnStatements(J.Block block) {
                if (block == null) {
                    return null;
                }

                RemoveReturnsVisitor visitor = new RemoveReturnsVisitor();
                return (J.Block) visitor.visitBlock(block, new InMemoryExecutionContext());
            }
        };
    }

    private static class RemoveReturnsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private int nestedLevel = 0;

        @Override
        public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
            nestedLevel++;
            J.Lambda result = super.visitLambda(lambda, ctx);
            nestedLevel--;
            return result;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            if (newClass.getBody() != null) {
                nestedLevel++;
                J.NewClass result = super.visitNewClass(newClass, ctx);
                nestedLevel--;
                return result;
            }
            return super.visitNewClass(newClass, ctx);
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            if (nestedLevel == 0) {
                // Process the statements to remove returns at the top level
                List<org.openrewrite.java.tree.Statement> newStatements = new ArrayList<>();
                for (org.openrewrite.java.tree.Statement statement : b.getStatements()) {
                    if (statement instanceof J.Return) {
                        J.Return return_ = (J.Return) statement;
                        // If the return has an expression that's not a literal, keep it as a statement
                        if (return_.getExpression() != null && !(return_.getExpression() instanceof J.Literal)) {
                            org.openrewrite.java.tree.Expression expr = return_.getExpression();
                            // Check if the expression is already a statement (e.g., method invocation)
                            if (expr instanceof org.openrewrite.java.tree.Statement) {
                                newStatements.add(((org.openrewrite.java.tree.Statement) expr).withPrefix(statement.getPrefix()));
                            }
                            // Otherwise, we can't simply convert it - just remove the return
                        }
                        // Otherwise, don't add anything (removes "return;" or "return literal;")
                    } else {
                        newStatements.add(statement);
                    }
                }
                return b.withStatements(newStatements);
            }
            return b;
        }
    }
}
