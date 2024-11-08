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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /*
    public void testServiceMethod() {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.performAction());
                      expect(dependency.performAction2()).andReturn("Mocked Result");
                      replay(dependency);
                      verify(dependency);
                      //1
                      //2
                      expect(dependency.performAction3()).andReturn("Mocked Result");
                      verify(dependency);
                      //1
                  }

                  // dependency.performAction()
                  // dependency.performAction2()
     */

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(VERIFY_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = method;

                // if-statement

                List<J.MethodInvocation> expectedCalls = new ArrayList<>();
                if (md.getBody() != null) {
                    for (Statement statement : md.getBody().getStatements()) {
                        if (statement instanceof J.MethodInvocation) {
                            J.MethodInvocation m = (J.MethodInvocation) statement;
                            if (isExpectInvocation(m)) {
                                expectedCalls.add((J.MethodInvocation) m.getArguments().get(0));
                            } else if (isExpectAndReturnInvocation(m)) {
                                expectedCalls.add((J.MethodInvocation) ((J.MethodInvocation) m.getSelect()).getArguments().get(0));
                            } else if (VERIFY_MATCHER.matches(m)) {
                                StringBuilder verifyCode = new StringBuilder();
                                for (J.MethodInvocation expectedMethod : expectedCalls) {
                                    //Statement s = JavaTemplate.builder("verify(#{any()}).#{any()}")
                                    md = JavaTemplate.builder("verify(#{any()})")
                                            .contextSensitive()
                                            //.doBeforeParseTemplate(System.out::println)
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                                            .staticImports("org.mockito.Mockito.verify")
                                            .build()
                                            .apply(getCursor(), statement.getCoordinates().after(), expectedMethod.getSelect());
                                }
                                expectedCalls.clear();
                            }
                        }


                    }
                }

                return super.visitMethodDeclaration(md, ctx);
            }

            /*@Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!VERIFY_MATCHER.matches(mi)) {
                    return super.visitMethodInvocation(mi, ctx);
                }

                maybeAddImport("org.mockito.Mockito", "verify");
                // maybeRemoveImport("org.junit.jupiter.api.Assertions");

                System.out.println("Verify: " + mi);
                List<J.MethodInvocation> expectedCalls = new ArrayList<>();
                for (Statement statement : getCursor().firstEnclosingOrThrow(J.Block.class).getStatements()) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) statement;
                        if (m.equals(mi)) {
                            break;
                        } else if (isExpectInvocation(m)) {
                            expectedCalls.add((J.MethodInvocation) m.getArguments().get(0));
                        } else if (isExpectAndReturnInvocation(m)) {
                            expectedCalls.add((J.MethodInvocation) ((J.MethodInvocation) m.getSelect()).getArguments().get(0));
                        } else if (VERIFY_MATCHER.matches(m)) { // another verify, clear list
                            expectedCalls.clear();
                        }
                    }
                }
                System.out.println("- EXPECTED REPLACEMENT - ");
                System.out.println(expectedCalls);



                // Retrieve the dependency object being verified
                Expression dependency = method.getArguments().get(0);

                StringBuilder verifyCode = new StringBuilder();
                for (J.MethodInvocation expectedMethod : expectedCalls) {
                    String methodName = expectedMethod.getSimpleName();
                    List<Expression> args = expectedMethod.getTypeParameters();
                    verifyCode.append("verify(").append(dependency).append(").").append(methodName).append("(");
                    if (args != null) {
                        for (int i = 0; i < args.size(); i++) {
                            verifyCode.append(i == 0 ? "" : ", ").append(args.get(i));
                        }
                    }
                    verifyCode.append(");\n");
                }
                String template = verifyCode.toString();

                return JavaTemplate.builder(template)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .staticImports("org.mockito.Mockito.verify")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace());
            }*/

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
