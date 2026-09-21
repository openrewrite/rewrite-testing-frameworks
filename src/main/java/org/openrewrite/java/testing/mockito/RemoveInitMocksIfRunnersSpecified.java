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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

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

                        Set<Expression> closeables = new JavaIsoVisitor<Set<Expression>>() {
                            @Override
                            public J.Assignment visitAssignment(J.Assignment assignment, Set<Expression> exprSet) {
                                J.Assignment as = super.visitAssignment(assignment, exprSet);

                                if (isMockitoOpenMocksCall(assignment.getAssignment())) {
                                    exprSet.add(assignment.getVariable());
                                }
                                return as;
                            }
                        }.reduce(cd, new HashSet<>());

                        J.ClassDeclaration modifiedCd = (J.ClassDeclaration) new JavaIsoVisitor<ExecutionContext>() {

                            @Override
                            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                                if (service(AnnotationService.class).matches(getCursor(), MOCKITO_EXTENSION_MATCHER) ||
                                        service(AnnotationService.class).matches(getCursor(), MOCKITO_JUNIT_MATCHER) ||
                                        getCursor().getParentTreeCursor().firstEnclosing(J.ClassDeclaration.class) != null) {
                                    return super.visitClassDeclaration(classDecl, ctx);
                                }
                                return classDecl;
                            }

                            @Override
                            public J.Try visitTry(J.Try tryable, ExecutionContext ctx) {
                                J.Try t = tryable;
                                List<J.Try.Resource> resources = t.getResources();
                                if (resources != null) {
                                    // Drop `try (AutoCloseable mocks = MockitoAnnotations.openMocks(this))` resources
                                    List<J.Try.Resource> kept = ListUtils.map(resources, r -> isRemovableMockInitResource(r, tryable.getBody()) ? null : r);
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
                                if (!isMockitoOpenMocksCall(initializer)) {
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
                                // Remove assignments where RHS is initMocks/openMocks
                                if (isMockitoInitMocksCall(assignment.getAssignment()) || isMockitoOpenMocksCall(assignment.getAssignment())) {
                                    maybeRemoveImport("org.mockito.MockitoAnnotations");
                                    return null;
                                }
                                return a;
                            }

                            @Override
                            public  J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                                if ((OPEN_MOCKS_MATCHER.matches(mi) || INIT_MOCKS_MATCHER.matches(mi)) &&
                                    // A variable initializer cannot be dropped; the try-with-resources case is handled in visitTry
                                    !(getCursor().getParentTreeCursor().getValue() instanceof J.VariableDeclarations.NamedVariable)) {
                                    return null;
                                }
                                if (CLOSEABLE_MATCHER.matches(mi) && mi.getSelect() != null && closeables.stream().anyMatch(it -> SemanticallyEqual.areEqual(it, mi.getSelect()))) {
                                    return null;
                                }
                                return mi;
                            }

                            @Override
                            public  J.@Nullable VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                                // Remove field declarations for fields that store openMocks result
                                for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                                    if (closeables.stream().anyMatch(it -> SemanticallyEqual.areEqual(it, variable.getDeclarator()))) {
                                        return null;
                                    }
                                }
                                return vd;
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

                    private boolean isMockitoOpenMocksCall(Expression expr) {
                        return expr instanceof J.MethodInvocation && OPEN_MOCKS_MATCHER.matches((J.MethodInvocation)expr);
                    }

                    private boolean isMockitoInitMocksCall(Expression expr) {
                        return expr instanceof J.MethodInvocation && INIT_MOCKS_MATCHER.matches((J.MethodInvocation)expr);
                    }
                }
        );
    }
}
