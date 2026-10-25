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
package org.openrewrite.java.testing.junit;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.dependencies.RemoveDependency;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.testing.junit5.HandleExternalResourceRules;
import org.openrewrite.java.tree.J;
import org.openrewrite.maven.RemoveManagedDependency;

import static org.openrewrite.Singleton.singleton;

@EqualsAndHashCode(callSuper = false)
public class RemoveJupiterMigrationSupport extends ScanningRecipe<RemoveJupiterMigrationSupport.Accumulator> {
    private static final String MIGRATION_SUPPORT = "org.junit.jupiter.migrationsupport..*";
    private static final RemoveDependency REMOVE_DEPENDENCY = new RemoveDependency("org.junit.jupiter", "junit-jupiter-migrationsupport", MIGRATION_SUPPORT, null, null);
    private static final RemoveManagedDependency REMOVE_MANAGED_DEPENDENCY = new RemoveManagedDependency("org.junit.jupiter", "junit-jupiter-migrationsupport", null);

    @Getter
    final String displayName = "Remove JUnit Jupiter migrationsupport";

    @Getter
    final String description = "Removes `junit-jupiter-migrationsupport` from modules that don't use its `@EnableRuleMigrationSupport`, `ExternalResourceSupport` or other types, and its managed version once no module uses them.";

    public static class Accumulator {
        final RemoveDependency.Accumulator dependency;
        boolean inUse;

        Accumulator(ExecutionContext ctx) {
            dependency = REMOVE_DEPENDENCY.getInitialValue(ctx);
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(ctx);
    }

    // Scanners see the sources before any edit of this cycle, so they would miss the `ExternalResourceSupport` that the
    // JUnit 4 migration adds earlier in `JUnit5BestPractices`; this errs on keeping migrationsupport for JUnit 4 rules
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> dependency = REMOVE_DEPENDENCY.getScanner(acc.dependency);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                Tree migrated = tree instanceof J.CompilationUnit ? new HandleExternalResourceRules().getVisitor().visitNonNull(tree, ctx) : tree;
                dependency.visit(migrated, ctx);
                if (!acc.inUse && new UsesType<>(MIGRATION_SUPPORT, true).visit(migrated, ctx) != migrated) {
                    acc.inUse = true;
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> dependency = REMOVE_DEPENDENCY.getVisitor(acc.dependency);
        TreeVisitor<?, ExecutionContext> managedDependency = REMOVE_MANAGED_DEPENDENCY.getVisitor();
        return singleton(this, new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Tree t = dependency.visit(tree, ctx);
                return acc.inUse ? t : managedDependency.visit(t, ctx);
            }
        });
    }
}
