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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.HashSet;
import java.util.Set;

public class RemoveDoNothingForDefaultMocks extends Recipe {

    @Getter
    final String displayName = "Remove `doNothing()` for void methods on `@Mock` fields";

    @Getter
    final String description = "Remove unnecessary `doNothing()` stubbings for void methods on `@Mock` fields. " +
            "Mockito mocks already do nothing for void methods by default, making these stubbings redundant " +
            "and triggering strict stubbing violations in Mockito 3+.";

    private static final MethodMatcher DO_NOTHING_MATCHER = new MethodMatcher("org.mockito.Mockito doNothing()", false);
    private static final MethodMatcher STUBBER_WHEN_MATCHER = new MethodMatcher("org.mockito.stubbing.Stubber when(..)", false);
    private static final AnnotationMatcher MOCK_ANNOTATION_MATCHER = new AnnotationMatcher("@org.mockito.Mock");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(DO_NOTHING_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        Set<String> mockFieldNames = new HashSet<>();
                        for (Statement stmt : classDecl.getBody().getStatements()) {
                            if (stmt instanceof J.VariableDeclarations) {
                                J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                                if (vd.getLeadingAnnotations().stream().anyMatch(MOCK_ANNOTATION_MATCHER::matches)) {
                                    for (J.VariableDeclarations.NamedVariable var : vd.getVariables()) {
                                        mockFieldNames.add(var.getSimpleName());
                                    }
                                }
                            }
                        }
                        getCursor().putMessage("mockFieldNames", mockFieldNames);
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (mi != null && isDoNothingOnMockField(mi)) {
                            maybeRemoveImport("org.mockito.Mockito.doNothing");
                            return null;
                        }
                        return mi;
                    }

                    private boolean isDoNothingOnMockField(J.MethodInvocation mi) {
                        // Pattern: doNothing().when(mock).someVoidMethod(args)
                        if (!(mi.getSelect() instanceof J.MethodInvocation)) {
                            return false;
                        }
                        J.MethodInvocation whenCall = (J.MethodInvocation) mi.getSelect();
                        if (!STUBBER_WHEN_MATCHER.matches(whenCall)) {
                            return false;
                        }
                        // Ensure doNothing() is standalone (not chained after doThrow() etc.)
                        if (!(whenCall.getSelect() instanceof J.MethodInvocation)) {
                            return false;
                        }
                        J.MethodInvocation doNothingCall = (J.MethodInvocation) whenCall.getSelect();
                        if (!DO_NOTHING_MATCHER.matches(doNothingCall) || doNothingCall.getSelect() != null) {
                            return false;
                        }
                        // Check that the when() argument references a @Mock field
                        if (whenCall.getArguments().isEmpty() || !(whenCall.getArguments().get(0) instanceof J.Identifier)) {
                            return false;
                        }
                        String mockName = ((J.Identifier) whenCall.getArguments().get(0)).getSimpleName();
                        Set<String> mockFieldNames = getCursor().getNearestMessage("mockFieldNames");
                        return mockFieldNames != null && mockFieldNames.contains(mockName);
                    }
                }
        );
    }
}
