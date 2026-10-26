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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoveInitMocksIfRunnersSpecified extends Recipe {

    @Getter
    final String displayName = "Remove `MockitoAnnotations.initMocks(this)` and `openMocks(this)` if JUnit runners specified";

    @Getter
    final String description = "Remove `MockitoAnnotations.initMocks(this)` and `MockitoAnnotations.openMocks(this)` if class-level " +
            "JUnit runners `@RunWith(MockitoJUnitRunner.class)` or `@ExtendWith(MockitoExtension.class)` are specified. " +
            "These manual initialization calls are redundant when using Mockito's JUnit integration. " +
            "Note that the `@Mock` fields will then be initialized by the strict mocking session of the extension or runner; " +
            "tests that relied on the lenient mocks created by an explicit `openMocks(this)` call inside `@BeforeEach` " +
            "may surface `UnnecessaryStubbingException`. Add `@MockitoSettings(strictness = Strictness.LENIENT)` to opt out.";

    private static final String MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";
    private static final String MOCKITO_JUNIT_RUNNER = "org.mockito.junit.MockitoJUnitRunner";
    private static final AnnotationMatcher MOCKITO_EXTENSION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.extension.ExtendWith(" + MOCKITO_EXTENSION + ".class)");
    private static final AnnotationMatcher MOCKITO_JUNIT_MATCHER = new AnnotationMatcher("@org.junit.runner.RunWith(" + MOCKITO_JUNIT_RUNNER + ".class)");
    private static final AnnotationMatcher NESTED_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Nested");
    private static final MethodMatcher INIT_MOCKS_MATCHER = new MethodMatcher("org.mockito.MockitoAnnotations initMocks(..)", false);
    private static final MethodMatcher OPEN_MOCKS_MATCHER = new MethodMatcher("org.mockito.MockitoAnnotations openMocks(..)", false);
    private static final MethodMatcher CLOSEABLE_MATCHER = new MethodMatcher("java.lang.AutoCloseable close()", false);
    private static final List<AnnotationMatcher> BEFORE_AND_AFTER_MATCHERS = Arrays.asList(
            new AnnotationMatcher("@org.junit.jupiter.api.BeforeAll"),
            new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach"),
            new AnnotationMatcher("@org.junit.BeforeClass"),
            new AnnotationMatcher("@org.junit.Before"),
            new AnnotationMatcher("@org.junit.jupiter.api.AfterAll"),
            new AnnotationMatcher("@org.junit.jupiter.api.AfterEach"),
            new AnnotationMatcher("@org.junit.AfterClass"),
            new AnnotationMatcher("@org.junit.After")
    );

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        Preconditions.or(
                                new UsesMethod<>(INIT_MOCKS_MATCHER),
                                new UsesMethod<>(OPEN_MOCKS_MATCHER)
                        ),
                        Preconditions.or(
                                new UsesType<>(MOCKITO_EXTENSION, false),
                                new UsesType<>(MOCKITO_JUNIT_RUNNER, false)
                        )
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        // A handle can be removed only when all of its uses belong to removable initializations/close calls.
                        // In particular, sharing it with a helper's openMocks call must retain its declaration and cleanup.
                        List<JavaType.Variable> closeables = new JavaIsoVisitor<List<JavaType.Variable>>() {
                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, List<JavaType.Variable> variables) {
                                JavaType.Variable variable = referencedVariable(assignment.getVariable());
                                if (variable != null && isMockitoOpenMocksCall(assignment.getAssignment(), getCursor()) &&
                                    isSimpleHandle(assignment.getVariable()) && isBlockStatement(getCursor()) &&
                                    variables.stream().noneMatch(variable::equals)) {
                                    variables.add(variable);
                                }
                                return super.visitAssignment(assignment, variables);
                            }
                        }.reduce(cd, new ArrayList<>(), getCursor().getParentOrThrow());
                        closeables.removeIf(variable -> !hasOnlyRemovableHandleUses(cd, variable, getCursor().getParentOrThrow()));

                        J.ClassDeclaration modifiedCd = (J.ClassDeclaration) new JavaIsoVisitor<ExecutionContext>() {

                            @Override
                            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                                if (isManagedClass(getCursor())) {
                                    return super.visitClassDeclaration(classDecl, ctx);
                                }
                                return classDecl;
                            }

                            @Override
                            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                                // Anonymous-class this is a different instance. A lambda, in contrast, retains lexical this.
                                return newClass.getBody() == null ? super.visitNewClass(newClass, ctx) : newClass;
                            }

                            @Override
                            public J.Try visitTry(J.Try tryable, ExecutionContext ctx) {
                                J.Try t = tryable;
                                List<J.Try.Resource> resources = t.getResources();
                                if (resources != null) {
                                    // Drop `try (AutoCloseable mocks = MockitoAnnotations.openMocks(this))` resources
                                    List<J.Try.Resource> kept = ListUtils.filter(resources, r -> !isRemovableMockInitResource(r, tryable.getBody()));
                                    if (kept != resources) {
                                        boolean nothingLeft = kept.isEmpty() && t.getCatches().isEmpty() && t.getFinally() == null;
                                        if (nothingLeft && !parentCanCollapseNeuteredTry()) {
                                            // A resource-less/catchless/finallyless `try { body }` is invalid Java; leave the try alone unless an ancestor visit knows how to consume it
                                            return super.visitTry(t, ctx);
                                        }
                                        if (kept.isEmpty()) {
                                            t = t.withResources(null);
                                        } else {
                                            if (kept.get(0) != resources.get(0)) {
                                                // The removed resource was first; keep its (usually empty) prefix
                                                Space firstPrefix = resources.get(0).getPrefix();
                                                kept = ListUtils.mapFirst(kept, r -> r.withPrefix(firstPrefix));
                                            }
                                            boolean lastTerminatedWithSemicolon = resources.get(resources.size() - 1).isTerminatedWithSemicolon();
                                            kept = ListUtils.mapLast(kept, r -> r.withTerminatedWithSemicolon(lastTerminatedWithSemicolon));
                                            t = t.withResources(kept);
                                        }
                                    }
                                }
                                return super.visitTry(t, ctx);
                            }

                            @Override
                            public J.If.Else visitElse(J.If.Else elze, ExecutionContext ctx) {
                                J.If.Else e = super.visitElse(elze, ctx);
                                return e.withBody(collapseNeuteredTry(e.getBody()));
                            }

                            @Override
                            public J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, ExecutionContext ctx) {
                                J.WhileLoop w = super.visitWhileLoop(whileLoop, ctx);
                                return w.withBody(collapseNeuteredTry(w.getBody()));
                            }

                            @Override
                            public J.DoWhileLoop visitDoWhileLoop(J.DoWhileLoop doWhileLoop, ExecutionContext ctx) {
                                J.DoWhileLoop d = super.visitDoWhileLoop(doWhileLoop, ctx);
                                return d.withBody(collapseNeuteredTry(d.getBody()));
                            }

                            @Override
                            public J.ForLoop visitForLoop(J.ForLoop forLoop, ExecutionContext ctx) {
                                J.ForLoop f = super.visitForLoop(forLoop, ctx);
                                return f.withBody(collapseNeuteredTry(f.getBody()));
                            }

                            @Override
                            public J.ForEachLoop visitForEachLoop(J.ForEachLoop forEachLoop, ExecutionContext ctx) {
                                J.ForEachLoop f = super.visitForEachLoop(forEachLoop, ctx);
                                return f.withBody(collapseNeuteredTry(f.getBody()));
                            }

                            @Override
                            public J.Label visitLabel(J.Label label, ExecutionContext ctx) {
                                J.Label l = super.visitLabel(label, ctx);
                                return l.withStatement(collapseNeuteredTry(l.getStatement()));
                            }

                            private Statement collapseNeuteredTry(Statement stmt) {
                                if (stmt instanceof J.Try) {
                                    J.Try t = (J.Try) stmt;
                                    if (t.getResources() == null && t.getCatches().isEmpty() && t.getFinally() == null) {
                                        return t.getBody().withPrefix(t.getPrefix());
                                    }
                                }
                                return stmt;
                            }

                            private boolean parentCanCollapseNeuteredTry() {
                                Object parent = getCursor().getParentTreeCursor().getValue();
                                return parent instanceof J.Block ||
                                        parent instanceof J.If ||
                                        parent instanceof J.If.Else ||
                                        parent instanceof J.WhileLoop ||
                                        parent instanceof J.DoWhileLoop ||
                                        parent instanceof J.ForLoop ||
                                        parent instanceof J.ForEachLoop ||
                                        parent instanceof J.Label;
                            }

                            @Override
                            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                                J.Block b = super.visitBlock(block, ctx);
                                List<Statement> statements = b.getStatements();
                                return b.withStatements(ListUtils.flatMap(statements, (i, stmt) -> {
                                    if (!(stmt instanceof J.Try)) {
                                        return stmt;
                                    }
                                    J.Try t = (J.Try) stmt;
                                    if (t.getResources() != null || !t.getCatches().isEmpty() || t.getFinally() != null) {
                                        return stmt;
                                    }
                                    // Only resources were mock initializers and there is no catch/finally: the try is gone, keep its body
                                    J.Block body = t.getBody();
                                    if (!body.getEnd().getComments().isEmpty() ||
                                        declaresNameUsedLater(body, statements.subList(i + 1, statements.size()))) {
                                        return body.withPrefix(t.getPrefix());
                                    }
                                    List<Statement> inlined = ListUtils.map(body.getStatements(), s -> ShiftFormat.indent(s, getCursor(), -1));
                                    return ListUtils.mapFirst(inlined, s -> s.withPrefix(mergePrefix(t.getPrefix(), s.getPrefix())));
                                }));
                            }

                            private Space mergePrefix(Space tryPrefix, Space statementPrefix) {
                                if (statementPrefix.getComments().isEmpty()) {
                                    return tryPrefix;
                                }
                                return tryPrefix.withComments(ListUtils.concatAll(tryPrefix.getComments(), statementPrefix.getComments()));
                            }

                            private boolean declaresNameUsedLater(J.Block body, List<Statement> laterStatements) {
                                Set<String> declared = new HashSet<>();
                                for (Statement s : body.getStatements()) {
                                    if (s instanceof J.VariableDeclarations) {
                                        for (J.VariableDeclarations.NamedVariable v : ((J.VariableDeclarations) s).getVariables()) {
                                            declared.add(v.getSimpleName());
                                        }
                                    }
                                }
                                if (declared.isEmpty()) {
                                    return false;
                                }
                                // Any later use of a hoisted name would either fail to compile or silently resolve to the hoisted local
                                Set<String> usedLater = new HashSet<>();
                                JavaIsoVisitor<Set<String>> names = new JavaIsoVisitor<Set<String>>() {
                                    @Override
                                    public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> found) {
                                        // Only variable/field references can shadow-collide with a hoisted local; method names and type refs have null fieldType
                                        if (identifier.getFieldType() != null) {
                                            found.add(identifier.getSimpleName());
                                        }
                                        return identifier;
                                    }
                                };
                                for (Statement later : laterStatements) {
                                    names.reduce(later, usedLater);
                                }
                                return declared.stream().anyMatch(usedLater::contains);
                            }

                            private boolean isRemovableMockInitResource(J.Try.Resource resource, J.Block body) {
                                if (!(resource.getVariableDeclarations() instanceof J.VariableDeclarations)) {
                                    return false;
                                }
                                List<J.VariableDeclarations.NamedVariable> variables = ((J.VariableDeclarations) resource.getVariableDeclarations()).getVariables();
                                if (variables.size() != 1 || variables.get(0).getInitializer() == null) {
                                    return false;
                                }
                                // `initMocks` returns void so cannot appear as a resource initializer; only `openMocks` is reachable here
                                Expression initializer = variables.get(0).getInitializer();
                                if (!isMockitoOpenMocksCall(initializer, getCursor())) {
                                    return false;
                                }
                                // Keep the resource when the body still uses the variable
                                String name = variables.get(0).getSimpleName();
                                return !new JavaIsoVisitor<AtomicBoolean>() {
                                    @Override
                                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean referenced) {
                                        if (identifier.getSimpleName().equals(name)) {
                                            referenced.set(true);
                                        }
                                        return identifier;
                                    }
                                }.reduce(body, new AtomicBoolean()).get();
                            }

                            @Override
                            public  J.@Nullable Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                                J.Assignment a = super.visitAssignment(assignment, ctx);
                                JavaType.Variable variable = referencedVariable(assignment.getVariable());
                                if (variable != null && closeables.stream().anyMatch(variable::equals) &&
                                    isMockitoOpenMocksCall(assignment.getAssignment(), getCursor()) && isBlockStatement(getCursor())) {
                                    maybeRemoveImport("org.mockito.MockitoAnnotations");
                                    return null;
                                }
                                return a;
                            }

                            @Override
                            public  J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                                // Value-producing contexts and assignments are handled separately or conservatively retained.
                                if ((isMockitoOpenMocksCall(mi, getCursor()) || isMockitoInitMocksCall(mi, getCursor())) &&
                                    isBlockStatement(getCursor())) {
                                    return null;
                                }
                                JavaType.Variable variable = referencedVariable(mi.getSelect());
                                if (CLOSEABLE_MATCHER.matches(mi) && variable != null &&
                                    closeables.stream().anyMatch(variable::equals) && isBlockStatement(getCursor())) {
                                    return null;
                                }
                                return mi;
                            }

                            @Override
                            public  J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                                // Retain unrelated siblings in declarations such as `AutoCloseable managed, foreign`.
                                List<J.VariableDeclarations.NamedVariable> variables = ListUtils.filter(vd.getVariables(),
                                        variable -> closeables.stream().noneMatch(it -> it.equals(variable.getVariableType())));
                                return variables.isEmpty() ? null : vd.withVariables(variables);
                            }

                            @Override
                            public J.@Nullable If visitIf(J.If iff, ExecutionContext ctx) {
                                J.If i = super.visitIf(iff, ctx);
                                i = i.withThenPart(collapseNeuteredTry(i.getThenPart()));
                                if (i != iff &&
                                    i.getThenPart() instanceof J.Block &&
                                    ((J.Block) i.getThenPart()).getStatements().isEmpty() &&
                                    i.getElsePart() == null) {
                                    return null;
                                }
                                return i;
                            }

                            @Override
                            public  J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                                if (md != method && md.getBody() != null && md.getBody().getStatements().isEmpty()) {
                                    // Only remove empty Before and After methods
                                    if (BEFORE_AND_AFTER_MATCHERS.stream().anyMatch(matcher ->
                                            service(AnnotationService.class).matches(getCursor(), matcher))) {
                                        return null;
                                    }
                                }
                                return md;
                            }

                        }.visitNonNull(cd, ctx, getCursor().getParentOrThrow());

                        maybeRemoveImport("org.mockito.MockitoAnnotations");
                        maybeRemoveImport("org.junit.jupiter.api.BeforeAll");
                        maybeRemoveImport("org.junit.jupiter.api.BeforeEach");
                        maybeRemoveImport("org.junit.jupiter.api.AfterAll");
                        maybeRemoveImport("org.junit.jupiter.api.AfterEach");
                        maybeRemoveImport("org.junit.BeforeClass");
                        maybeRemoveImport("org.junit.Before");
                        maybeRemoveImport("org.junit.AfterClass");
                        maybeRemoveImport("org.junit.After");

                        return modifiedCd;
                    }

                    private boolean isMockitoOpenMocksCall(Expression expr, Cursor cursor) {
                        return expr instanceof J.MethodInvocation && OPEN_MOCKS_MATCHER.matches((J.MethodInvocation) expr) &&
                               initializesManagedThis((J.MethodInvocation) expr, cursor);
                    }

                    private boolean isMockitoInitMocksCall(Expression expr, Cursor cursor) {
                        return expr instanceof J.MethodInvocation && INIT_MOCKS_MATCHER.matches((J.MethodInvocation) expr) &&
                               initializesManagedThis((J.MethodInvocation) expr, cursor);
                    }

                    private boolean initializesManagedThis(J.MethodInvocation method, Cursor cursor) {
                        if (method.getArguments().size() != 1 || !(method.getArguments().get(0) instanceof J.Identifier) ||
                            !"this".equals(((J.Identifier) method.getArguments().get(0)).getSimpleName())) {
                            return false;
                        }
                        Cursor owner = instanceOwner(cursor);
                        return owner != null && isManagedClass(owner) &&
                               ((J.ClassDeclaration) owner.getValue()).getType() != null &&
                               TypeUtils.isOfType(method.getArguments().get(0).getType(), ((J.ClassDeclaration) owner.getValue()).getType());
                    }

                    private @Nullable Cursor instanceOwner(Cursor cursor) {
                        for (Cursor c = cursor; c != null; c = c.getParent()) {
                            if (c.getValue() instanceof J.NewClass && ((J.NewClass) c.getValue()).getBody() != null) {
                                return null;
                            }
                            if (c.getValue() instanceof J.ClassDeclaration) {
                                return c;
                            }
                        }
                        return null;
                    }

                    private boolean isManagedClass(Cursor cursor) {
                        return service(AnnotationService.class).matches(cursor, MOCKITO_JUNIT_MATCHER) || hasMockitoExtension(cursor);
                    }

                    private boolean hasMockitoExtension(Cursor cursor) {
                        if (service(AnnotationService.class).matches(cursor, MOCKITO_EXTENSION_MATCHER)) {
                            return true;
                        }
                        J.ClassDeclaration cd = cursor.getValue();
                        if (cd.hasModifier(J.Modifier.Type.Static) || !service(AnnotationService.class).matches(cursor, NESTED_MATCHER)) {
                            return false;
                        }
                        Cursor body = cursor.getParentTreeCursor();
                        if (!(body.getValue() instanceof J.Block)) {
                            return false;
                        }
                        Cursor enclosing = body.getParentTreeCursor();
                        // Only genuine member @Nested chains inherit the enclosing Jupiter extension.
                        return enclosing.getValue() instanceof J.ClassDeclaration &&
                               ((J.ClassDeclaration) enclosing.getValue()).getBody() == body.getValue() && hasMockitoExtension(enclosing);
                    }

                    private boolean isBlockStatement(Cursor cursor) {
                        return cursor.getParentTreeCursor().getValue() instanceof J.Block;
                    }

                    private boolean isSimpleHandle(Expression expression) {
                        return expression instanceof J.Identifier || expression instanceof J.FieldAccess &&
                               ((J.FieldAccess) expression).getTarget() instanceof J.Identifier &&
                               "this".equals(((J.Identifier) ((J.FieldAccess) expression).getTarget()).getSimpleName());
                    }

                    private JavaType.@Nullable Variable referencedVariable(@Nullable Expression expression) {
                        return expression instanceof J.Identifier ? ((J.Identifier) expression).getFieldType() :
                                expression instanceof J.FieldAccess ? ((J.FieldAccess) expression).getName().getFieldType() : null;
                    }

                    private boolean hasOnlyRemovableHandleUses(J.ClassDeclaration cd, JavaType.Variable variable, Cursor parent) {
                        return !new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean unsafe) {
                                if (!variable.equals(identifier.getFieldType())) {
                                    return identifier;
                                }
                                Cursor reference = getCursor();
                                Cursor containing = reference.getParentTreeCursor();
                                Object tree = containing.getValue();
                                if (tree instanceof J.VariableDeclarations.NamedVariable &&
                                    ((J.VariableDeclarations.NamedVariable) tree).getName() == identifier) {
                                    if (((J.VariableDeclarations.NamedVariable) tree).getInitializer() != null) {
                                        unsafe.set(true);
                                    }
                                    return identifier;
                                }
                                if (tree instanceof J.FieldAccess && ((J.FieldAccess) tree).getName() == identifier) {
                                    reference = containing;
                                    containing = reference.getParentTreeCursor();
                                    tree = containing.getValue();
                                }
                                Expression expression = reference.getValue();
                                Cursor owner = instanceOwner(reference);
                                if (owner == null || !isManagedClass(owner) || !isSimpleHandle(expression)) {
                                    unsafe.set(true);
                                } else if (tree instanceof J.Assignment && ((J.Assignment) tree).getVariable() == expression &&
                                           isBlockStatement(containing) && isMockitoOpenMocksCall(((J.Assignment) tree).getAssignment(), containing)) {
                                    // A removable initialization of this exact handle.
                                } else if (tree instanceof J.MethodInvocation && ((J.MethodInvocation) tree).getSelect() == expression &&
                                           CLOSEABLE_MATCHER.matches((J.MethodInvocation) tree) && isBlockStatement(containing)) {
                                    // Its standalone cleanup.
                                } else if (isNullGuardedCleanup(reference, variable)) {
                                    // The existing empty-if cleanup removes this pure guard together with its only close call.
                                } else {
                                    // Escaping, reassigned, foreign-owner or otherwise ambiguous handles must remain intact.
                                    unsafe.set(true);
                                }
                                return identifier;
                            }
                        }.reduce(cd, new AtomicBoolean(), parent).get();
                    }

                    private boolean isNullGuardedCleanup(Cursor reference, JavaType.Variable variable) {
                        Cursor binaryCursor = reference.getParentTreeCursor();
                        if (!(binaryCursor.getValue() instanceof J.Binary)) {
                            return false;
                        }
                        J.Binary binary = binaryCursor.getValue();
                        Expression other = binary.getLeft() == reference.getValue() ? binary.getRight() : binary.getLeft();
                        if (binary.getOperator() != J.Binary.Type.NotEqual || !(other instanceof J.Literal) ||
                            ((J.Literal) other).getValue() != null) {
                            return false;
                        }
                        Cursor condition = binaryCursor.getParentTreeCursor();
                        if (!(condition.getValue() instanceof J.ControlParentheses) ||
                            !(condition.getParentTreeCursor().getValue() instanceof J.If)) {
                            return false;
                        }
                        J.If iff = condition.getParentTreeCursor().getValue();
                        if (iff.getIfCondition() != condition.getValue() || iff.getElsePart() != null || !(iff.getThenPart() instanceof J.Block)) {
                            return false;
                        }
                        List<Statement> statements = ((J.Block) iff.getThenPart()).getStatements();
                        if (statements.size() != 1 || !(statements.get(0) instanceof J.MethodInvocation)) {
                            return false;
                        }
                        J.MethodInvocation close = (J.MethodInvocation) statements.get(0);
                        return CLOSEABLE_MATCHER.matches(close) && close.getSelect() != null &&
                               isSimpleHandle(close.getSelect()) && variable.equals(referencedVariable(close.getSelect()));
                    }
                }
        );
    }
}
