/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.mockito;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SonarS6068Fixer extends Recipe {
    @NlsRewrite.DisplayName
    @NotNull
    @Override
    public String getDisplayName() {
        return "java:S6068";
    }

    @NotNull
    @NlsRewrite.Description
    @Override
    public String getDescription() {
        return "Fixes Sonar Issue [java:S6068: Call to Mockito method \"verify\", \"when\" or \"given\" should be simplified].";
    }

    @NotNull
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SonarS6068FixerVisitor();
    }

    static class SonarS6068FixerVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation originalInvocation, ExecutionContext ctx) {
            final J.MethodInvocation methodInvocation = super.visitMethodInvocation(originalInvocation, ctx);

            if (isMockitoWhen(methodInvocation) && methodInvocation.getArguments().get(0) instanceof J.MethodInvocation) {
                final J.MethodInvocation whenArgument = (J.MethodInvocation) methodInvocation.getArguments().get(0);
                final List<Expression> originalArguments = methodInvocation.getArguments();
                final J.MethodInvocation updatedInvocation = checkAndUpdateEq(whenArgument);
                final List<Expression> updatedArguments = new ArrayList<>(originalArguments);
                updatedArguments.set(0, updatedInvocation);

                return methodInvocation.withArguments(updatedArguments);
            } else if (isInvokedOnVerify(methodInvocation)) {
                return checkAndUpdateEq(methodInvocation);
            }

            return originalInvocation;
        }

        private boolean isMockitoWhen(J.MethodInvocation methodInvocation) {
            return methodInvocation.getMethodType() != null &&
                   TypeUtils.isOfType(methodInvocation.getMethodType().getDeclaringType(), JavaType.buildType("org.mockito.Mockito")) &&
                   methodInvocation.getSimpleName().equals("when");
        }

        private boolean isInvokedOnVerify(J.MethodInvocation methodInvocation) {
            if (methodInvocation.getSelect() == null) {
                return false;
            }
            final Expression select = methodInvocation.getSelect();

            if (!(select instanceof J.MethodInvocation) || ((J.MethodInvocation) select).getMethodType() == null) {
                return false;
            }

            final J.MethodInvocation selectInvocation = (J.MethodInvocation) select;
            return TypeUtils.isOfType(selectInvocation.getMethodType().getDeclaringType(), JavaType.buildType("org.mockito.Mockito")) &&
                   selectInvocation.getSimpleName().equals("verify");
        }

        private J.MethodInvocation checkAndUpdateEq(J.MethodInvocation methodInvocation) {
            final List<Expression> originalArguments = methodInvocation.getArguments();
            final boolean onlyEqArguments = originalArguments.stream().allMatch(this::isEqExpression);
            if (!onlyEqArguments) {
                return methodInvocation;
            }

            final List<Expression> updatedArguments = originalArguments.stream()
                    .map(J.MethodInvocation.class::cast)
                    .map(invocation -> invocation.getArguments().get(0))
                    .collect(Collectors.toList());

            return methodInvocation.withArguments(updatedArguments);
        }

        private boolean isEqExpression(Expression expression) {
            if (!(expression instanceof J.MethodInvocation)) {
                return false;
            }
            final J.MethodInvocation invocation = (J.MethodInvocation) expression;

            if (invocation.getMethodType() == null || !TypeUtils.isOfType(invocation.getMethodType().getDeclaringType(), JavaType.buildType("org.mockito.ArgumentMatchers"))) {
                return false;
            }

            return invocation.getSimpleName().equals("eq");
        }
    }
}
