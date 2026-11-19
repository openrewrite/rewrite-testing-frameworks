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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class PowerMockitoDoStubbingTargetToMockito extends Recipe {

    /**
     * PowerMock's stubber alone accepts a member name or a class, as in `doReturn(x).when(mock, "method", args)`
     * and `doReturn(x).when(Type.class)`. Mockito's `Stubber` has neither overload, so retargeting the `doX`
     * that starts such a chain would leave one that does not compile or does not stub what it did.
     */
    private static final MethodMatcher STUBBER_WHEN =
            new MethodMatcher("org.powermock.api.mockito.expectation.PowerMockitoStubber when(..)");
    private static final MethodMatcher POWER_MOCKITO_DO = new MethodMatcher("org.powermock.api.mockito.PowerMockito do*(..)");
    private static final JavaType.FullyQualified MOCKITO = JavaType.ShallowClass.build("org.mockito.Mockito");

    @Getter
    final String displayName = "Replace `PowerMockito.doX()` with `Mockito.doX()`";

    @Getter
    final String description = "Retargets `PowerMockito`'s `doReturn`, `doThrow`, `doAnswer`, `doNothing` and " +
            "`doCallRealMethod` to `Mockito`, except for chains that still stub a member by name or a class. There " +
            "`Stubber.when(Object, String, ...)` and `Stubber.when(Class)` have no Mockito counterpart, so that " +
            "chain has to stay on PowerMock to keep compiling. The other chains in the same file are retargeted.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(POWER_MOCKITO_DO), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!POWER_MOCKITO_DO.matches(mi) || mi.getMethodType() == null || chainStubsMemberOrClass()) {
                    return mi;
                }
                maybeRemoveImport(mi.getMethodType().getDeclaringType());
                if (mi.getSelect() == null) {
                    maybeAddImport(MOCKITO.getFullyQualifiedName(), mi.getSimpleName());
                } else {
                    maybeAddImport(MOCKITO.getFullyQualifiedName());
                    mi = mi.withSelect(new J.Identifier(randomId(), mi.getSelect().getPrefix(), Markers.EMPTY, emptyList(), MOCKITO.getClassName(), MOCKITO, null));
                }
                JavaType.Method type = mi.getMethodType().withDeclaringType(MOCKITO);
                return mi.withMethodType(type).withName(mi.getName().withType(type));
            }

            private boolean chainStubsMemberOrClass() {
                Object inner = getCursor().getValue();
                for (Cursor parent = getCursor().getParentTreeCursor(); parent.getValue() instanceof J.MethodInvocation; parent = parent.getParentTreeCursor()) {
                    J.MethodInvocation outer = parent.getValue();
                    if (outer.getSelect() != inner) {
                        return false;
                    }
                    if (STUBBER_WHEN.matches(outer) && stubsMemberOrClass(outer)) {
                        return true;
                    }
                    inner = outer;
                }
                return false;
            }

            /**
             * `when(Object, String, ..)`, and `when(Class)` that is followed by a call of the static method.
             * The single-argument `when(mock)` that {@link PowerMockitoDoStubbingToMockito} produces keeps the
             * same declaring type, so the overloads are told apart by their arguments.
             */
            private boolean stubsMemberOrClass(J.MethodInvocation when) {
                List<Expression> arguments = when.getArguments();
                return arguments.size() >= 2 || (arguments.size() == 1 && arguments.get(0) instanceof J.FieldAccess &&
                                                 "class".equals(((J.FieldAccess) arguments.get(0)).getSimpleName()));
            }
        });
    }
}
