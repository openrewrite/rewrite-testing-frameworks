/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Value
@EqualsAndHashCode(callSuper = false)
public class GradleUseJunitJupiter extends Recipe {
    @Override
    public String getDisplayName() {
        return "Gradle `Test` use JUnit Jupiter";
    }

    @Override
    public String getDescription() {
        return "By default Gradle's `Test` tasks use JUnit 4. " +
               "Gradle `Test` tasks must be configured with `useJUnitPlatform()` to run JUnit Jupiter tests. " +
               "This recipe adds the `useJUnitPlatform()` method call to the `Test` task configuration.";
    }

    private static final String USE_JUNIT_PLATFORM_PATTERN = "org.gradle.api.tasks.testing.Test useJUnitPlatform()";
    private static final MethodMatcher USE_JUNIT_PLATFORM_MATCHER = new MethodMatcher(USE_JUNIT_PLATFORM_PATTERN);
    private static final MethodMatcher USE_JUNIT4_MATCHER = new MethodMatcher("org.gradle.api.tasks.testing.Test useJUnit()");
    private static final MethodMatcher USE_JUNIT4_ALTERNATE_MATCHER = new MethodMatcher("RewriteTestSpec useJUnit()");
    private static final MethodMatcher TEST_DSL_MATCHER = new MethodMatcher("RewriteGradleProject test(..)");
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        //noinspection NotNullFieldNotInitialized
        return new GroovyIsoVisitor<ExecutionContext>() {

            GradleProject gp;

            @Override
            public G.CompilationUnit visitCompilationUnit(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
                //noinspection DataFlowIssue
                gp = compilationUnit.getMarkers().findFirst(GradleProject.class).orElse(null);
                if(gp == null) {
                    return compilationUnit;
                }
                if(gp.getPlugins().stream().noneMatch(plugin -> plugin.getFullyQualifiedClassName().contains("org.gradle.api.plugins.JavaBasePlugin"))) {
                    return compilationUnit;
                }
                if(containsJUnitPlatformInvocation(compilationUnit)) {
                    return compilationUnit;
                }
                // If anywhere in the tree there is a useJunit() we can swap it out for useJUnitPlatform() and be done in one step
                G.CompilationUnit cu = (G.CompilationUnit) new UpdateExistingUseJunit4()
                        .visitNonNull(compilationUnit, ctx, requireNonNull(getCursor().getParent()));
                if (cu != compilationUnit) {
                    return cu;
                }
                // No useJUnit(), but there might already be configuration of a Test task, add useJUnitPlatform() to it
                cu = (G.CompilationUnit) new AddJUnitPlatformToExistingTestDsl()
                        .visitNonNull(cu, ctx, requireNonNull(getCursor().getParent()));
                if(cu != compilationUnit) {
                    return cu;
                }
                // No existing test task configuration seems to exist, add a whole new one
                return (G.CompilationUnit) new AddUseJUnitPlatform()
                        .visitNonNull(cu, ctx, getCursor().getParent());
            }
        };
    }

    private static boolean containsJUnitPlatformInvocation(G.CompilationUnit cu) {
        AtomicBoolean found = new AtomicBoolean(false);
        new GroovyIsoVisitor<AtomicBoolean>() {
            @Override
            public @Nullable J preVisit(J tree, AtomicBoolean found) {
                if(found.get()) {
                    stopAfterPreVisit();
                    return tree;
                }
                return super.preVisit(tree, found);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, AtomicBoolean found) {
                // Groovy gradle scripts being weakly type-attributed means we will miss likely-correct changes if we are too strict
                if ("useJUnitPlatform".equals(m.getSimpleName()) && (m.getArguments().isEmpty() || m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Empty)) {
                    found.set(true);
                    return m;
                }
                return super.visitMethodInvocation(m, found);
            }
        }.visit(cu, found);
        return found.get();
    }

    private static class UpdateExistingUseJunit4 extends GroovyIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            // Groovy gradle scripts being weakly type-attributed means we will miss changes if we are too strict
            if ("useJUnit".equals(m.getSimpleName()) && (m.getArguments().isEmpty() || m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Empty)) {
                JavaType.Method useJUnitPlatformType = Optional.ofNullable(m.getMethodType())
                        .map(JavaType.Method::getDeclaringType)
                        .flatMap(declaringType -> declaringType.getMethods()
                                .stream()
                                .filter(method1 -> method1.getName().equals("useJUnitPlatform"))
                                .findFirst())
                        .orElse(null);
                return m.withName(m.getName().withSimpleName("useJUnitPlatform"))
                        .withMethodType(useJUnitPlatformType);
            }
            return m;
        }
    }

    private static class AddUseJUnitPlatform extends GroovyIsoVisitor<ExecutionContext> {
        @Override
        public G.CompilationUnit visitCompilationUnit(G.CompilationUnit cu, ExecutionContext executionContext) {
            G.CompilationUnit template = GradleParser.builder()
                    .build()
                    .parse("plugins {\n" +
                           "    id 'java'\n" +
                           "}\n" +
                           "tasks.withType(Test).configureEach {\n" +
                           "    useJUnitPlatform()\n" +
                           "}")
                    .map(G.CompilationUnit.class::cast)
                    .collect(Collectors.toList())
                    .get(0);
            J.MethodInvocation configureEachInvocation = (J.MethodInvocation) template.getStatements().get(1);
            return cu.withStatements(ListUtils.concat(cu.getStatements(), configureEachInvocation));
        }
    }

    private static class AddJUnitPlatformToExistingTestDsl extends GroovyIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            String mName = m.getSimpleName();
            // A non-exhaustive list of common ways by which the task may already be configured
            // test { }
            // tasks.withType(Test) { }
            // tasks.withType(Test).configureEach { }
            // tasks.named("test") { }
            // tasks.named("test", Test) { }
            switch (mName) {
                case "test":
                    if (!(m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Lambda)) {
                        return m;
                    }
                    // Other DSLs may be named "test" so only assume it is test {} if it isn't enclosed in anything else
                    if(getCursor().getParentTreeCursor().firstEnclosing(J.MethodInvocation.class) != null) {
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
                    if (m.getSelect() == null
                        || !TypeUtils.isOfClassType(m.getSelect().getType(), "org.gradle.api.tasks.TaskContainer")
                        || !(m.getArguments().get(0) instanceof J.Identifier && "Test".equals(((J.Identifier) m.getArguments().get(0)).getSimpleName()))) {
                        return m;
                    }
                    break;
                case "configureEach":
                    if(m.getArguments().size() != 1 || !(m.getArguments().get(0) instanceof J.Lambda)) {
                        return m;
                    }
                    if(m.getSelect() == null || !(m.getSelect() instanceof J.MethodInvocation)) {
                        return m;
                    }
                    J.MethodInvocation select = (J.MethodInvocation) m.getSelect();
                    if(!"withType".equals(select.getSimpleName())
                       || select.getArguments().size() != 1
                       || !(select.getArguments().get(0) instanceof J.Identifier)
                       || !"Test".equals(((J.Identifier) select.getArguments().get(0)).getSimpleName())) {
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

    private static class AddJUnitPlatformAsLastStatementInClosure extends GroovyIsoVisitor<ExecutionContext> {
        @Override
        public J.Lambda visitLambda(J.Lambda l, ExecutionContext ctx) {
            if(!(l.getBody() instanceof J.Block)) {
                return l;
            }
            G.CompilationUnit cu = GradleParser.builder()
                    .build()
                    .parse("plugins {\n" +
                           "    id 'java'\n" +
                           "}\n" +
                           "tasks.withType(Test) {\n" +
                           "    useJUnitPlatform()\n" +
                           "}")
                    .map(G.CompilationUnit.class::cast)
                    .collect(Collectors.toList())
                    .get(0);
            J.MethodInvocation useJUnitPlatform = Optional.of(cu.getStatements().get(1))
                    .map(J.MethodInvocation.class::cast)
                    .map(J.MethodInvocation::getArguments)
                    .map(args -> args.get(1))
                    .map(J.Lambda.class::cast)
                    .map(J.Lambda::getBody)
                    .map(J.Block.class::cast)
                    .map(J.Block::getStatements)
                    .map(statements -> statements.get(0))
                    .map(J.Return.class::cast)
                    .map(J.Return::getExpression)
                    .map(J.MethodInvocation.class::cast)
                    .orElse(null);
            if(useJUnitPlatform == null) {
                return l;
            }
            J.Block b = (J.Block) l.getBody();
            l = l.withBody(b.withStatements(ListUtils.concat(b.getStatements(), useJUnitPlatform)));
            return autoFormat(l, ctx, requireNonNull(getCursor().getParent()));
        }
    }
}
