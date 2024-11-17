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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

public class UseAssertSame extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use JUnit5's `assertSame` or `assertNotSame` instead of assertTrue(... == ...)";
    }

    @Override
    public String getDescription() {
        return "Prefers the usage of `assertSame` or `assertNotSame` methods instead of using of vanilla assertTrue " +
                "or assertFalse with a boolean comparison.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        MethodMatcher assertTrueMatcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertTrue(..)");
        MethodMatcher assertFalseMatcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertFalse(..)");
        TreeVisitor<?, ExecutionContext> orMatcher = Preconditions.or(new UsesMethod<>(assertTrueMatcher), new UsesMethod<>(assertFalseMatcher));
        return Preconditions.check(orMatcher, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);
                if (m.getBody() == null) {
                    return m;
                }
                return m.withBody(m.getBody().withStatements(ListUtils.flatMap(m.getBody().getStatements(), methodStatement -> {
                    if (!(methodStatement instanceof J.MethodInvocation)) {
                        return methodStatement;
                    }
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) methodStatement;
                    if (!assertTrueMatcher.matches(methodInvocation) && !assertFalseMatcher.matches(methodInvocation)) {
                        return methodStatement;
                    }

                    Expression firstArgument = methodInvocation.getArguments().get(0);
                    List<Expression> argumentsTail = methodInvocation.getArguments().subList(1, methodInvocation.getArguments().size());
                    if (!(firstArgument instanceof J.Binary)) {
                        return methodStatement;
                    }
                    J.Binary binary = (J.Binary) firstArgument;
                    if (binary.getOperator() != J.Binary.Type.Equal && binary.getOperator() != J.Binary.Type.NotEqual) {
                        return methodStatement;
                    }
                    boolean positive = binary.getOperator() == J.Binary.Type.Equal == assertTrueMatcher.matches(methodInvocation);
                    List<Expression> newArguments = new ArrayList<>();
                    newArguments.add(binary.getLeft());
                    newArguments.add(binary.getRight());
                    newArguments.addAll(argumentsTail);

                    String newMethodName = positive ? "assertSame" : "assertNotSame";

                    maybeRemoveImport("org.junit.jupiter.api.Assertions");
                    maybeAddImport("org.junit.jupiter.api.Assertions", newMethodName);

                    return methodInvocation
                            .withName(methodInvocation.getName().withSimpleName(newMethodName))
                            .withArguments(newArguments);
                })));
            }
        });
    }
}
