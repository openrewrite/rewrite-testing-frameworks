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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class PowerMockEasyMockToEasyMock extends Recipe {

    private static final String POWER_MOCK = "org.powermock.api.easymock.PowerMock";

    private static final List<MethodMatcher> CREATE_MOCK = Arrays.asList(
            new MethodMatcher(POWER_MOCK + " createMock(java.lang.Class, ..)"),
            new MethodMatcher(POWER_MOCK + " createNiceMock(java.lang.Class, ..)"),
            new MethodMatcher(POWER_MOCK + " createStrictMock(java.lang.Class, ..)"));
    private static final List<MethodMatcher> REPLAY_VERIFY_RESET = Arrays.asList(
            new MethodMatcher(POWER_MOCK + " replay(..)"),
            new MethodMatcher(POWER_MOCK + " verify(..)"),
            new MethodMatcher(POWER_MOCK + " reset(..)"));

    private static final MethodMatcher EASY_MOCK_VERIFY = new MethodMatcher("org.easymock.EasyMock verify(..)");

    @Getter
    final String displayName = "Replace `PowerMock` EasyMock calls with `EasyMock`";

    @Getter
    final String description = "Replaces `PowerMock.createMock(Type.class)`, `createNiceMock`, `createStrictMock`, " +
            "`replay(mocks)`, `verify(mocks)` and `reset(mocks)` with the same calls on `EasyMock`, which is what " +
            "they delegate to for a mock of a type that EasyMock can mock itself. Calls with a class as an argument, " +
            "which mock static methods, and the creation of a partial mock are left as they are. `verify` of several " +
            "mocks becomes one `verify` for each, as that is what `EasyMockCodeToMockito` migrates.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(POWER_MOCK, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                List<Statement> statements = new ArrayList<>();
                boolean changed = false;
                for (Statement statement : b.getStatements()) {
                    if (statement instanceof J.MethodInvocation && isVerifyOfSeveralMocks((J.MethodInvocation) statement)) {
                        J.MethodInvocation verify = (J.MethodInvocation) statement;
                        for (int i = 0; i < verify.getArguments().size(); i++) {
                            Expression mock = verify.getArguments().get(i);
                            statements.add(verify.withId(Tree.randomId())
                                    .withPrefix(i == 0 ? verify.getPrefix() : verify.getPrefix().withComments(emptyList()))
                                    .withArguments(singletonList(mock.withPrefix(Space.EMPTY))));
                        }
                        changed = true;
                    } else {
                        statements.add(statement);
                    }
                }
                return changed ? b.withStatements(statements) : b;
            }

            private boolean isVerifyOfSeveralMocks(J.MethodInvocation mi) {
                return (REPLAY_VERIFY_RESET.get(1).matches(mi) || EASY_MOCK_VERIFY.matches(mi)) && mi.getArguments().size() > 1 && !takesClass(mi);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (CREATE_MOCK.stream().anyMatch(m -> m.matches(mi)) && mi.getArguments().size() == 1) {
                    return retarget(mi, "createMock(#{any(java.lang.Class)})".replace("createMock", mi.getSimpleName()), ctx);
                }
                if (REPLAY_VERIFY_RESET.stream().anyMatch(m -> m.matches(mi)) && !takesClass(mi)) {
                    StringBuilder arguments = new StringBuilder();
                    for (int i = 0; i < mi.getArguments().size(); i++) {
                        arguments.append(i == 0 ? "" : ", ").append("#{any()}");
                    }
                    return retarget(mi, mi.getSimpleName() + "(" + arguments + ")", ctx);
                }
                return mi;
            }

            private boolean takesClass(J.MethodInvocation mi) {
                // A class, or an array of them, stands for static methods to mock
                return mi.getArguments().stream().anyMatch(a -> a.getType() != null &&
                        TypeUtils.isOfClassType(a.getType(), "java.lang.Class"));
            }

            private J.MethodInvocation retarget(J.MethodInvocation mi, String call, ExecutionContext ctx) {
                maybeRemoveImport(POWER_MOCK);
                maybeAddImport("org.easymock.EasyMock");
                return JavaTemplate.builder("EasyMock." + call)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "easymock"))
                        .imports("org.easymock.EasyMock")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().toArray());
            }
        });
    }
}
