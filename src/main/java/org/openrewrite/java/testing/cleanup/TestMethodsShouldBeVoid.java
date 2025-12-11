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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Objects.requireNonNull;

public class TestMethodsShouldBeVoid extends Recipe {
    /**
     * Returns whether the given method is intended to be a JUnit test method.
     */
    static boolean isIntendedTestMethod(J.MethodDeclaration method) {
        if (method.getBody() == null) {
            return false;
        }
        for (J.Annotation annotation : method.getLeadingAnnotations()) {
            if (TypeUtils.isOfClassType(annotation.getType(), "org.junit.Test") ||
                    TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.RepeatedTest") ||
                    TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.Test") ||
                    TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.api.TestTemplate") ||
                    TypeUtils.isOfClassType(annotation.getType(), "org.junit.jupiter.params.ParameterizedTest")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getDisplayName() {
        return "Test methods should have void return type";
    }

    @Override
    public String getDescription() {
        return "Test methods annotated with `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate` " +
                "should have `void` return type. Non-void return types can cause test discovery issues, " +
                "and warnings as of JUnit 5.13+. This recipe changes the return type to `void` and removes `return` statements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org..* *Test*", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                // If the method is not intended to the be a test method, do nothing.
                if (!isIntendedTestMethod(m)) {
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
                        voidType
                ));

                // Update method type
                if (m.getMethodType() != null) {
                    m = m.withMethodType(m.getMethodType().withReturnType(voidType));
                }

                // Remove return statements that are not in nested classes or lambdas
                return m.withBody((J.Block) new RemoveDirectReturns().visitBlock(requireNonNull(m.getBody()), ctx));
            }
        });
    }

    private static class RemoveDirectReturns extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitLambda(J.Lambda lambda, ExecutionContext ctx) {
            return lambda; // Retain nested returns
        }

        @Override
        public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            return newClass; // Retain nested returns
        }

        @Override
        public @Nullable J visitReturn(J.Return retrn, ExecutionContext ctx) {
            return retrn.getExpression() instanceof Statement ?
                    // Retain any side effects from expressions in return statements
                    retrn.getExpression().withPrefix(retrn.getPrefix()) :
                    // Remove any other return statements
                    null;
        }
    }
}
