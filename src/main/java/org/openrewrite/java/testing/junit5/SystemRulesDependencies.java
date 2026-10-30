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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.RemoveDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openrewrite.java.testing.junit5.SystemRules.PACKAGE;

public class SystemRulesDependencies extends ScanningRecipe<SystemRulesDependencies.Accumulator> {
    private static final List<Recipe> MIGRATIONS = Arrays.asList(new EnvironmentVariables(), new SystemPropertyRulesToPioneer(), new SystemRulesToSystemStubs(), new ExpectedSystemExitToCatchSystemExit());

    private static final AddDependency ADD_SYSTEM_STUBS = new AddDependency("uk.org.webcompere", "system-stubs-jupiter", "2.x", null, "uk.org.webcompere.systemstubs..*", null, null, null, null, null, null, null, null, true);
    private static final AddDependency ADD_PIONEER = new AddDependency("org.junit-pioneer", "junit-pioneer", "2.x", null, "org.junitpioneer..*", null, null, null, null, null, null, null, null, true);
    private static final UpgradeDependencyVersion UPGRADE_PIONEER = new UpgradeDependencyVersion("org.junit-pioneer", "junit-pioneer", "2.x", null, null, null);
    private static final RemoveDependency REMOVE_SYSTEM_RULES = new RemoveDependency("com.github.stefanbirkner", "system-rules", PACKAGE + "..*", null, null);

    @Getter
    final String displayName = "Update dependencies for the System Rules migration";

    @Getter
    final String description = "Adds System Stubs and JUnit Pioneer where the System Rules migration introduces them, upgrades JUnit Pioneer to 2.x in modules that get its `@RestoreSystemProperties`, and removes System Rules from modules that no longer use it.";

    public static class Accumulator {
        final AddDependency.Accumulator systemStubs;
        final AddDependency.Accumulator pioneer;
        final UpgradeDependencyVersion.Accumulator upgradePioneer;
        final RemoveDependency.Accumulator systemRules;
        final Set<JavaProject> restoringSystemProperties = new HashSet<>();

        Accumulator(ExecutionContext ctx) {
            systemStubs = ADD_SYSTEM_STUBS.getInitialValue(ctx);
            pioneer = ADD_PIONEER.getInitialValue(ctx);
            upgradePioneer = UPGRADE_PIONEER.getInitialValue(ctx);
            systemRules = REMOVE_SYSTEM_RULES.getInitialValue(ctx);
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(ctx);
    }

    // Scanners see the sources before any edit of this cycle, so they are fed the migrated sources instead
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> systemStubs = ADD_SYSTEM_STUBS.getScanner(acc.systemStubs);
        TreeVisitor<?, ExecutionContext> pioneer = ADD_PIONEER.getScanner(acc.pioneer);
        TreeVisitor<?, ExecutionContext> upgradePioneer = UPGRADE_PIONEER.getScanner(acc.upgradePioneer);
        TreeVisitor<?, ExecutionContext> systemRules = REMOVE_SYSTEM_RULES.getScanner(acc.systemRules);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                Tree migrated = migrate(tree, ctx);
                systemStubs.visit(migrated, ctx);
                pioneer.visit(migrated, ctx);
                upgradePioneer.visit(tree, ctx);
                systemRules.visit(migrated, ctx);
                if (new UsesType<>("org.junitpioneer.jupiter.RestoreSystemProperties", false).visit(migrated, ctx) != migrated) {
                    migrated.getMarkers().findFirst(JavaProject.class).ifPresent(acc.restoringSystemProperties::add);
                }
                return tree;
            }
        };
    }

    private static Tree migrate(Tree tree, ExecutionContext ctx) {
        if (!(tree instanceof J.CompilationUnit) || new UsesType<>(PACKAGE + "..*", false).visit(tree, ctx) == tree) {
            return tree;
        }
        Tree migrated = tree;
        for (Recipe migration : MIGRATIONS) {
            migrated = migration.getVisitor().visitNonNull(migrated, ctx);
        }
        return migrated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> systemStubs = ADD_SYSTEM_STUBS.getVisitor(acc.systemStubs);
        TreeVisitor<?, ExecutionContext> pioneer = ADD_PIONEER.getVisitor(acc.pioneer);
        TreeVisitor<?, ExecutionContext> upgradePioneer = UPGRADE_PIONEER.getVisitor(acc.upgradePioneer);
        TreeVisitor<?, ExecutionContext> systemRules = REMOVE_SYSTEM_RULES.getVisitor(acc.systemRules);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Tree t = systemStubs.visit(tree, ctx);
                t = pioneer.visit(t, ctx);
                if (t != null && t.getMarkers().findFirst(JavaProject.class).filter(acc.restoringSystemProperties::contains).isPresent()) {
                    t = upgradePioneer.visit(t, ctx);
                }
                return systemRules.visit(t, ctx);
            }
        };
    }
}
