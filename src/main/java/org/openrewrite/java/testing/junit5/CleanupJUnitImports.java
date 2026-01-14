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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

public class CleanupJUnitImports extends Recipe {
    @Getter
    final String displayName = "Cleanup JUnit imports";

    @Getter
    final String description = "Removes unused `org.junit` import symbols.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>("org.junit.*", false),
                new UsesType<>("junit.*", false)
        ), new CleanupJUnitImportsVisitor());
    }

    public static class CleanupJUnitImportsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J preVisit(J tree, ExecutionContext ctx) {
            stopAfterPreVisit();
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile c = (JavaSourceFile) tree;
                for (J.Import imp : c.getImports()) {
                    String packageName = imp.getPackageName();
                    if (packageName.startsWith("junit") || (packageName.startsWith("org.junit") && !packageName.contains("jupiter"))) {
                        maybeRemoveImport(imp.getTypeName());
                    }
                }
            }
            return tree;
        }
    }
}
