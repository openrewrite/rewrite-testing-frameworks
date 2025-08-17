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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

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

                // Check if method has a test annotation & body
                if (!hasTestAnnotation(m) || m.getBody() == null) {
                    return m;
                }

                // Check if return type is already void
                JavaType.Primitive voidType = JavaType.Primitive.Void;
                if (m.getReturnTypeExpression() == null || TypeUtils.isOfType(m.getReturnTypeExpression().getType(), voidType)) {
                    return m;
                }

                // Change return type to void
                m = m.withReturnTypeExpression(new J.Primitive(
                        m.getReturnTypeExpression().getId(),
                        m.getReturnTypeExpression().getPrefix(),
                        m.getReturnTypeExpression().getMarkers(),
                        JavaType.Primitive.Void
                ));

                // Update method type
                if (m.getMethodType() != null) {
                    m = m.withMethodType(m.getMethodType().withReturnType(voidType));
                }

                // Remove return statements that are not in nested classes or lambdas
                return m.withBody((J.Block) new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitLambda(J.Lambda lambda, ExecutionContext ctx1) {
                        return lambda; // Retain nested returns
                    }

                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx1) {
                        return newClass; // Retain nested returns
                    }

                    @Override
                    public @Nullable J visitReturn(J.Return retrn, ExecutionContext ctx1) {
                        return retrn.getExpression() instanceof Statement ?
                                retrn.getExpression().withPrefix(retrn.getPrefix()) :
                                null; // Remove return statements that are not statements we need to retain
                    }
                }.visitBlock(m.getBody(), ctx));
            }

            private boolean hasTestAnnotation(J.MethodDeclaration method) {
                for (J.Annotation annotation : method.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(annotation.getType(), "org.junit.Test") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.Test") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.RepeatedTest") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.TestFactory") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.TestTemplate") ||
                            TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.params.ParameterizedTest")) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
