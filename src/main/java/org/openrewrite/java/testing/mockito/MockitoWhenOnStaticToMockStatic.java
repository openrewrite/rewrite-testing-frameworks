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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockitoWhenOnStaticToMockStatic extends Recipe {

    private static final MethodMatcher MOCKITO_WHEN = new MethodMatcher("org.mockito.Mockito when(..)");

    @Override
    public String getDisplayName() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic";
    }

    @Override
    public String getDescription() {
        return "Replace `Mockito.when` on static (non mock) with try-with-resource with MockedStatic as Mockito4 no longer allows this.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MOCKITO_WHEN), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (m.getBody() == null) {
                    return m;
                }

                List<Statement> newStatements = maybeWrapStatementsInTryWithResourcesMockedStatic(m, m.getBody().getStatements());
                return maybeAutoFormat(m, m.withBody(m.getBody().withStatements(newStatements)), ctx);
            }

            private List<Statement> maybeWrapStatementsInTryWithResourcesMockedStatic(J.MethodDeclaration m, List<Statement> originalStatements) {
                AtomicBoolean restInTry = new AtomicBoolean(false);
                return ListUtils.flatMap(originalStatements, (index, statement) -> {
                    if (restInTry.get()) {
                        // Rest of the statements have ended up in the try block
                        return Collections.emptyList();
                    }

                    if (statement instanceof J.MethodInvocation &&
                        MOCKITO_WHEN.matches(((J.MethodInvocation) statement).getSelect())) {
                        J.MethodInvocation when = (J.MethodInvocation) ((J.MethodInvocation) statement).getSelect();
                        if (when != null && when.getArguments().get(0) instanceof J.MethodInvocation) {
                            J.MethodInvocation whenArg = (J.MethodInvocation) when.getArguments().get(0);
                            if (whenArg.getMethodType() != null && whenArg.getMethodType().hasFlags(Flag.Static)) {
                                J.Identifier clazz = (J.Identifier) whenArg.getSelect();
                                if (clazz != null && clazz.getType() != null) {
                                    String mockName = VariableNameUtils.generateVariableName("mock" + clazz.getSimpleName(), updateCursor(m), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                                    maybeAddImport("org.mockito.MockedStatic", false);
                                    maybeAddImport("org.mockito.Mockito", "mockStatic");
                                    String template = String.format(
                                            "try(MockedStatic<#{}> %1$s = mockStatic(#{}.class)) {\n" +
                                            "    %1$s.when(#{any()}).thenReturn(#{any()});\n" +
                                            "}", mockName);
                                    J.Try try_ = (J.Try) ((J.MethodDeclaration) JavaTemplate.builder(template)
                                            .contextSensitive()
                                            .imports("org.mockito.MockedStatic")
                                            .staticImports("org.mockito.Mockito.mockStatic")
                                            .build()
                                            .apply(getCursor(), m.getCoordinates().replaceBody(),
                                                    clazz.getType(), clazz.getType(),
                                                    whenArg, ((J.MethodInvocation) statement).getArguments().get(0)))
                                            .getBody().getStatements().get(0);

                                    restInTry.set(true);

                                    return try_.withBody(try_.getBody().withStatements(ListUtils.concatAll(
                                                    try_.getBody().getStatements(),
                                                    maybeWrapStatementsInTryWithResourcesMockedStatic(
                                                            m.withBody(m.getBody().withStatements(ListUtils.concat(m.getBody().getStatements(), try_))),
                                                            originalStatements.subList(index + 1, originalStatements.size())
                                                    ))))
                                            .withPrefix(statement.getPrefix());
                                }
                            }
                        }
                    }
                    return statement;
                });
            }
        });
    }
}
