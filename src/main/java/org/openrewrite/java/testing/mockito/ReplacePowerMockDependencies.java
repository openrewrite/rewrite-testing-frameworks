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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.tree.Dependency;
import org.openrewrite.maven.tree.ManagedDependency;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.maven.tree.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;

public class ReplacePowerMockDependencies extends ScanningRecipe<ReplacePowerMockDependencies.Accumulator> {

    @Getter
    final String displayName = "Replace PowerMock dependencies with Mockito equivalents";

    @Getter
    final String description = "Replaces PowerMock API dependencies with `mockito-inline` when `mockStatic()`, " +
            "`whenNew()`, or `@PrepareForTest` usage is detected, or `mockito-core` otherwise. PowerMock features " +
            "like static mocking, constructor mocking, and final class mocking require the inline mock maker " +
            "which is bundled in `mockito-inline` for Mockito 3.x/4.x.";

    static class Accumulator {
        Map<JavaProject, Boolean> needsInlineMocking = new HashMap<>();
        Set<JavaProject> withJavaSources = new HashSet<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    private static final String PREPARE_FOR_TEST = "org.powermock.core.classloader.annotations.PrepareForTest";

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        MethodMatcher mockStaticMatcher = new MethodMatcher("org.powermock.api.mockito.PowerMockito mockStatic(..)");
        MethodMatcher whenNewMatcher = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(..)");
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof JavaSourceFile) {
                    JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                    acc.withJavaSources.add(project);
                    if (Boolean.TRUE.equals(acc.needsInlineMocking.get(project))) {
                        return tree;
                    }
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    for (JavaType.Method type : sourceFile.getTypesInUse().getUsedMethods()) {
                        if (mockStaticMatcher.matches(type) || whenNewMatcher.matches(type)) {
                            acc.needsInlineMocking.put(project, true);
                            return tree;
                        }
                    }
                    for (JavaType type : sourceFile.getTypesInUse().getTypesInUse()) {
                        if (type instanceof JavaType.FullyQualified &&
                                PREPARE_FOR_TEST.equals(((JavaType.FullyQualified) type).getFullyQualifiedName())) {
                            acc.needsInlineMocking.put(project, true);
                            return tree;
                        }
                    }
                }
                return tree;
            }
        };
    }

    /// A cycle scans every source file before it edits any, so `RemoveUnusedProperties` cannot see the
    /// `${powermock.version}` references vanish until the cycle after this recipe removes the dependencies that
    /// held them. Only a leaf recipe's flag is consulted, so this cannot be declared on the enclosing YAML.
    @Override
    public boolean causesAnotherCycle() {
        return true;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                String targetArtifact = needsInlineMocking(tree, project, acc) ? "mockito-inline" : "mockito-core";
                // Renaming onto an artifact the pom already declares or manages would list it twice, which Maven
                // rejects whatever the scopes, so the existing entry is kept and a later step of the migration
                // removes the PowerMock one. Mockito 5 mocks inline by default, so nothing needs adding to it.
                if (declaresDependency(tree, targetArtifact) || resolvesMockito5(tree)) {
                    return tree;
                }
                Boolean changeManagedDependency = managesDependency(tree, targetArtifact) ? false : null;
                doAfterVisit(new ChangeDependency(
                        "org.powermock", "powermock-api-mockito",
                        "org.mockito", targetArtifact, "3.x",
                        null, null, changeManagedDependency).getVisitor());
                doAfterVisit(new ChangeDependency(
                        "org.powermock", "powermock-api-mockito2",
                        "org.mockito", targetArtifact, "3.x",
                        null, null, changeManagedDependency).getVisitor());
                return tree;
            }
        };
    }

    /// Only what stays once PowerMock is removed counts, so the tree is walked from the other direct dependencies.
    /// PowerMock's own `mockito-core` can also win Maven's mediation over a newer one while it is still a dependency,
    /// so any `org.mockito` artifact at 5 or later is taken as the sign, as all are released in step with `mockito-core`.
    private static boolean resolvesMockito5(Tree tree) {
        List<ResolvedDependency> direct = null;
        MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        if (mrr != null) {
            direct = new ArrayList<>();
            for (ResolvedDependency dependency : mrr.getDependencies().getOrDefault(Scope.Test, emptyList())) {
                if (dependency.isDirect()) {
                    direct.add(dependency);
                }
            }
        } else {
            GradleProject gp = tree.getMarkers().findFirst(GradleProject.class).orElse(null);
            GradleDependencyConfiguration testRuntime = gp == null ? null : gp.getConfiguration("testRuntimeClasspath");
            if (testRuntime != null) {
                direct = testRuntime.getDirectResolved();
            }
        }
        if (direct != null) {
            Set<ResolvedDependency> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ResolvedDependency dependency : direct) {
                if (!"org.powermock".equals(dependency.getGroupId()) && reachesMockito5(dependency, seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean reachesMockito5(ResolvedDependency dependency, Set<ResolvedDependency> seen) {
        if (!seen.add(dependency)) {
            return false;
        }
        if ("org.mockito".equals(dependency.getGroupId()) && majorVersion(dependency.getVersion()) >= 5) {
            return true;
        }
        for (ResolvedDependency transitive : dependency.getDependencies()) {
            if (reachesMockito5(transitive, seen)) {
                return true;
            }
        }
        return false;
    }

    private static int majorVersion(String version) {
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(version.substring(0, end));
    }

    private static boolean declaresDependency(Tree tree, String mockitoArtifact) {
        MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        if (mrr == null) {
            return false;
        }
        ResolvedPom pom = mrr.getPom();
        for (Dependency dependency : pom.getRequested().getDependencies()) {
            if ("org.mockito".equals(pom.getValue(dependency.getGroupId())) &&
                mockitoArtifact.equals(pom.getValue(dependency.getArtifactId()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean managesDependency(Tree tree, String mockitoArtifact) {
        MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        if (mrr == null) {
            return false;
        }
        ResolvedPom pom = mrr.getPom();
        for (ManagedDependency managed : pom.getRequested().getDependencyManagement()) {
            if (managed instanceof ManagedDependency.Defined &&
                "org.mockito".equals(pom.getValue(((ManagedDependency.Defined) managed).getGroupId())) &&
                mockitoArtifact.equals(pom.getValue(((ManagedDependency.Defined) managed).getArtifactId()))) {
                return true;
            }
        }
        return false;
    }

    /// Parent and aggregator poms typically manage the version of PowerMock for their modules, so these have to
    /// agree with their modules on the Mockito artifact that replaces it; otherwise modules end up with a Mockito
    /// dependency that has no managed version.
    private static boolean needsInlineMocking(Tree tree, @Nullable JavaProject project, Accumulator acc) {
        if (Boolean.TRUE.equals(acc.needsInlineMocking.get(project))) {
            return true;
        }
        if (!acc.needsInlineMocking.containsValue(true)) {
            return false;
        }
        MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        return !acc.withJavaSources.contains(project) ||
               mrr != null && (mrr.getParent() != null || !mrr.getModules().isEmpty());
    }
}
