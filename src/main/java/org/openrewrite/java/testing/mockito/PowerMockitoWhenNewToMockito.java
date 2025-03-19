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
package org.openrewrite.java.testing.mockito;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Collections;

public class PowerMockitoWhenNewToMockito extends Recipe {

    private static final MethodMatcher PM_WHEN_NEW = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(..)");
    private static final MethodMatcher WITH_NO_ARGUMENTS = new MethodMatcher("*..* withNoArguments(..)");
    private static final MethodMatcher THEN_RETURN = new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)");

    @Override
    public String getDisplayName() {
        return "Replace `PowerMockito.whenNew` with Mockito counterpart";
    }

    @Override
    public String getDescription() {
        return "Replaces `PowerMockito.whenNew` calls with respective `Mockito.whenConstructed` calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(PM_WHEN_NEW),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (THEN_RETURN.matches(method) && method.getSelect() instanceof J.MethodInvocation) {
                            J.MethodInvocation select1 = (J.MethodInvocation) method.getSelect();
                            if (WITH_NO_ARGUMENTS.matches(select1) && select1.getSelect() instanceof J.MethodInvocation) {
                                J.MethodInvocation select2 = (J.MethodInvocation) select1.getSelect();
                                if (PM_WHEN_NEW.matches(select2) && select2.getArguments().size() == 1) {
                                    maybeRemoveImport("org.powermock.api.mockito.PowerMockito");

                                    Cursor c = getCursor().dropParentUntil(c -> c instanceof J.MethodDeclaration);
                                    Expression argument = select2.getArguments().get(0);
                                    if (c != null && argument instanceof J.FieldAccess) {
                                        c.putMessage("POWERMOCKITO_WHEN_NEW_REPLACED", (J.FieldAccess) argument);
                                        return null;
                                    }
                                }
                            }
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }

                    @Override
                    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J ret = super.visitMethodDeclaration(method, ctx);
                        J.FieldAccess mockArgument = getCursor().getMessage("POWERMOCKITO_WHEN_NEW_REPLACED");
                        if (mockArgument != null && ret instanceof J.MethodDeclaration) {
                            J.MethodDeclaration retM = (J.MethodDeclaration) ret;
                            J.Block originalBody = retM.getBody();

                            // onlyIfReferenced=false as `maybeAddImport` doesn't seem to find the type referred to in a try statement
                            // see https://github.com/openrewrite/rewrite/issues/5187
                            maybeAddImport("org.mockito.MockedConstruction", false);

                            String mockedClassName = ((J.Identifier) mockArgument.getTarget()).getSimpleName();
                            String variableNameForMock = VariableNameUtils.generateVariableName("mock" + mockedClassName, getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
                            JavaTemplate template = JavaTemplate.builder(String.format("try (MockedConstruction<%s> %s = Mockito.mockConstruction(%s.class)) { } ", mockedClassName, variableNameForMock, mockedClassName))
                                    .contextSensitive()
                                    .build();
                            // For some reason the getCoordinates().replace() didn't work. Thus using the trick of `.firstStatement()` and then removing the extra statements
                            J.MethodDeclaration applied = template.apply(updateCursor(ret), method.getBody().getCoordinates().firstStatement());
                            J.Try tryy = (J.Try) applied.getBody().getStatements().get(0);
                            return autoFormat(applied.withBody(applied.getBody().withStatements(Collections.singletonList(tryy.withBody(originalBody)))), ctx);
                        }
                        return ret;
                    }
                });
    }
}
