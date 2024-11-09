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
import static java.lang.String.join;
import static java.util.Collections.nCopies;
import java.util.List;

import static java.lang.String.*;
import static java.util.Collections.*;

public class EasyMockVerifyToMockitoVerify extends Recipe {

    private static final MethodMatcher VERIFY_MATCHER = new MethodMatcher("org.easymock.EasyMock verify(..)", true);
    private static final MethodMatcher EASY_MATCHER = new MethodMatcher("org.easymock.EasyMock expect(..)");

    @Override
    public String getDisplayName() {
        return "Replace EasyMock verify calls with Mockito verify calls";
    }

    @Override
    public String getDescription() {
        return "Replace EasyMock.verify(dependency) with individual Mockito.verify(dependency).method() calls based on expected methods.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(VERIFY_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = method;

                maybeAddImport("org.mockito.Mockito", "verify");
                maybeRemoveImport("org.easymock.EasyMock.verify");

                List<J.MethodInvocation> expectedCalls = new ArrayList<>();
                if (md.getBody() != null) {
                    List<Statement> statements = md.getBody().getStatements();
                    int idx = 0;
                    for (Statement statement : statements) {
                        if (statement instanceof J.MethodInvocation) {
                            J.MethodInvocation m = (J.MethodInvocation) statement;
                            if (isExpectInvocation(m)) {
                                expectedCalls.add((J.MethodInvocation) m.getArguments().get(0));
                            } else if (isExpectAndReturnInvocation(m)) {
                                expectedCalls.add((J.MethodInvocation) ((J.MethodInvocation) m.getSelect()).getArguments().get(0));
                            } else if (VERIFY_MATCHER.matches(m)) {
                                for (int i = 0, expectedCallsSize = expectedCalls.size(); i < expectedCallsSize; i++) {
                                    J.MethodInvocation expectedMethod = expectedCalls.get(i);
                                    List<Expression> parameters = expectedMethod.getArguments();
                                    if (parameters.size() == 1 && parameters.get(0) instanceof J.Empty) {
                                        parameters = new ArrayList<>();
                                    }
                                    String anyArgs = join(",", nCopies(parameters.size(), "#{any()}"));
                                    parameters.add(0, expectedMethod.getSelect());
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
                                expectedCalls.clear();
                            }
                        }
                        idx++;
                    }
                }

                return super.visitMethodDeclaration(md, ctx);
            }

            // match: expect(<dep>.someMethod());
            private boolean isExpectInvocation(J.MethodInvocation mi) {
                return EASY_MATCHER.matches(mi) &&
                        mi.getArguments().size() == 1 &&
                        mi.getArguments().get(0) instanceof J.MethodInvocation;
            }

            // match: expect(<dep>.someMethod()).andReturn();
            private boolean isExpectAndReturnInvocation(J.MethodInvocation m) {
                return EASY_MATCHER.matches(m.getSelect()) &&
                        m.getSelect() instanceof J.MethodInvocation &&
                        isExpectInvocation((J.MethodInvocation) m.getSelect());
            }
        });
    }
}
