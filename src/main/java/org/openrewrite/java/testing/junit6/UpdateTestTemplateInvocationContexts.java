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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class UpdateTestTemplateInvocationContexts extends Recipe {

    private static final String TEST_TEMPLATE_PROVIDER = "org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider";
    private static final String TEST_TEMPLATE_CONTEXT = "org.junit.jupiter.api.extension.TestTemplateInvocationContext";
    private static final MethodMatcher PROVIDE_CONTEXTS_MATCHER = new MethodMatcher(
            TEST_TEMPLATE_PROVIDER + " provideTestTemplateInvocationContexts(..)", true);

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

                // Use MethodMatcher to check if this is the provideTestTemplateInvocationContexts method
                if (PROVIDE_CONTEXTS_MATCHER.matches(md.getMethodType()) && md.getReturnTypeExpression() != null) {
                    // Check if return type needs updating
                    if (md.getReturnTypeExpression() instanceof J.ParameterizedType) {
                        J.ParameterizedType pt = (J.ParameterizedType) md.getReturnTypeExpression();

                        // Check if it's Stream<TestTemplateInvocationContext> (without wildcard)
                        if (pt.getClazz() != null &&
                                pt.getClazz().toString().contains("Stream") &&
                                pt.getTypeParameters() != null &&
                                pt.getTypeParameters().size() == 1) {

                            // Check if the type parameter is not already a wildcard
                            if (!(pt.getTypeParameters().get(0) instanceof J.Wildcard)) {
                                JavaTemplate template = JavaTemplate.builder("Stream<? extends TestTemplateInvocationContext>")
                                        .imports("java.util.stream.Stream", TEST_TEMPLATE_CONTEXT)
                                        .javaParser(JavaParser.fromJavaVersion()
                                                .classpathFromResources(ctx, "junit-jupiter-api"))
                                        .build();
                                md = md.withReturnTypeExpression(template.apply(getCursor(),
                                        pt.getCoordinates().replace()));
                            }
                        }
                    }
                }

                return md;
            }
        });
    }
}
