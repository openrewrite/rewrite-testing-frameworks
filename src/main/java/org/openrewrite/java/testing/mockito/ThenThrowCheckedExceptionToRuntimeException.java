/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;


public class ThenThrowCheckedExceptionToRuntimeException extends Recipe {
    @Getter
    final String displayName = "Replace undeclared checked exceptions in `thenThrow` with `RuntimeException`";

    @Getter
    final String description = "In Mockito 3+, `thenThrow()` validates that checked exceptions are declared " +
            "in the mocked method's `throws` clause. This recipe replaces checked exception class " +
            "literals in `thenThrow()` calls with `RuntimeException.class` when the mocked method " +
            "does not declare the exception.";

    private static final MethodMatcher WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final MethodMatcher THEN_THROW_MATCHER = new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenThrow(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.mockito.stubbing.OngoingStubbing", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        if (!THEN_THROW_MATCHER.matches(mi)) {
                            return mi;
                        }

                        JavaType.Method mockedMethodType = findMockedMethodType(mi);
                        if (mockedMethodType == null) {
                            return mi;
                        }

                        return mi.withArguments(ListUtils.map(mi.getArguments(), arg -> {
                            if (arg instanceof J.FieldAccess) {
                                J.FieldAccess fa = (J.FieldAccess) arg;
                                if ("class".equals(fa.getName().getSimpleName())) {
                                    JavaType exceptionType = fa.getTarget().getType();
                                    if (isUndeclaredCheckedException(exceptionType, mockedMethodType) &&
                                            fa.getTarget() instanceof J.Identifier) {
                                        maybeRemoveImport(((JavaType.FullyQualified) exceptionType).getFullyQualifiedName());
                                        return fa.withTarget(((J.Identifier) fa.getTarget())
                                                .withSimpleName("RuntimeException")
                                                .withType(JavaType.ShallowClass.build("java.lang.RuntimeException")));
                                    }
                                }
                            }
                            return arg;
                        }));
                    }
                }
        );
    }

    private static JavaType.Method findMockedMethodType(J.MethodInvocation thenThrowCall) {
        Expression select = thenThrowCall.getSelect();
        if (select instanceof J.MethodInvocation) {
            J.MethodInvocation possibleWhenCall = (J.MethodInvocation) select;
            if (WHEN_MATCHER.matches(possibleWhenCall) && !possibleWhenCall.getArguments().isEmpty()) {
                Expression mockedCall = possibleWhenCall.getArguments().get(0);
                if (mockedCall instanceof J.MethodInvocation) {
                    return ((J.MethodInvocation) mockedCall).getMethodType();
                }
            }
        }
        return null;
    }

    private static boolean isUndeclaredCheckedException(JavaType exceptionType, JavaType.Method mockedMethodType) {
        if (!(exceptionType instanceof JavaType.FullyQualified)) {
            return false;
        }
        if (!TypeUtils.isAssignableTo("java.lang.Exception", exceptionType) ||
                TypeUtils.isAssignableTo("java.lang.RuntimeException", exceptionType)) {
            return false;
        }
        for (JavaType thrown : mockedMethodType.getThrownExceptions()) {
            if (thrown instanceof JavaType.FullyQualified &&
                    TypeUtils.isAssignableTo(((JavaType.FullyQualified) thrown).getFullyQualifiedName(), exceptionType)) {
                return false;
            }
        }
        return true;
    }
}
