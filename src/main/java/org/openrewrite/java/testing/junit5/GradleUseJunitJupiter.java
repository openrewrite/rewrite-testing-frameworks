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
package org.openrewrite.java.testing.junit5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.gradle.IsBuildGradle;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.tree.ParseError;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.internal.ListUtils.concat;

@Value
@EqualsAndHashCode(callSuper = false)
public class GradleUseJunitJupiter extends Recipe {
    String displayName = "Gradle `Test` use JUnit Jupiter";

    String description = "By default Gradle's `Test` tasks use JUnit 4. " +
                "Gradle `Test` tasks must be configured with `useJUnitPlatform()` to run JUnit Jupiter tests. " +
                "This recipe adds the `useJUnitPlatform()` method call to the `Test` task configuration in `build.gradle` and `build.gradle.kts` files.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // Avoid adding a new test configuration to script plugins as it may be added too broadly to all scripts
        return Preconditions.check(new IsBuildGradle<>(true), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof JavaSourceFile)) {
                    return tree;
                }
                JavaSourceFile compilationUnit = (JavaSourceFile) tree;
                if (!hasJavaBasePlugin(compilationUnit) || containsJUnitPlatformInvocation(compilationUnit)) {
                    return compilationUnit;
                }
                // If anywhere in the tree there is a useJunit() we can swap it out for useJUnitPlatform() and be done in one step
                JavaSourceFile cu = (JavaSourceFile) new UpdateExistingUseJunit4().visitNonNull(compilationUnit, ctx, getCursor());
                if (cu != compilationUnit) {
                    return cu;
                }
                // No useJUnit(), but there might already be configuration of a Test task, add useJUnitPlatform() to it
                cu = (JavaSourceFile) new AddJUnitPlatformToExistingTestDsl().visitNonNull(cu, ctx, getCursor());
                if (cu != compilationUnit) {
                    return cu;
                }

                // No existing test task configuration seems to exist, add a whole new one
                return addUseJUnitPlatform(cu, new Cursor(getCursor(), cu), ctx);
            }
        });
    }

    private static Boolean hasJavaBasePlugin(JavaSourceFile compilationUnit) {
        return compilationUnit.getMarkers().findFirst(GradleProject.class)
                .map(gp -> gp.getPlugins().stream().anyMatch(it ->
                        it.getFullyQualifiedClassName().contains("org.gradle.api.plugins.JavaBasePlugin")))
                .orElse(false);
    }

    private static boolean containsJUnitPlatformInvocation(JavaSourceFile cu) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public @Nullable J preVisit(J tree, AtomicBoolean found) {
                if (found.get()) {
                    stopAfterPreVisit();
                    return tree;
                }
                return super.preVisit(tree, found);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, AtomicBoolean found) {
                // Groovy gradle scripts being weakly type-attributed means we will miss likely-correct changes if we are too strict
                if ("useJUnitPlatform".equals(m.getSimpleName()) &&
                    (m.getArguments().isEmpty() ||
                     m.getArguments().size() == 1 &&
                     (m.getArguments().get(0) instanceof J.Empty || m.getArguments().get(0) instanceof J.Lambda))) {
                    found.set(true);
                    return m;
                }
                return super.visitMethodInvocation(m, found);
            }
        }.reduce(cu, new AtomicBoolean(false)).get();
    }

    private static class UpdateExistingUseJunit4 extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            // Groovy gradle scripts being weakly type-attributed means we will miss changes if we are too strict
            if ("useJUnit".equals(m.getSimpleName()) && (m.getArguments().isEmpty() || m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Empty)) {
                JavaType.Method useJUnitPlatformType = Optional.ofNullable(m.getMethodType())
                        .map(JavaType.Method::getDeclaringType)
                        .flatMap(declaringType -> declaringType.getMethods()
                                .stream()
                                .filter(method1 -> "useJUnitPlatform".equals(method1.getName()))
                                .findFirst())
                        .orElse(null);
                return m.withName(m.getName().withSimpleName("useJUnitPlatform"))
                        .withMethodType(useJUnitPlatformType);
            }
            return m;
        }
    }

    private static JavaSourceFile addUseJUnitPlatform(JavaSourceFile cu, Cursor cursor, ExecutionContext ctx) {
        J.MethodInvocation task = createTaskUseJUnitPlatform(cursor, ctx, true).orElse(null);
        if (cu instanceof K.CompilationUnit) {
            K.CompilationUnit kcu = (K.CompilationUnit) cu;
            return kcu.withStatements(ListUtils.mapFirst(kcu.getStatements(), script -> ((J.Block) script).withStatements(concat(((J.Block) script).getStatements(), task))));
        }
        G.CompilationUnit gcu = (G.CompilationUnit) cu;
        return gcu.withStatements(concat(gcu.getStatements(), task));
    }

    private static class AddJUnitPlatformToExistingTestDsl extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            String mName = m.getSimpleName();
            // A non-exhaustive list of common ways by which the task may already be configured
            // test { }
            // tasks.test { }
            // tasks.withType(Test) { }
            // tasks.withType<Test> { }
            // tasks.withType(Test).configureEach { }
            // tasks.withType<Test>().configureEach { }
            // tasks.named("test") { }
            // tasks.named("test", Test) { }
            // tasks.named<Test>("test") { }
            switch (mName) {
                case "test":
                    if (!(m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Lambda)) {
                        return m;
                    }
                    // Kotlin DSL `sourceSets.test { }` configures a source set rather than the task
                    if (m.getSelect() != null && !isTasks(m.getSelect())) {
                        return m;
                    }
                    // Other DSLs may be named "test" so only assume it is `test {}` if it isn't enclosed in anything else
                    if (getCursor().getParentTreeCursor().firstEnclosing(J.MethodInvocation.class) != null) {
                        return m;
                    }
                    break;
                case "named":
                    if (m.getArguments().isEmpty()) {
                        return m;
                    }
                    if (!(m.getArguments().get(0) instanceof J.Literal && "test".equals(((J.Literal) m.getArguments().get(0)).getValue()))) {
                        return m;
                    }
                    // The final argument must be a J.Lambda
                    if (!(m.getArguments().get(m.getArguments().size() - 1) instanceof J.Lambda)) {
                        return m;
                    }
                    break;
                case "withType":
                    if (m.getSelect() == null || !isTasks(m.getSelect()) || !isWithTypeTest(m)) {
                        return m;
                    }
                    break;
                case "configureEach":
                    if (m.getArguments().size() != 1 || !(m.getArguments().get(0) instanceof J.Lambda)) {
                        return m;
                    }
                    if (!(m.getSelect() instanceof J.MethodInvocation) || !isWithTypeTest((J.MethodInvocation) m.getSelect())) {
                        return m;
                    }
                    break;
                default:
                    return m;
            }

            return (J.MethodInvocation) new AddJUnitPlatformAsLastStatementInClosure()
                    .visitNonNull(m, ctx, requireNonNull(getCursor().getParent()));
        }
    }

    private static boolean isTasks(Expression select) {
        return TypeUtils.isOfClassType(select.getType(), "org.gradle.api.tasks.TaskContainer") ||
                // With Gradle 9 we saw the type change to Object, so fall back to checking simple name
                select instanceof J.Identifier && "tasks".equals(((J.Identifier) select).getSimpleName());
    }

    private static boolean isWithTypeTest(J.MethodInvocation withType) {
        if (!"withType".equals(withType.getSimpleName())) {
            return false;
        }
        List<Expression> typeParameters = withType.getTypeParameters();
        if (typeParameters != null) {
            return typeParameters.size() == 1 && isTestIdentifier(typeParameters.get(0));
        }
        Expression type = withType.getArguments().get(0);
        if (type instanceof J.FieldAccess && "java".equals(((J.FieldAccess) type).getSimpleName())) {
            type = ((J.FieldAccess) type).getTarget();
        }
        if (type instanceof J.MemberReference && "class".equals(((J.MemberReference) type).getReference().getSimpleName())) {
            type = ((J.MemberReference) type).getContaining();
        }
        return isTestIdentifier(type);
    }

    private static boolean isTestIdentifier(Expression expression) {
        return expression instanceof J.Identifier && "Test".equals(((J.Identifier) expression).getSimpleName());
    }

    private static class AddJUnitPlatformAsLastStatementInClosure extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.Lambda visitLambda(J.Lambda l, ExecutionContext ctx) {
            if (!(l.getBody() instanceof J.Block)) {
                return l;
            }
            return createTaskUseJUnitPlatform(getCursor(), ctx, false)
                    .map(it -> (J.Lambda) it.getArguments().get(it.getArguments().size() - 1))
                    .map(it -> ((J.Block) it.getBody()).getStatements().get(0))
                    // Groovy closures wrap their last statement in an implicit return
                    .map(it -> it instanceof J.Return ? (Statement) requireNonNull(((J.Return) it).getExpression()) : it)
                    .map(useJUnitPlatform -> {
                        J.Block b = (J.Block) l.getBody();
                        J.Lambda j = l.withBody(b.withStatements(concat(b.getStatements(), useJUnitPlatform)));
                        return autoFormat(j, ctx, requireNonNull(getCursor().getParent()));
                    })
                    .orElse(l);
        }
    }

    private static final String TEMPLATE_KEY = GradleUseJunitJupiter.class.getName() + ".template.";

    private static Optional<J.MethodInvocation> createTaskUseJUnitPlatform(Cursor cursor, ExecutionContext ctx, boolean forEachInvocation) {
        boolean kotlinDsl = cursor.firstEnclosing(K.CompilationUnit.class) != null;
        // Parsing is expensive, so cache once per cycle on the root cursor; not statically, to avoid retaining the LST.
        J.MethodInvocation template = cursor.getRoot().computeMessageIfAbsent(TEMPLATE_KEY + forEachInvocation + kotlinDsl,
                k -> parseTemplate(ctx, forEachInvocation, kotlinDsl));
        // Hand back a copy with fresh ids so reusing the cached template (across files, or across multiple matching
        // test blocks within one script) never produces colliding ids in the resulting tree.
        return template == null ? Optional.empty() : Optional.of(withNewIds(template));
    }

    private static J.@Nullable MethodInvocation parseTemplate(ExecutionContext ctx, boolean forEachInvocation, boolean kotlinDsl) {
        String plugins = kotlinDsl ? "plugins {\n    java\n}\n" : "plugins {\n    id 'java'\n}\n";
        String testTasks = kotlinDsl ? "tasks.withType<Test>()" : "tasks.withType(Test)";
        String snippet = plugins + testTasks + (forEachInvocation ? ".configureEach" : "") + " {\n    useJUnitPlatform()\n}";
        SourceFile sourceFile = GradleParser.builder()
                .build()
                .parseInputs(singletonList(Parser.Input.fromString(Paths.get(kotlinDsl ? "build.gradle.kts" : "build.gradle"), snippet)), null, ctx)
                .findFirst()
                .orElse(null);
        if (sourceFile == null) {
            return null;
        }
        if (sourceFile instanceof ParseError) {
            throw ((ParseError) sourceFile).toException();
        }
        List<Statement> statements = sourceFile instanceof K.CompilationUnit ? ((J.Block) ((K.CompilationUnit) sourceFile).getStatements().get(0)).getStatements() : ((G.CompilationUnit) sourceFile).getStatements();
        return (J.MethodInvocation) statements.get(1);
    }

    private static J.MethodInvocation withNewIds(J.MethodInvocation template) {
        return (J.MethodInvocation) new JavaIsoVisitor<Integer>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, Integer p) {
                J j = super.visit(tree, p);
                return j == null ? null : j.withId(Tree.randomId());
            }
        }.visitNonNull(template, 0);
    }
}
