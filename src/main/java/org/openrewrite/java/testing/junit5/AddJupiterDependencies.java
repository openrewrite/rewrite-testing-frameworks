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
import org.openrewrite.*;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.RemoveDependency;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.xml.tree.Xml;

import java.util.List;
import java.util.Optional;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddJupiterDependencies extends ScanningRecipe<AddJupiterDependencies.Accumulator> {
    String displayName = "Add JUnit Jupiter dependencies";

    String description = "Adds JUnit Jupiter dependencies to a Maven or Gradle project. " +
               "JUnit Jupiter can be added either with the artifact `junit-jupiter`, or both of `junit-jupiter-api` and `junit-jupiter-engine`. " +
               "This adds `junit-jupiter` dependency unless `junit-jupiter-api` or `junit-jupiter-engine` are already present. " +
               "Maven projects running dependency analysis receive explicit API, engine and, when used, parameterized-test dependencies.";

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(addJupiterDependency().getInitialValue(ctx),
                explicitJupiterDependency("junit-jupiter-params", "org.junit.jupiter.params..*", "5.x", null).getInitialValue(ctx));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        // The scanner sees the sources before `MigrateJUnitTestCase` rewrites them, so a
        // codebase of `junit.framework.TestCase` subclasses uses no `org.junit` type yet.
        TreeVisitor<?, ExecutionContext> usesJUnit4 = addJupiterDependency("org.junit..*").getScanner(acc.jupiter);
        TreeVisitor<?, ExecutionContext> usesJUnit3 = addJupiterDependency("junit.framework..*").getScanner(acc.jupiter);
        TreeVisitor<?, ExecutionContext> usesJupiterParams = explicitJupiterDependency(
                "junit-jupiter-params", "org.junit.jupiter.params..*", "5.x", null).getScanner(acc.parameters);
        TreeVisitor<?, ExecutionContext> usesJUnit4Params = explicitJupiterDependency(
                "junit-jupiter-params", "org.junit.runners.Parameterized*", "5.x", null).getScanner(acc.parameters);
        TreeVisitor<?, ExecutionContext> usesJUnitParams = explicitJupiterDependency(
                "junit-jupiter-params", "junitparams..*", "5.x", null).getScanner(acc.parameters);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                usesJUnit4.visit(tree, ctx);
                usesJUnit3.visit(tree, ctx);
                usesJupiterParams.visit(tree, ctx);
                usesJUnit4Params.visit(tree, ctx);
                usesJUnitParams.visit(tree, ctx);
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        AddJupiterGradle gv = new AddJupiterGradle(acc.jupiter);
        AddJupiterMaven mv = new AddJupiterMaven(acc);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx, Cursor parent) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (gv.isAcceptable(s, ctx)) {
                    s = (SourceFile) gv.visitNonNull(s, ctx);
                }
                if (mv.isAcceptable(s, ctx)) {
                    s = (SourceFile) mv.visitNonNull(s, ctx);
                }
                return s;
            }
        };
    }

    private static AddDependency addJupiterDependency() {
        return addJupiterDependency("org.junit..*");
    }

    private static AddDependency addJupiterDependency(String onlyIfUsing) {
        return new AddDependency("org.junit.jupiter", "junit-jupiter", "5.x", null,
                onlyIfUsing, null, null, null, null, null,
                null, null, null, null);
    }

    private static AddDependency explicitJupiterDependency(String artifactId, String onlyIfUsing, String version,
                                                            @Nullable String scope) {
        return new AddDependency("org.junit.jupiter", artifactId, version, null,
                onlyIfUsing, null, null, null, null, scope,
                null, null, null, false);
    }

    @Value
    public static class Accumulator {
        AddDependency.Accumulator jupiter;
        AddDependency.Accumulator parameters;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class AddJupiterGradle extends GroovyIsoVisitor<ExecutionContext> {
        AddDependency.Accumulator acc;

        @Override
        public G.CompilationUnit visitCompilationUnit(G.CompilationUnit t, ExecutionContext ctx) {
            Optional<GradleProject> maybeGp = t.getMarkers().findFirst(GradleProject.class);
            if (!maybeGp.isPresent()) {
                return t;
            }
            GradleProject gp = maybeGp.get();
            GradleDependencyConfiguration trc = gp.getConfiguration("testRuntimeClasspath");
            if (trc == null) {
                return t;
            }
            ResolvedDependency jupiterApi = trc.findResolvedDependency("org.junit.jupiter", "junit-jupiter-api");
            if (jupiterApi == null) {
                t = (G.CompilationUnit) addJupiterDependency().getVisitor(acc)
                        .visitNonNull(t, ctx);
            }

            return t;
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class AddJupiterMaven extends MavenIsoVisitor<ExecutionContext> {
        Accumulator acc;

        @Override
        public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
            Xml.Document d = document;
            List<ResolvedDependency> jupiterApi = getResolutionResult().findDependencies("org.junit.jupiter", "junit-jupiter-api", null);
            if (!runsDependencyAnalysis()) {
                return jupiterApi.isEmpty() ? (Xml.Document) addJupiterDependency().getVisitor(acc.jupiter)
                        .visitNonNull(d, ctx) : d;
            }

            // The aggregate supplies these transitively, which dependency:analyze reports as undeclared.
            String version = jupiterApi.isEmpty() ? "5.x" : jupiterApi.get(0).getVersion();
            // A project may intentionally keep the aggregate alongside direct APIs for its analyzer.
            boolean apiDeclared = declaresDependency(d, "junit-jupiter-api") ||
                    jupiterApi.stream().anyMatch(dependency -> dependency.getDepth() == 0);
            boolean replaceAggregate = !apiDeclared && declaresDependency(d, "junit-jupiter");
            d = (Xml.Document) explicitJupiterDependency("junit-jupiter-api", "org.junit..*", version, null)
                    .getVisitor(acc.jupiter).visitNonNull(d, ctx);
            if (replaceAggregate || jupiterApi.isEmpty()) {
                d = (Xml.Document) explicitJupiterDependency("junit-jupiter-engine", "org.junit..*", version, "test")
                        .getVisitor(acc.jupiter).visitNonNull(d, ctx);
            }
            List<ResolvedDependency> parameters = getResolutionResult().findDependencies("org.junit.jupiter", "junit-jupiter-params", null);
            String parameterVersion = parameters.isEmpty() ? version : parameters.get(0).getVersion();
            d = (Xml.Document) explicitJupiterDependency("junit-jupiter-params", "org.junit.jupiter.params..*", parameterVersion, null)
                    .getVisitor(acc.parameters).visitNonNull(d, ctx);
            if (replaceAggregate && declaresDependency(d, "junit-jupiter-api") && declaresDependency(d, "junit-jupiter-engine")) {
                d = (Xml.Document) new RemoveDependency("org.junit.jupiter", "junit-jupiter", null)
                        .getVisitor().visitNonNull(d, ctx);
            }
            return d;
        }

        private boolean runsDependencyAnalysis() {
            return getResolutionResult().getPom().getPlugins().stream().anyMatch(plugin ->
                    "org.apache.maven.plugins".equals(plugin.getGroupId()) &&
                            "maven-dependency-plugin".equals(plugin.getArtifactId()) &&
                            plugin.getExecutions().stream().anyMatch(execution -> execution.getGoals() != null &&
                                    (execution.getGoals().contains("analyze") || execution.getGoals().contains("analyze-only"))));
        }

        private static boolean declaresDependency(Xml.Document document, String artifactId) {
            return document.getRoot().getChild("dependencies").map(dependencies ->
                    dependencies.getChildren("dependency").stream().anyMatch(dependency ->
                            "org.junit.jupiter".equals(dependency.getChildValue("groupId").orElse(null)) &&
                                    artifactId.equals(dependency.getChildValue("artifactId").orElse(null))))
                    .orElse(false);
        }
    }
}
