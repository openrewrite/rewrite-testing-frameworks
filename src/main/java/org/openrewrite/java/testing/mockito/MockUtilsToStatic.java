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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In Mockito 1 you use a code snippet like:
 * <p>
 * new MockUtil().isMock(foo);
 * <p>
 * In Mockito 2+ this class now has a private constructor and only exposes static methods:
 * <p>
 * MockUtil.isMock(foo);
 * <p>
 * This recipe makes a best-effort attempt to remove MockUtil instances, but if someone did something unexpected like
 * subclassing MockUtils that will not be handled and will have to be hand-remediated.
 */
public class MockUtilsToStatic extends Recipe {

    @Getter
    final String displayName = "Use static form of Mockito `MockUtil`";

    @Getter
    final String description = "Best-effort attempt to remove Mockito `MockUtil` instances.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.mockito.internal.util.MockUtil", false), new MockUtilsToStaticVisitor());
    }

    public static class MockUtilsToStaticVisitor extends JavaVisitor<ExecutionContext> {
        private static final String MOCK_UTIL = "org.mockito.internal.util.MockUtil";
        private static final String MOCK_UTIL_METHODS = MOCK_UTIL + " *(..)";
        private static final MethodMatcher METHOD_MATCHER = new MethodMatcher(MOCK_UTIL + " <constructor>()");
        private static final MethodMatcher MIGRATED_METHOD_MATCHER = new MethodMatcher(MOCK_UTIL_METHODS);
        private final ChangeMethodTargetToStatic changeMethodTargetToStatic = new ChangeMethodTargetToStatic(MOCK_UTIL_METHODS, MOCK_UTIL, null, null, false);

        @Override
        public J visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
            J.CompilationUnit cu = (J.CompilationUnit) super.visitCompilationUnit(compilationUnit, ctx);
            return (J.CompilationUnit) changeMethodTargetToStatic.getVisitor().visitNonNull(cu, ctx);
        }

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
            // Uses of a visible field from another source file are not analysed
            J.CompilationUnit scope = getCursor().firstEnclosing(J.CompilationUnit.class);
            if (scope == null) {
                return vd;
            }

            List<JRightPadded<J.VariableDeclarations.NamedVariable>> original = vd.getPadding().getVariables();
            List<JRightPadded<J.VariableDeclarations.NamedVariable>> variables =
                    ListUtils.map(original, v -> isObsoleteMockUtilInstance(v.getElement(), scope) ? null : v);
            if (variables.size() == original.size()) {
                return vd;
            }
            if (variables.isEmpty()) {
                if (getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                    maybeRemoveImport(MOCK_UTIL);
                    //noinspection DataFlowIssue
                    return null;
                }
                return vd;
            }
            if (original.get(0) != variables.get(0)) {
                // The next declarator now carries the separation from the type expression
                variables = ListUtils.mapFirst(variables, v -> v.getElement().getPrefix().getComments().isEmpty() ?
                        v.withElement(v.getElement().withPrefix(Space.SINGLE_SPACE)) : v);
            }
            JRightPadded<J.VariableDeclarations.NamedVariable> last = original.get(original.size() - 1);
            if (variables.get(variables.size() - 1) != last) {
                // The previous declarator now carries the separation from the semicolon
                variables = ListUtils.mapLast(variables, v -> v.withAfter(last.getAfter()));
            }
            return vd.getPadding().withVariables(variables);
        }

        private static boolean isObsoleteMockUtilInstance(J.VariableDeclarations.NamedVariable variable, J.CompilationUnit scope) {
            if (!(variable.getInitializer() instanceof J.NewClass) || !METHOD_MATCHER.matches((J.NewClass) variable.getInitializer())) {
                return false;
            }
            JavaType.Variable variableType = variable.getVariableType();
            // Without symbol attribution the uses cannot be proven obsolete
            return variableType != null &&
                    !new FindUnmigratedUses(variableType, variable.getSimpleName()).reduce(scope, new AtomicBoolean()).get();
        }

        @RequiredArgsConstructor
        private static class FindUnmigratedUses extends JavaIsoVisitor<AtomicBoolean> {
            private final JavaType.Variable variableType;
            private final String name;

            @Override
            public @Nullable J visit(@Nullable Tree tree, AtomicBoolean found) {
                return found.get() ? (J) tree : super.visit(tree, found);
            }

            @Override
            public J.Package visitPackage(J.Package pkg, AtomicBoolean found) {
                // Package and import name segments are not uses of the variable
                return pkg;
            }

            @Override
            public J.Import visitImport(J.Import anImport, AtomicBoolean found) {
                return anImport;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                if (!name.equals(identifier.getSimpleName()) ||
                        identifier.getFieldType() != null && !variableType.equals(identifier.getFieldType())) {
                    return identifier;
                }
                Cursor parent = getCursor().getParentTreeCursor();
                if (!isNeverVariableReference(identifier, parent) && !isMigratedUse(identifier, parent)) {
                    found.set(true);
                }
                return identifier;
            }

            /**
             * @return whether the identifier declares a variable or names something in another namespace, so that
             * it can never reference the variable under analysis.
             */
            private static boolean isNeverVariableReference(J.Identifier identifier, Cursor parentCursor) {
                if (identifier.getFieldType() == null &&
                        (identifier.getType() instanceof JavaType.Class || identifier.getType() instanceof JavaType.GenericTypeVariable)) {
                    return true;
                }
                Object parent = parentCursor.getValue();
                if (parent instanceof J.VariableDeclarations.NamedVariable) {
                    return ((J.VariableDeclarations.NamedVariable) parent).getName() == identifier;
                }
                if (parent instanceof J.MethodInvocation) {
                    return ((J.MethodInvocation) parent).getName() == identifier;
                }
                if (parent instanceof J.MethodDeclaration) {
                    return ((J.MethodDeclaration) parent).getName() == identifier;
                }
                if (parent instanceof J.ClassDeclaration) {
                    return ((J.ClassDeclaration) parent).getName() == identifier;
                }
                if (parent instanceof J.MemberReference) {
                    return ((J.MemberReference) parent).getReference() == identifier;
                }
                if (parent instanceof J.Label) {
                    return ((J.Label) parent).getLabel() == identifier;
                }
                if (parent instanceof J.Break) {
                    return ((J.Break) parent).getLabel() == identifier;
                }
                if (parent instanceof J.Continue) {
                    return ((J.Continue) parent).getLabel() == identifier;
                }
                if (parent instanceof J.EnumValue) {
                    return ((J.EnumValue) parent).getName() == identifier;
                }
                if (parent instanceof J.TypeParameter) {
                    return ((J.TypeParameter) parent).getName() == identifier;
                }
                if (parent instanceof J.Assignment) {
                    // The left side of an annotation element assignment names the element, not a variable
                    return ((J.Assignment) parent).getVariable() == identifier &&
                            parentCursor.getParentTreeCursor().getValue() instanceof J.Annotation;
                }
                if (parent instanceof J.FieldAccess) {
                    // Package and type segments carry no field type, unlike an attributed variable
                    return identifier.getFieldType() == null;
                }
                return false;
            }

            /**
             * @return whether the identifier is the receiver of a call {@link ChangeMethodTargetToStatic} makes
             * static, bare or through a field access, in which case the rewrite drops it and the instance with it.
             */
            private static boolean isMigratedUse(J.Identifier identifier, Cursor parent) {
                Object parentValue = parent.getValue();
                if (parentValue instanceof J.FieldAccess && ((J.FieldAccess) parentValue).getName() == identifier) {
                    return isMigratedReceiver((J.FieldAccess) parentValue, parent.getParentTreeCursor().getValue());
                }
                return isMigratedReceiver(identifier, parentValue);
            }

            private static boolean isMigratedReceiver(Expression receiver, Object parent) {
                if (parent instanceof J.MethodInvocation) {
                    J.MethodInvocation method = (J.MethodInvocation) parent;
                    return method.getSelect() == receiver && MIGRATED_METHOD_MATCHER.matches(method);
                }
                if (parent instanceof J.MemberReference) {
                    J.MemberReference reference = (J.MemberReference) parent;
                    return reference.getContaining() == receiver && MIGRATED_METHOD_MATCHER.matches(reference);
                }
                return false;
            }
        }
    }
}
