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
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;

public class AddJupiterMigrationSupport extends ScanningRecipe<AddDependency.Accumulator> {
    // The rule migrations that run before `HandleExternalResourceRules`, which leaves only the rules they don't convert
    private static final List<Recipe> MIGRATIONS = Arrays.asList(new EnvironmentVariables(), new SystemPropertyRulesToPioneer(), new SystemRulesToSystemStubs(), new ExpectedSystemExitToCatchSystemExit(), new TemporaryFolderToTempDir(), new UpdateMockWebServer(), new HandleExternalResourceRules());

    private static final AddDependency ADD_MIGRATION_SUPPORT = new AddDependency("org.junit.jupiter", "junit-jupiter-migrationsupport", "5.x", null, "org.junit.jupiter.migrationsupport..*", null, "org.junit.jupiter", null, null, "test", null, null, null, true);

    @Getter
    final String displayName = "Add JUnit Jupiter migrationsupport";

    @Getter
    final String description = "Adds `junit-jupiter-migrationsupport` to modules whose JUnit 4 `ExternalResource` rules the JUnit 4 to 5 migration keeps with `@ExtendWith(ExternalResourceSupport.class)`.";

    @Override
    public AddDependency.Accumulator getInitialValue(ExecutionContext ctx) {
        return ADD_MIGRATION_SUPPORT.getInitialValue(ctx);
    }

    // Scanners see the sources before any edit of this cycle, so they are fed the migrated sources instead
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AddDependency.Accumulator acc) {
        TreeVisitor<?, ExecutionContext> migrationSupport = ADD_MIGRATION_SUPPORT.getScanner(acc);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                migrationSupport.visit(migrate(tree, ctx), ctx);
                return tree;
            }
        };
    }

    private static Tree migrate(Tree tree, ExecutionContext ctx) {
        if (!(tree instanceof J.CompilationUnit) || new UsesType<>("org.junit.*Rule", false).visit(tree, ctx) == tree) {
            return tree;
        }
        Tree migrated = tree;
        for (Recipe migration : MIGRATIONS) {
            migrated = migration.getVisitor().visitNonNull(migrated, ctx);
        }
        return migrated;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AddDependency.Accumulator acc) {
        return ADD_MIGRATION_SUPPORT.getVisitor(acc);
    }
}
