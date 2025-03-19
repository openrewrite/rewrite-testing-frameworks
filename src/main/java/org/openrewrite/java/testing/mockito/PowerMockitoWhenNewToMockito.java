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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockitoWhenNewToMockito extends Recipe {

    private static final MethodMatcher PM_WHEN_NEW = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(..)");
    private static final MethodMatcher WITH_NO_ARGUMENTS = new MethodMatcher("*..* withNoArguments(..)");
    private static final MethodMatcher THEN_RETURN = new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)");
    private static final MethodMatcher MOCKITO_MOCK = new MethodMatcher("org.mockito.Mockito mock(..)");
    private static final MethodMatcher PM_MOCK = new MethodMatcher("org.powermock.api.mockito.PowerMockito mock(..)");

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
        return Preconditions.check(new UsesMethod<>(PM_WHEN_NEW), new JavaVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (THEN_RETURN.matches(method) && method.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation select1 = (J.MethodInvocation) method.getSelect();
                    if (WITH_NO_ARGUMENTS.matches(select1) && select1.getSelect() instanceof J.MethodInvocation) {
                        J.MethodInvocation select2 = (J.MethodInvocation) select1.getSelect();
                        if (PM_WHEN_NEW.matches(select2) && select2.getArguments().size() == 1) {
                            maybeRemoveImport("org.powermock.api.mockito.PowerMockito");

                            Cursor containingMethod = getCursor().dropParentUntil(x -> x instanceof J.MethodDeclaration);
                            Expression argument = select2.getArguments().get(0);
                            if (argument instanceof J.FieldAccess) {
                                ArrayList<J.FieldAccess> listOfMocks = containingMethod.getMessage("POWERMOCKITO_WHEN_NEW_REPLACED", new ArrayList<J.FieldAccess>());
                                listOfMocks.add((J.FieldAccess) argument);
                                containingMethod.putMessage("POWERMOCKITO_WHEN_NEW_REPLACED", listOfMocks);
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
                List<J.FieldAccess> mockArguments = getCursor().getMessage("POWERMOCKITO_WHEN_NEW_REPLACED");
                if (mockArguments != null && ret instanceof J.MethodDeclaration) {
                    doAfterVisit(removeMockUsagesVisitor(mockArguments, method.toString()));

                    J.MethodDeclaration retM = (J.MethodDeclaration) ret;

                    // onlyIfReferenced=false as `maybeAddImport` doesn't seem to find the type referred to in a try statement
                    // see https://github.com/openrewrite/rewrite/issues/5187
                    maybeAddImport("org.mockito.MockedConstruction", false);

                    for (J.FieldAccess mockArgument: mockArguments) {
                        String mockedClassName = ((J.Identifier) mockArgument.getTarget()).getSimpleName();
                        String variableNameForMock = generateVariableName("mock" + mockedClassName, updateCursor(ret), INCREMENT_NUMBER);
                        J.MethodDeclaration appliedTemplate = JavaTemplate.builder(String.format("try (MockedConstruction<%s> %s = Mockito.mockConstruction(%s.class)) { } ", mockedClassName, variableNameForMock, mockedClassName))
                                .contextSensitive()
                                .imports("org.mockito.MockedConstruction")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core"))
                                .build()
                                .apply(getCursor(), method.getCoordinates().replaceBody());
                        J.Try try_ = (J.Try) appliedTemplate.getBody().getStatements().get(0);
                        retM = appliedTemplate.withBody(appliedTemplate.getBody().withStatements(Collections.singletonList(try_.withBody(retM.getBody()))));
                    }
                    return autoFormat(retM, ctx);
                }
                return ret;
            }

            private JavaIsoVisitor<ExecutionContext> removeMockUsagesVisitor(List<J.FieldAccess> mockArguments, String inMethodSignature) {
                Set<String> mockedClassNames = mockArguments.stream().map(fa -> ((J.Identifier) fa.getTarget()).getSimpleName()).collect(Collectors.toSet());
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations ret = super.visitVariableDeclarations(multiVariable, ctx);
                        Cursor containingMethod = getCursor().dropParentUntil(x -> x instanceof J.MethodDeclaration);
                        if (!inMethodSignature.equals(containingMethod.getValue().toString())) {
                            return ret;
                        }
                        List<J.VariableDeclarations.NamedVariable> variables = ListUtils.filter(ret.getVariables(), varr -> {
                            // The original code likely contains PowerMockito.mock(), but that gets converted to Mockito.mock() by other subrecipes of
                            // org.openrewrite.java.testing.mockito.ReplacePowerMockito, so we need to check both.
                            if (varr.getInitializer() instanceof J.MethodInvocation && (MOCKITO_MOCK.matches(varr.getInitializer()) || PM_MOCK.matches(varr.getInitializer()))) {
                                J.MethodInvocation initializer = (J.MethodInvocation) varr.getInitializer();
                                if (initializer.getArguments().size() == 1 && initializer.getArguments().get(0) instanceof J.FieldAccess) {
                                    J.FieldAccess classReference = (J.FieldAccess) initializer.getArguments().get(0);
                                    String mockedClassName = ((J.Identifier) classReference.getTarget()).getSimpleName();
                                    if (mockedClassNames.contains(mockedClassName)) {
                                        return false;
                                    }
                                }
                            }
                            return true;
                        });
                        if (variables.isEmpty()) {
                            return null;
                        } else {
                            return ret.withVariables(variables);
                        }
                    }
                };
            }
        });
    }
}
