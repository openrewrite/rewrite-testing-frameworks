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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashSet;
import java.util.Set;

public class RemoveUnusedMocksControl extends Recipe {

    private static final String IMOCKS_CONTROL = "org.easymock.IMocksControl";
    private static final MethodMatcher EASYMOCK_CREATE_CONTROL = new MethodMatcher("org.easymock.EasyMock create*Control(..)");
    private static final MethodMatcher SUPPORT_CREATE_CONTROL = new MethodMatcher("org.easymock.EasyMockSupport create*Control(..)", true);

    @Getter
    final String displayName = "Remove unused EasyMock `IMocksControl` variables";

    @Getter
    final String description = "Remove `IMocksControl` fields and local variables, along with their assignments, " +
            "that are only ever assigned a new control from `createControl()`, `createNiceControl()` or `createStrictControl()`. " +
            "Mockito has no control, so once the mocks a control creates and its `replay()` and `verify()` calls are " +
            "migrated, nothing is left that uses it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(IMOCKS_CONTROL, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                Set<String> used = new HashSet<>();
                new JavaIsoVisitor<Set<String>>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> used) {
                        JavaType.Variable variable = identifier.getFieldType();
                        if (variable != null && TypeUtils.isOfClassType(variable.getType(), IMOCKS_CONTROL) &&
                                !isDeclarationOrAssignmentTarget(getCursor())) {
                            used.add(key(variable));
                        }
                        return identifier;
                    }
                }.visit(compilationUnit, used);
                J.CompilationUnit cu = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                        J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                        Object parent = getCursor().getParentTreeCursor().getValue();
                        if (!TypeUtils.isOfClassType(vd.getType(), IMOCKS_CONTROL) ||
                                parent instanceof J.MethodDeclaration || parent instanceof J.Lambda) {
                            return vd;
                        }
                        for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                            if (variable.getVariableType() == null || used.contains(key(variable.getVariableType())) ||
                                    variable.getInitializer() != null && !isNewControl(variable.getInitializer())) {
                                return vd;
                            }
                        }
                        return null;
                    }

                    @Override
                    public J.@Nullable Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                        J.Assignment a = super.visitAssignment(assignment, ctx);
                        JavaType.Variable variable = assignedVariable(a.getVariable());
                        if (variable != null && TypeUtils.isOfClassType(variable.getType(), IMOCKS_CONTROL) &&
                                !used.contains(key(variable)) && isNewControl(a.getAssignment()) &&
                                getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                            return null;
                        }
                        return a;
                    }
                }.visitNonNull(compilationUnit, ctx);
                if (cu != compilationUnit) {
                    maybeRemoveImport(IMOCKS_CONTROL);
                    maybeRemoveImport("org.easymock.EasyMock");
                    maybeRemoveImport("org.easymock.EasyMock.createControl");
                    maybeRemoveImport("org.easymock.EasyMock.createNiceControl");
                    maybeRemoveImport("org.easymock.EasyMock.createStrictControl");
                }
                return cu;
            }
        });
    }

    /**
     * Variables of the same name are told apart by what declares them, such as a method for a local variable.
     */
    private static String key(JavaType.Variable variable) {
        return variable.getName() + " of " + variable.getOwner();
    }

    private static boolean isNewControl(Expression expression) {
        return EASYMOCK_CREATE_CONTROL.matches(expression) || SUPPORT_CREATE_CONTROL.matches(expression);
    }

    private static JavaType.@Nullable Variable assignedVariable(Expression variable) {
        if (variable instanceof J.Identifier) {
            return ((J.Identifier) variable).getFieldType();
        }
        if (variable instanceof J.FieldAccess) {
            return ((J.FieldAccess) variable).getName().getFieldType();
        }
        return null;
    }

    private static boolean isDeclarationOrAssignmentTarget(Cursor cursor) {
        J identifier = cursor.getValue();
        Object parent = cursor.getParentTreeCursor().getValue();
        if (parent instanceof J.VariableDeclarations.NamedVariable) {
            return ((J.VariableDeclarations.NamedVariable) parent).getName() == identifier;
        }
        if (parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier) {
            identifier = (J) parent;
            parent = cursor.getParentTreeCursor().getParentTreeCursor().getValue();
        }
        return parent instanceof J.Assignment && ((J.Assignment) parent).getVariable() == identifier;
    }
}
