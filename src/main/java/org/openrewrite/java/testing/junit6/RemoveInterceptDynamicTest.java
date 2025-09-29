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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

public class RemoveInterceptDynamicTest extends Recipe {

    private static final String INVOCATION_INTERCEPTOR = "org.junit.jupiter.api.extension.InvocationInterceptor";

    @Override
    public String getDisplayName() {
        return "Remove InvocationInterceptor.interceptDynamicTest";
    }

    @Override
    public String getDescription() {
        return "JUnit 6 removed the `interceptDynamicTest(Invocation, ExtensionContext)` method from " +
               "`InvocationInterceptor`. This recipe removes implementations of this deprecated method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(INVOCATION_INTERCEPTOR, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                // Check if method is named interceptDynamicTest
                if (md.getSimpleName().equals("interceptDynamicTest")) {
                    // Check if this is implementing the interface method
                    J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (enclosingClass != null && implementsInvocationInterceptor(enclosingClass)) {
                        // Remove the method entirely
                        return null;
                    }
                }

                return md;
            }

            private boolean implementsInvocationInterceptor(J.ClassDeclaration classDecl) {
                if (classDecl.getImplements() != null) {
                    for (TypeTree impl : classDecl.getImplements()) {
                        if (TypeUtils.isOfClassType(impl.getType(), INVOCATION_INTERCEPTOR)) {
                            return true;
                        }
                    }
                }

                // Check if extending a class that implements InvocationInterceptor
                if (classDecl.getExtends() != null) {
                    JavaType.FullyQualified extendedType = TypeUtils.asFullyQualified(classDecl.getExtends().getType());
                    if (extendedType != null) {
                        for (JavaType.FullyQualified iface : extendedType.getInterfaces()) {
                            if (INVOCATION_INTERCEPTOR.equals(iface.getFullyQualifiedName())) {
                                return true;
                            }
                        }
                    }
                }

                return false;
            }
        });
    }
}
