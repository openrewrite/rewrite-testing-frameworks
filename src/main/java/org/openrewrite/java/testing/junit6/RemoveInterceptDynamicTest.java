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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.tree.J;

public class RemoveInterceptDynamicTest extends Recipe {

    private static final MethodMatcher INTERCEPT_DYNAMIC_TEST_MATCHER = new MethodMatcher(
            "org.junit.jupiter.api.extension.InvocationInterceptor interceptDynamicTest(..)", true);

    @Getter
    final String displayName = "Remove `InvocationInterceptor.interceptDynamicTest`";

    @Getter
    final String description = "JUnit 6 removed the `interceptDynamicTest(Invocation, ExtensionContext)` method from " +
            "`InvocationInterceptor`. This recipe removes implementations of this deprecated method.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new DeclaresMethod<>(INTERCEPT_DYNAMIC_TEST_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (INTERCEPT_DYNAMIC_TEST_MATCHER.matches(md.getMethodType())) {
                    return null;
                }
                return md;
            }
        });
    }
}
