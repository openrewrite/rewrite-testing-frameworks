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
package org.openrewrite.java.testing.easymock;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.join;
import static java.util.Collections.nCopies;

public class EasyMockVerifyToMockitoVerify extends Recipe {

    private static final MethodMatcher VERIFY_MATCHER = new MethodMatcher("org.easymock.EasyMock verify(..)", true);
    private static final MethodMatcher EASY_MATCHER = new MethodMatcher("org.easymock.EasyMock expect(..)");

    @Override
    public String getDisplayName() {
        return "Replace EasyMock `verify` calls with Mockito `verify` calls";
    }

    @Override
    public String getDescription() {
        return "Replace `EasyMock.verify(dependency)` with individual `Mockito.verify(dependency).method()` calls based on expected methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(VERIFY_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (md.getBody() == null) {
                    return md;
                }

                maybeRemoveImport("org.easymock.EasyMock.verify");
                maybeAddImport("org.mockito.Mockito", "verify");

                int idx = 0;
                for (Statement statement : md.getBody().getStatements()) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) statement;
                        if (VERIFY_MATCHER.matches(m) && m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Identifier) {
                            J.Identifier dependency = (J.Identifier) m.getArguments().get(0);
                            List<Statement> statementsAboveVerify = md.getBody().getStatements().subList(0, idx);
                            List<J.MethodInvocation> expectedCalls = getExpectedCalls(dependency, statementsAboveVerify);

                            for (int i = 0, expectedCallsSize = expectedCalls.size(); i < expectedCallsSize; i++) {
                                J.MethodInvocation expectedMethod = expectedCalls.get(i);
                                List<Expression> parameters = expectedMethod.getArguments();
                                if (parameters.size() == 1 && parameters.get(0) instanceof J.Empty) {
                                    parameters.clear();
                                }
                                String anyArgs = join(",", nCopies(parameters.size(), "#{any()}"));
                                parameters.add(0, dependency);
                                Statement currStatement = md.getBody().getStatements().get(idx);
                                JavaCoordinates coordinates = i == 0 ? currStatement.getCoordinates().replace() : currStatement.getCoordinates().after();
                                md = JavaTemplate.builder("verify(#{any()})." + expectedMethod.getSimpleName() + "(" + anyArgs + ")")
                                        .contextSensitive()
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                                        .staticImports("org.mockito.Mockito.verify")
                                        .build()
                                        .apply(updateCursor(md), coordinates, parameters.toArray());
                                if (i != 0) {
                                    idx++;
                                }
                            }
                        }
                    }
                    idx++;
                }

                return md;
            }

            private List<J.MethodInvocation> getExpectedCalls(J.Identifier dependency, List<Statement> statementsAboveVerify) {
                List<J.MethodInvocation> expectedCalls = new ArrayList<>();
                for (Statement statement : statementsAboveVerify) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) statement;
                        if (isExpectInvocation(mi, dependency)) {
                            expectedCalls.add((J.MethodInvocation) mi.getArguments().get(0));
                        } else if (isExpectAndReturnInvocation(mi, dependency)) {
                            expectedCalls.add((J.MethodInvocation) ((J.MethodInvocation) mi.getSelect()).getArguments().get(0));
                        }
                    }
                }
                return expectedCalls;
            }

            // match: expect(<dep>.someMethod());
            private boolean isExpectInvocation(J.MethodInvocation mi, J.Identifier dependency) {
                return EASY_MATCHER.matches(mi) &&
                        mi.getArguments().size() == 1 &&
                        mi.getArguments().get(0) instanceof J.MethodInvocation &&
                        ((J.MethodInvocation) mi.getArguments().get(0)).getSelect() instanceof J.Identifier &&
                        dependency.getSimpleName().equals(((J.Identifier) ((J.MethodInvocation) mi.getArguments().get(0)).getSelect()).getSimpleName());
            }

            // match: expect(<dep>.someMethod()).andReturn();
            private boolean isExpectAndReturnInvocation(J.MethodInvocation m, J.Identifier dependency) {
                return EASY_MATCHER.matches(m.getSelect()) &&
                        m.getSelect() instanceof J.MethodInvocation &&
                        isExpectInvocation((J.MethodInvocation) m.getSelect(), dependency);
            }
        });
    }
}
