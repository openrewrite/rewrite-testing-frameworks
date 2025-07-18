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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

/**
 * Removes unused "org.mockito" imports.
 */
public class CleanupPowerMockImports extends Recipe {

    @Override
    public String getDisplayName() {
        return "Cleanup PowerMock imports";
    }

    @Override
    public String getDescription() {
        return "Removes unused `org.powermock` import symbols.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.powermock..*", false),
                new CleanupPowerMockImportsVisitor());
    }

    private static class CleanupPowerMockImportsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public @Nullable J preVisit(J tree, ExecutionContext ctx) {
            stopAfterPreVisit();
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile sf = (JavaSourceFile) tree;
                for (J.Import _import : sf.getImports()) {
                    if (_import.getPackageName().startsWith("org.powermock")) {
                        maybeRemoveImport(_import.getPackageName() + "." + _import.getClassName());
                    }
                }
            }
            return tree;
        }
    }
}
