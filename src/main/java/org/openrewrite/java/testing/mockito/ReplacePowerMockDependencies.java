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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.MavenTagInsertionComparator;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.openrewrite.internal.StringUtils.matchesGlob;

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
                MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                // A child can still resolve against the parent's old coordinates during this cycle.
                boolean overrideManagedVersion = mrr != null && mrr.getParent() != null;
                doAfterVisit(new MavenIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                        Xml.Tag t = super.visitTag(tag, ctx);
                        ResolvedPom pom = getResolutionResult().getPom();
                        String artifactId = pom.getValue(tag.getChildValue("artifactId").orElse(null));
                        if (isDependencyTag() &&
                                "org.powermock".equals(pom.getValue(tag.getChildValue("groupId").orElse(null))) &&
                                matchesGlob(artifactId, "powermock*")) {
                            Scope scope = pom.getManagedScope("org.powermock", artifactId,
                                    tag.getChildValue("type").orElse(null), tag.getChildValue("classifier").orElse(null));
                            if (!tag.getChild("scope").isPresent() && scope != null && scope != Scope.Compile) {
                                // Preserve inherited scopes for coordinate changes and wildcard dependency removal.
                                t = (Xml.Tag) new AddToTagVisitor<ExecutionContext>(t,
                                        Xml.Tag.build("<scope>" + scope.name().toLowerCase(Locale.ROOT) + "</scope>"),
                                        new MavenTagInsertionComparator(t.getChildren()))
                                        .visitNonNull(t, ctx, getCursor().getParent());
                            }
                        }
                        return t;
                    }
                });
                doAfterVisit(new ChangeDependency(
                        "org.powermock", "powermock-api-mockito",
                        "org.mockito", targetArtifact, "3.x",
                        null, overrideManagedVersion, null).getVisitor());
                doAfterVisit(new ChangeDependency(
                        "org.powermock", "powermock-api-mockito2",
                        "org.mockito", targetArtifact, "3.x",
                        null, overrideManagedVersion, null).getVisitor());
                return tree;
            }
        };
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
