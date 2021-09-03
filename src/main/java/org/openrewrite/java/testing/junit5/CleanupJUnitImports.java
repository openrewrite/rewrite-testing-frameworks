/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class CleanupJUnitImports extends Recipe {
    @Override
    public String getDisplayName() {
        return "Cleanup JUnit imports";
    }

    @Override
    public String getDescription() {
        return "Removes unused `org.junit` import symbols.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CleanupJUnitImportsVisitor();
    }

    @Nullable
    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                doAfterVisit(new UsesType<>("org.junit.*"));
                doAfterVisit(new UsesType<>("junit.*"));
                return cu;
            }
        };
    }

    public static class CleanupJUnitImportsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            for (J.Import im : cu.getImports()) {
                String packageName = im.getPackageName();
                if (packageName.startsWith("junit") || (packageName.startsWith("org.junit") && !packageName.contains("jupiter"))) {
                    maybeRemoveImport(im.getTypeName());
                }
            }
            return cu;
        }
    }
}
