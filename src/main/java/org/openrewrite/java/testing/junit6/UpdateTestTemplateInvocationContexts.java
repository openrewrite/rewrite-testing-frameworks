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
package org.openrewrite.java.testing.junit6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;

public class UpdateTestTemplateInvocationContexts extends Recipe {

    private static final String TEST_TEMPLATE_PROVIDER = "org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider";
    private static final String TEST_TEMPLATE_CONTEXT = "org.junit.jupiter.api.extension.TestTemplateInvocationContext";
    private static final MethodMatcher PROVIDE_CONTEXTS = new MethodMatcher(
            TEST_TEMPLATE_PROVIDER + " provideTestTemplateInvocationContexts(..)"
    );

    @Override
    public String getDisplayName() {
        return "Update TestTemplateInvocationContext return types";
    }

    @Override
    public String getDescription() {
        return "JUnit 6 changed the return type of `provideTestTemplateInvocationContexts()` from " +
               "`Stream<TestTemplateInvocationContext>` to `Stream<? extends TestTemplateInvocationContext>`. " +
               "This recipe updates custom test template providers to use the new signature.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(TEST_TEMPLATE_PROVIDER, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                if (PROVIDE_CONTEXTS.matches(md.getMethodType()) && md.getReturnTypeExpression() != null) {
                    // Check if the return type is Stream<TestTemplateInvocationContext>
                    JavaType returnType = md.getReturnTypeExpression().getType();
                    if (returnType instanceof JavaType.Parameterized) {
                        JavaType.Parameterized parameterized = (JavaType.Parameterized) returnType;
                        if (TypeUtils.isOfClassType(parameterized, "java.util.stream.Stream") &&
                            parameterized.getTypeParameters().size() == 1) {

                            JavaType typeParam = parameterized.getTypeParameters().get(0);
                            if (TypeUtils.isOfClassType(typeParam, TEST_TEMPLATE_CONTEXT)) {
                                // Need to update to Stream<? extends TestTemplateInvocationContext>
                                TypeTree returnTypeExpression = md.getReturnTypeExpression();
                                if (returnTypeExpression instanceof J.ParameterizedType) {
                                    J.ParameterizedType pt = (J.ParameterizedType) returnTypeExpression;
                                    if (pt.getTypeParameters() != null && !pt.getTypeParameters().isEmpty()) {
                                        // Create wildcard type
                                        J.Wildcard wildcard = new J.Wildcard(
                                                java.util.UUID.randomUUID(),
                                                pt.getTypeParameters().get(0).getPrefix(),
                                                pt.getTypeParameters().get(0).getMarkers(),
                                                J.Wildcard.Bound.Extends,
                                                pt.getTypeParameters().get(0).withPrefix(J.Space.SINGLE_SPACE)
                                        );

                                        J.ParameterizedType updatedPt = pt.withTypeParameters(Collections.singletonList(wildcard));
                                        md = md.withReturnTypeExpression(updatedPt);
                                    }
                                }
                            }
                        }
                    }
                }

                return md;
            }
        });
    }
}