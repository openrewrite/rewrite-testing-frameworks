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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

public class PowerMockitoDoStubbingTargetToMockito extends Recipe {

    /**
     * PowerMock's stubber alone accepts a member name, as in `doReturn(x).when(mock, "method", args)`.
     * Mockito's `Stubber` has no such overload, so retargeting the `doX` that precedes one would leave
     * a chain that does not compile.
     */
    private static final MethodMatcher STUBBER_WHEN =
            new MethodMatcher("org.powermock.api.mockito.expectation.PowerMockitoStubber when(..)");

    @Getter
    final String displayName = "Replace `PowerMockito.doX()` with `Mockito.doX()`";

    @Getter
    final String description = "Retargets `PowerMockito`'s `doReturn`, `doThrow`, `doAnswer` and `doNothing` to " +
            "`Mockito`, except in files that still stub a member by name. There `Stubber.when(Object, String, ...)` " +
            "has no Mockito counterpart, so the whole chain has to stay on PowerMock to keep compiling.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.not(stubsAMemberByName()),
                new ChangeMethodTargetToStatic("org.powermock.api.mockito.PowerMockito do*(..)",
                        "org.mockito.Mockito", null, null).getVisitor());
    }

    /**
     * Matches files calling the stubber's by-name overload. The single-argument `when(mock)` that
     * {@link PowerMockitoDoStubbingToMockito} produces keeps the same declaring type, so the overloads are
     * told apart by argument count rather than by matcher.
     */
    private static TreeVisitor<?, ExecutionContext> stubsAMemberByName() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (STUBBER_WHEN.matches(method) && method.getArguments().size() >= 2) {
                    return SearchResult.found(method);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}
