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
package org.openrewrite.java.testing.easymock;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.join;
import static java.util.Collections.reverse;
import static java.util.Collections.nCopies;

public class EasyMockExpectLastCallToMockito extends Recipe {

    private static final MethodMatcher EXPECT_LAST_CALL = new MethodMatcher("org.easymock.EasyMock expectLastCall()");
    private static final MethodMatcher EXPECT = new MethodMatcher("org.easymock.EasyMock expect(..)");
    private static final MethodMatcher EXPECTATION_SETTERS = new MethodMatcher("org.easymock.IExpectationSetters *(..)");

    @Getter
    final String displayName = "Replace EasyMock `expectLastCall()` with Mockito `do*().when(mock)` stubbing";

    @Getter
    final String description = "Replace a call recorded on an EasyMock mock and the `expectLastCall()` that follows it. " +
            "`andThrow`, `andReturn`, `andAnswer` and `andDelegateTo` become `doThrow`, `doReturn`, `doAnswer` and " +
            "`doAnswer(delegatesTo(..))` stubbing of that call. A recorded call that only has its call count set, " +
            "with `times`, `once`, `atLeastOnce`, `anyTimes` or `andVoid`, is removed, as Mockito mocks accept any call. " +
            "An `expectLastCall()` that follows an `expect(..)` adds to the expectation that `expect(..)` sets.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(EXPECT_LAST_CALL), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                for (int i = 1; i < b.getStatements().size(); i++) {
                    List<J.MethodInvocation> setters = expectationSetters(b.getStatements().get(i));
                    if (setters == null) {
                        continue;
                    }
                    Statement previous = b.getStatements().get(i - 1);
                    if (isExpectChain(previous) && setters.stream().allMatch(EXPECTATION_SETTERS::matches)) {
                        // expect(mock.call()).andReturn(1); expectLastCall().andThrow(e); is expect(mock.call()).andReturn(1).andThrow(e);
                        Expression chain = ((J.MethodInvocation) previous).withPrefix(Space.EMPTY);
                        for (J.MethodInvocation setter : setters) {
                            chain = setter.withPrefix(Space.EMPTY).withSelect(chain);
                        }
                        J.MethodInvocation merged = (J.MethodInvocation) chain.withPrefix(previous.getPrefix());
                        int previousIndex = i - 1;
                        int expectLastCallIndex = i;
                        b = b.withStatements(ListUtils.map(b.getStatements(),
                                (j, s) -> j == previousIndex ? merged : j == expectLastCallIndex ? null : s));
                        removeExplicitExpectLastCallImport();
                        i--;
                        continue;
                    }
                    J.MethodInvocation recorded = recordedCall(previous);
                    if (recorded == null) {
                        continue;
                    }

                    StringBuilder stubbing = new StringBuilder();
                    List<Object> parameters = new ArrayList<>();
                    Set<String> staticImports = new LinkedHashSet<>();
                    boolean understood = true;
                    for (J.MethodInvocation setter : setters) {
                        String stubber;
                        switch (setter.getSimpleName()) {
                            case "andThrow":
                            case "andStubThrow":
                                stubber = "doThrow(#{any(java.lang.Throwable)})";
                                break;
                            case "andReturn":
                            case "andStubReturn":
                                stubber = "doReturn(#{any()})";
                                break;
                            case "andAnswer":
                            case "andStubAnswer":
                                stubber = "doAnswer(#{any()})";
                                break;
                            case "andDelegateTo":
                            case "andStubDelegateTo":
                                stubber = "doAnswer(delegatesTo(#{any()}))";
                                staticImports.add("org.mockito.AdditionalAnswers.delegatesTo");
                                break;
                            case "andVoid":
                            case "asStub":
                            case "times":
                            case "once":
                            case "atLeastOnce":
                            case "anyTimes":
                                continue;
                            default:
                                understood = false;
                                continue;
                        }
                        if (stubbing.length() == 0) {
                            staticImports.add("org.mockito.Mockito." + stubber.substring(0, stubber.indexOf('(')));
                        } else {
                            stubbing.append('.');
                        }
                        stubbing.append(stubber);
                        parameters.add(setter.getArguments().get(0));
                    }
                    if (!understood) {
                        continue;
                    }

                    removeExplicitExpectLastCallImport();
                    int expectLastCallIndex = i;
                    if (stubbing.length() == 0) {
                        // Only the call count was set, which a Mockito mock has no need for
                        int recordedIndex = i - 1;
                        b = b.withStatements(ListUtils.map(b.getStatements(),
                                (j, s) -> j == recordedIndex || j == expectLastCallIndex ? null : s));
                        // the statement after the removed pair is now at i - 1, and was not preceded by a recorded call
                        i--;
                        continue;
                    }

                    List<Expression> recordedArguments = new ArrayList<>(recorded.getArguments());
                    if (recordedArguments.size() == 1 && recordedArguments.get(0) instanceof J.Empty) {
                        recordedArguments.clear();
                    }
                    parameters.add(recorded.getSelect());
                    parameters.addAll(recordedArguments);
                    String code = stubbing + ".when(#{any()})." + recorded.getSimpleName() +
                            "(" + join(", ", nCopies(recordedArguments.size(), "#{any()}")) + ")";
                    b = JavaTemplate.builder(code)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                            .staticImports(staticImports.toArray(new String[0]))
                            .build()
                            .apply(updateCursor(b), recorded.getCoordinates().replace(), parameters.toArray());
                    b = b.withStatements(ListUtils.map(b.getStatements(), (j, s) -> j == expectLastCallIndex ? null : s));
                    for (String staticImport : staticImports) {
                        int lastDot = staticImport.lastIndexOf('.');
                        maybeAddImport(staticImport.substring(0, lastDot), staticImport.substring(lastDot + 1), false);
                    }
                }
                return b;
            }

            /**
             * A star import is left to the type change that follows, so it is not unfolded here.
             */
            private void removeExplicitExpectLastCallImport() {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu != null) {
                    for (J.Import anImport : cu.getImports()) {
                        if (anImport.isStatic() && "expectLastCall".equals(anImport.getQualid().getSimpleName()) &&
                                "org.easymock.EasyMock".equals(anImport.getTypeName())) {
                            maybeRemoveImport("org.easymock.EasyMock.expectLastCall");
                            return;
                        }
                    }
                }
            }

            /**
             * @return The methods called on `expectLastCall()` in order, when the statement is such a chain.
             */
            private @Nullable List<J.MethodInvocation> expectationSetters(Statement statement) {
                List<J.MethodInvocation> setters = new ArrayList<>();
                J current = statement;
                while (current instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) current;
                    if (EXPECT_LAST_CALL.matches(mi)) {
                        reverse(setters);
                        return setters;
                    }
                    if (!EXPECTATION_SETTERS.matches(mi)) {
                        return null;
                    }
                    setters.add(mi);
                    current = mi.getSelect();
                }
                return null;
            }

            private boolean isExpectChain(Statement statement) {
                J current = statement;
                while (current instanceof J.MethodInvocation && EXPECTATION_SETTERS.matches((J.MethodInvocation) current)) {
                    current = ((J.MethodInvocation) current).getSelect();
                }
                return current instanceof J.MethodInvocation && EXPECT.matches((J.MethodInvocation) current);
            }

            /**
             * @return The statement as the call recorded on a mock, when it can be one.
             */
            private J.@Nullable MethodInvocation recordedCall(Statement statement) {
                if (!(statement instanceof J.MethodInvocation)) {
                    return null;
                }
                J.MethodInvocation mi = (J.MethodInvocation) statement;
                JavaType.Method methodType = mi.getMethodType();
                Expression select = mi.getSelect();
                if (select == null || methodType == null || methodType.hasFlags(Flag.Static) ||
                        select instanceof J.Identifier && ("this".equals(((J.Identifier) select).getSimpleName()) ||
                                                           "super".equals(((J.Identifier) select).getSimpleName())) ||
                        methodType.getDeclaringType().getPackageName().startsWith("org.easymock")) {
                    return null;
                }
                return mi;
            }
        });
    }
}
