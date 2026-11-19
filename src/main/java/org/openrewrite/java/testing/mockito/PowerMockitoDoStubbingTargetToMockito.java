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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PowerMockitoDoStubbingTargetToMockito extends Recipe {

    /**
     * PowerMock's stubber alone accepts a member name or a class, as in `doReturn(x).when(mock, "method", args)`
     * and `doReturn(x).when(Type.class)`. Mockito's `Stubber` has neither overload, so retargeting the `doX`
     * that starts such a chain would leave one that does not compile or does not stub what it did.
     */
    private static final MethodMatcher STUBBER_WHEN =
            new MethodMatcher("org.powermock.api.mockito.expectation.PowerMockitoStubber when(..)");
    private static final MethodMatcher POWER_MOCKITO_DO = new MethodMatcher("org.powermock.api.mockito.PowerMockito do*(..)");

    @Getter
    final String displayName = "Replace `PowerMockito.doX()` with `Mockito.doX()`";

    @Getter
    final String description = "Retargets `PowerMockito`'s `doReturn`, `doThrow`, `doAnswer`, `doNothing` and " +
            "`doCallRealMethod` to `Mockito`, except for chains that still stub a member by name or a class. There " +
            "`Stubber.when(Object, String, ...)` and `Stubber.when(Class)` have no Mockito counterpart, so that " +
            "chain has to stay on PowerMock to keep compiling. The other chains in the same file are retargeted.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> retarget = new ChangeMethodTargetToStatic(
                "org.powermock.api.mockito.PowerMockito do*(..)", "org.mockito.Mockito", null, null).getVisitor();
        return Preconditions.check(new UsesMethod<>(POWER_MOCKITO_DO), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                return tree instanceof JavaSourceFile ? retargetChains((JavaSourceFile) tree, ctx) : (J) tree;
            }

            private JavaSourceFile retargetChains(JavaSourceFile cu, ExecutionContext ctx) {
                Set<UUID> staying = new HashSet<>();
                new JavaIsoVisitor<Set<UUID>>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Set<UUID> p) {
                        if (STUBBER_WHEN.matches(method) && stubsMemberOrClass(method)) {
                            for (Expression select = method.getSelect(); select instanceof J.MethodInvocation;
                                 select = ((J.MethodInvocation) select).getSelect()) {
                                p.add(((J.MethodInvocation) select).getId());
                            }
                        }
                        return super.visitMethodInvocation(method, p);
                    }
                }.visit(cu, staying);
                if (staying.isEmpty()) {
                    return (JavaSourceFile) retarget.visitNonNull(cu, ctx);
                }

                // Those chains are hidden from the retargeting for its duration, by taking their type away
                Map<UUID, JavaType.Method> hidden = new HashMap<>();
                JavaSourceFile hiding = (JavaSourceFile) new JavaIsoVisitor<Map<UUID, JavaType.Method>>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Map<UUID, JavaType.Method> p) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, p);
                        if (staying.contains(mi.getId()) && mi.getMethodType() != null) {
                            p.put(mi.getId(), mi.getMethodType());
                            return mi.withMethodType(null);
                        }
                        return mi;
                    }
                }.visitNonNull(cu, hidden);
                JavaSourceFile retargeted = (JavaSourceFile) retarget.visitNonNull(hiding, ctx);
                if (retargeted == hiding) {
                    return cu;
                }
                return (JavaSourceFile) new JavaIsoVisitor<Map<UUID, JavaType.Method>>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Map<UUID, JavaType.Method> p) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, p);
                        JavaType.Method type = p.get(mi.getId());
                        return type == null ? mi : mi.withMethodType(type);
                    }
                }.visitNonNull(retargeted, hidden);
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
