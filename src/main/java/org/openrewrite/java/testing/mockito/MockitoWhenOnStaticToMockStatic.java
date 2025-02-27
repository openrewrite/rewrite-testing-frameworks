/*
 * Copyright 2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;
import static org.openrewrite.java.tree.Flag.Static;

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

            private List<Statement> maybeWrapStatementsInTryWithResourcesMockedStatic(J.MethodDeclaration m, List<Statement> statements) {
                AtomicBoolean restInTry = new AtomicBoolean(false);
                return ListUtils.map(statements, (index, statement) -> {
                    if (restInTry.get()) {
                        // Rest of the statements have ended up in the try block
                        return null;
                    }

                    if (statement instanceof J.MethodInvocation && MOCKITO_WHEN.matches(((J.MethodInvocation) statement).getSelect())) {
                        J.MethodInvocation when = (J.MethodInvocation) ((J.MethodInvocation) statement).getSelect();
                        if (when != null && when.getArguments().get(0) instanceof J.MethodInvocation) {
                            J.MethodInvocation whenArg = (J.MethodInvocation) when.getArguments().get(0);
                            if (whenArg.getMethodType() != null && whenArg.getMethodType().hasFlags(Static)) {
                                String className = getClassName(whenArg);
                                if (className != null) {
                                    restInTry.set(true);
                                    return tryWithMockedStatic(m, statements, index, (J.MethodInvocation) statement, className, whenArg);
                                }
                            }
                        }
                    }
                    return statement;
                });
            }

            private @Nullable String getClassName(J.MethodInvocation whenArg) {
                J.Identifier clazz = null;
                if (whenArg.getSelect() instanceof J.Identifier) {
                    clazz = (J.Identifier) whenArg.getSelect();
                } else if (whenArg.getSelect() instanceof J.FieldAccess && ((J.FieldAccess) whenArg.getSelect()).getTarget() instanceof J.Identifier) {
                    clazz = (J.Identifier) ((J.FieldAccess) whenArg.getSelect()).getTarget();
                }
                return clazz != null && clazz.getType() != null ? clazz.getSimpleName() : null;
            }

            private J.Try tryWithMockedStatic(
                    J.MethodDeclaration m,
                    List<Statement> statements,
                    Integer index,
                    J.MethodInvocation statement,
                    String className,
                    J.MethodInvocation whenArg) {
                Expression thenReturnArg = statement.getArguments().get(0);

                maybeAddImport("org.mockito.MockedStatic", false);
                maybeAddImport("org.mockito.Mockito", "mockStatic");

                J.MethodDeclaration md = JavaTemplate.builder(String.format(
                        "try(MockedStatic<%1$s> %2$s = mockStatic(%1$s.class)) {\n" +
                        "    %2$s.when(() -> #{any()}).thenReturn(#{any()});\n" +
                        "}", className, generateVariableName("mock" + className, updateCursor(m), INCREMENT_NUMBER)))
                        .contextSensitive()
                        .imports("org.mockito.MockedStatic")
                        .staticImports("org.mockito.Mockito.mockStatic")
                        .build()
                        .apply(getCursor(), m.getCoordinates().replaceBody(), whenArg, thenReturnArg);
                J.Try try_ = (J.Try) md.getBody().getStatements().get(0);

                List<Statement> precedingStatements = statements.subList(0, index);
                List<Statement> handledStatements = ListUtils.concat(precedingStatements, try_);
                List<Statement> remainingStatements = statements.subList(index + 1, statements.size());

                List<Statement> newStatements = ListUtils.concatAll(
                        try_.getBody().getStatements(),
                        maybeWrapStatementsInTryWithResourcesMockedStatic(m.withBody(m.getBody().withStatements(handledStatements)), remainingStatements));

                return try_.withBody(try_.getBody().withStatements(newStatements))
                        .withPrefix(statement.getPrefix());
            }
        });
    }
}
