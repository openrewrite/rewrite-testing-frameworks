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
package org.openrewrite.java.testing.junit5;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.RemoveDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.ExcludeDependency;
import org.openrewrite.maven.RemoveManagedDependency;
import org.openrewrite.maven.tree.MavenResolutionResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes {@code junit:junit}, its managed version and the vintage engine from each module, unless a source file of
 * that module still uses {@code junit.framework} or the other JUnit 3 packages, which only {@code junit:junit} provides
 * and only the vintage engine runs. Then the vintage engine is added instead.
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveJUnit4DependenciesUnlessJUnit3Used extends ScanningRecipe<RemoveJUnit4DependenciesUnlessJUnit3Used.Accumulator> {

    private static final String[] JUNIT3_PACKAGES = {"junit.framework.", "junit.textui.", "junit.awtui.", "junit.swingui.", "junit.runner.", "junit.extensions."};

    @Getter
    String displayName = "Remove `junit:junit` and the vintage engine unless JUnit 3 types are still used";

    @Getter
    String description = "Removes the `junit:junit` and `junit-vintage-engine` dependencies, unless a source file still uses `junit.framework` " +
                         "or the other JUnit 3 packages, such as test classes that extend a base class from a library. " +
                         "Then both stay, and the vintage engine is added if it is missing, so that Jupiter runs those tests.";

    @Value
    public static class Accumulator {
        Set<@Nullable JavaProject> withJavaSources = new HashSet<>();
        Set<@Nullable JavaProject> usingJUnit3 = new HashSet<>();
        RemoveDependency removeJUnit;
        RemoveDependency.Accumulator removeJUnitAcc;
        RemoveDependency removeVintage;
        RemoveDependency.Accumulator removeVintageAcc;
        AddDependency addVintage;
        AddDependency.Accumulator addVintageAcc;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        RemoveDependency removeJUnit = new RemoveDependency("junit", "junit", null, null, null);
        RemoveDependency removeVintage = new RemoveDependency("org.junit.vintage", "junit-vintage-engine", null, null, null);
        AddDependency addVintage = new AddDependency("org.junit.vintage", "junit-vintage-engine", "5.x", null,
                "junit.framework..*", null, null, null, null, "test",
                null, null, null, false);
        return new Accumulator(
                removeJUnit, removeJUnit.getInitialValue(ctx),
                removeVintage, removeVintage.getInitialValue(ctx),
                addVintage, addVintage.getInitialValue(ctx));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        List<TreeVisitor<?, ExecutionContext>> scanners = new ArrayList<>();
        scanners.add(acc.removeJUnit.getScanner(acc.removeJUnitAcc));
        scanners.add(acc.removeVintage.getScanner(acc.removeVintageAcc));
        scanners.add(acc.addVintage.getScanner(acc.addVintageAcc));
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    for (TreeVisitor<?, ExecutionContext> scanner : scanners) {
                        scanner.visit(tree, ctx);
                    }
                }
                return super.visit(tree, ctx);
            }

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                JavaProject project = cu.getMarkers().findFirst(JavaProject.class).orElse(null);
                acc.withJavaSources.add(project);
                for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
                    if (type instanceof JavaType.FullyQualified && isJUnit3((JavaType.FullyQualified) type)) {
                        acc.usingJUnit3.add(project);
                        break;
                    }
                }
                return cu;
            }
        };
    }

    private static boolean isJUnit3(JavaType.FullyQualified type) {
        String fqn = type.getFullyQualifiedName();
        for (String p : JUNIT3_PACKAGES) {
            if (fqn.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A parent, aggregator or root build has no sources of its own, but may declare or manage the dependency for
     * modules that still use JUnit 3.
     */
    private static boolean keepsJUnit(Tree tree, Accumulator acc) {
        JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
        if (acc.usingJUnit3.contains(project)) {
            return true;
        }
        if (acc.usingJUnit3.isEmpty()) {
            return false;
        }
        MavenResolutionResult mrr = tree.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
        return !acc.withJavaSources.contains(project) || mrr != null && !mrr.getModules().isEmpty();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> removeJUnit = acc.removeJUnit.getVisitor(acc.removeJUnitAcc);
        TreeVisitor<?, ExecutionContext> removeVintage = acc.removeVintage.getVisitor(acc.removeVintageAcc);
        TreeVisitor<?, ExecutionContext> addVintage = acc.addVintage.getVisitor(acc.addVintageAcc);
        // the version of a dependency that stays is managed where it was
        TreeVisitor<?, ExecutionContext> removeManagedJUnit = new RemoveManagedDependency("junit", "junit", null).getVisitor();
        TreeVisitor<?, ExecutionContext> removeManagedVintage = new RemoveManagedDependency("org.junit.vintage", "junit-vintage-engine", null).getVisitor();
        TreeVisitor<?, ExecutionContext> excludeVintage = new ExcludeDependency("org.junit.vintage", "junit-vintage-engine", null).getVisitor();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree != null && keepsJUnit(tree, acc)) {
                    return addVintage.visit(tree, ctx);
                }
                Tree t = removeJUnit.visit(tree, ctx);
                t = removeManagedJUnit.visit(t, ctx);
                t = removeVintage.visit(t, ctx);
                t = removeManagedVintage.visit(t, ctx);
                return excludeVintage.visit(t, ctx);
            }
        };
    }
}
