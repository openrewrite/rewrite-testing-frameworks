/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.utils;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.*;

@Value
public class VariableUtil {

    private VariableUtil() {
    }

    public static Set<J.Identifier> findVariablesInScope(Cursor scope) {
        JavaSourceFile compilationUnit = scope.firstEnclosing(JavaSourceFile.class);
        if (compilationUnit == null) {
            throw new IllegalStateException("A JavaSourceFile is required in the cursor path.");
        }

        Set<J.Identifier> names = new HashSet<>();
        VariableScopeVisitor variableScopeVisitor = new VariableScopeVisitor(scope);
        variableScopeVisitor.visit(compilationUnit, names);
        return names;
    }

    private static final class VariableScopeVisitor extends JavaIsoVisitor<Set<J.Identifier>> {
        private final Cursor scope;
        private final Map<Cursor, Set<J.Identifier>> nameScopes;
        private final Stack<Cursor> currentScope;

        public VariableScopeVisitor(Cursor scope) {
            this.scope = scope;
            this.nameScopes = new LinkedHashMap<>();
            this.currentScope = new Stack<>();
        }

        /**
         * Aggregates namespaces into specific scopes.
         * I.E. names declared in a {@link J.ControlParentheses} will belong to the parent AST element.
         */
        private Cursor aggregateNameScope() {
            return getCursor().dropParentUntil(is ->
                    is instanceof JavaSourceFile ||
                            is instanceof J.ClassDeclaration ||
                            is instanceof J.MethodDeclaration ||
                            is instanceof J.Block ||
                            is instanceof J.ForLoop ||
                            is instanceof J.ForEachLoop ||
                            is instanceof J.Case ||
                            is instanceof J.Try ||
                            is instanceof J.Try.Catch ||
                            is instanceof J.Lambda);
        }

        @Override
        public Statement visitStatement(Statement statement, Set<J.Identifier> namesInScope) {
            Statement s = super.visitStatement(statement, namesInScope);
            Cursor aggregatedScope = aggregateNameScope();
            if (currentScope.isEmpty() || currentScope.peek() != aggregatedScope) {
                Set<J.Identifier> namesInAggregatedScope = nameScopes.computeIfAbsent(aggregatedScope, k -> new HashSet<>());
                // Pass the name scopes available from a parent scope down to the child.
                if (!currentScope.isEmpty() && aggregatedScope.isScopeInPath(currentScope.peek().getValue())) {
                    namesInAggregatedScope.addAll(nameScopes.get(currentScope.peek()));
                }
                currentScope.push(aggregatedScope);
            }
            return s;
        }

        @Override
        public @Nullable J preVisit(J tree, Set<J.Identifier> namesInScope) {
            // visit value from scope rather than `tree`, since calling recipe may have modified it already
            return scope.<J>getValue().isScope(tree) ? scope.getValue() : super.preVisit(tree, namesInScope);
        }

        // Stop after the tree has been processed to ensure all the names in scope have been collected.
        @Override
        public @Nullable J postVisit(J tree, Set<J.Identifier> namesInScope) {
            if (!currentScope.isEmpty() && currentScope.peek().getValue().equals(tree)) {
                currentScope.pop();
            }

            if (scope.getValue().equals(tree)) {
                Cursor aggregatedScope = getCursor().getValue() instanceof JavaSourceFile ? getCursor() : aggregateNameScope();
                // Add names from parent scope.
                Set<J.Identifier> names = nameScopes.get(aggregatedScope);

                // Add the names created in the target scope.
                namesInScope.addAll(names);
                nameScopes.forEach((key, value) -> {
                    if (key.isScopeInPath(scope.getValue())) {
                        namesInScope.addAll(value);
                    }
                });
                return tree;
            }

            return super.postVisit(tree, namesInScope);
        }

        @Override
        public J.Import visitImport(J.Import _import, Set<J.Identifier> namesInScope) {
            // Skip identifiers from `import`s.
            return _import;
        }

        @Override
        public J.Package visitPackage(J.Package pkg, Set<J.Identifier> namesInScope) {
            // Skip identifiers from `package`.
            return pkg;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Set<J.Identifier> namesInScope) {
            // Collect class fields first, because class fields are always visible regardless of what order the statements are declared.
            classDecl.getBody().getStatements().forEach(o -> {
                if (o instanceof J.VariableDeclarations) {
                    J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) o;
                    variableDeclarations.getVariables().forEach(v ->
                            nameScopes.computeIfAbsent(getCursor(), k -> new HashSet<>()).add(v.getName()));
                }
            });

            return super.visitClassDeclaration(classDecl, namesInScope);
        }

        @Override
        public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, Set<J.Identifier> identifiers) {
            if (instanceOf.getPattern() instanceof J.Identifier) {
                Set<J.Identifier> names = nameScopes.get(currentScope.peek());
                if (names != null) {
                    names.add(((J.Identifier) instanceOf.getPattern()));
                }
            }
            return super.visitInstanceOf(instanceOf, identifiers);
        }

        @Override
        public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<J.Identifier> identifiers) {
            Set<J.Identifier> names = nameScopes.get(currentScope.peek());
            if (names != null) {
                names.add(variable.getName());
            }
            return super.visitVariable(variable, identifiers);
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, Set<J.Identifier> namesInScope) {
            J.Identifier v = super.visitIdentifier(identifier, namesInScope);
            if (v.getType() instanceof JavaType.Class &&
                    ((JavaType.Class) v.getType()).getKind() == JavaType.FullyQualified.Kind.Enum) {
                namesInScope.add(v);
            }
            return v;
        }
    }
}
