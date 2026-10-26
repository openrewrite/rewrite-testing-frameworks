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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class RemoveInterceptDynamicTest extends Recipe {

    private static final String DYNAMIC_TEST_INVOCATION_CONTEXT = "org.junit.jupiter.api.extension.DynamicTestInvocationContext";
    private static final MethodMatcher TWO_ARGUMENT_INTERCEPT_DYNAMIC_TEST = new MethodMatcher("org.junit.jupiter.api.extension.InvocationInterceptor interceptDynamicTest(org.junit.jupiter.api.extension.InvocationInterceptor$Invocation, org.junit.jupiter.api.extension.ExtensionContext)", true);
    private static final MethodMatcher THREE_ARGUMENT_INTERCEPT_DYNAMIC_TEST = new MethodMatcher("org.junit.jupiter.api.extension.InvocationInterceptor interceptDynamicTest(org.junit.jupiter.api.extension.InvocationInterceptor$Invocation, " + DYNAMIC_TEST_INVOCATION_CONTEXT + ", org.junit.jupiter.api.extension.ExtensionContext)", true);

    @Getter
    final String displayName = "Migrate the two-argument `InvocationInterceptor.interceptDynamicTest`";

    @Getter
    final String description = "JUnit 6 removed the deprecated `InvocationInterceptor#interceptDynamicTest(Invocation, ExtensionContext)`. " +
            "Java overrides gain a `DynamicTestInvocationContext` parameter, so they keep their body and override the remaining three-argument overload. " +
            "Overrides in classes that already override the three-argument overload, and non-Java overrides, are removed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(TWO_ARGUMENT_INTERCEPT_DYNAMIC_TEST), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = md.getMethodType();
                if (methodType == null || !TWO_ARGUMENT_INTERCEPT_DYNAMIC_TEST.matches(methodType)) {
                    return md;
                }
                if (getCursor().firstEnclosing(J.CompilationUnit.class) == null ||
                        methodType.getDeclaringType().getMethods().stream().anyMatch(THREE_ARGUMENT_INTERCEPT_DYNAMIC_TEST::matches)) {
                    return null;
                }

                String parameterName = generateVariableName("invocationContext", getCursor(), INCREMENT_NUMBER);
                J.MethodDeclaration withContext = JavaTemplate.builder("DynamicTestInvocationContext " + parameterName)
                        .imports(DYNAMIC_TEST_INVOCATION_CONTEXT)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-6"))
                        .build()
                        .apply(getCursor(), md.getCoordinates().replaceParameters());
                Statement contextParameter = withContext.getParameters().get(0).withPrefix(md.getParameters().get(1).getPrefix());
                JavaType contextType = withContext.getMethodType().getParameterTypes().get(0);
                maybeAddImport(DYNAMIC_TEST_INVOCATION_CONTEXT);
                return md
                        .withParameters(ListUtils.insert(md.getParameters(), contextParameter, 1))
                        .withMethodType(methodType
                                .withParameterNames(ListUtils.insert(methodType.getParameterNames(), parameterName, 1))
                                .withParameterTypes(ListUtils.insert(methodType.getParameterTypes(), contextType, 1)));
            }
        });
    }
}
