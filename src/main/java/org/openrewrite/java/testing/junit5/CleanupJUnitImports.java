/*
 * Copyright 2020 the original author or authors.
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

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.OrderImports;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.tree.J;

/**
 * Removes unused imports from classes which import symbols from the "org.junit" package.
 */
@AutoConfigure
public class CleanupJUnitImports extends JavaIsoRefactorVisitor {

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu) {
        boolean shouldCleanup = cu.getImports().stream()
                .anyMatch(impert -> impert.getPackageName().startsWith("org.junit"));
        if(shouldCleanup) {
            RemoveUnusedImports removeImports = new RemoveUnusedImports();
            andThen(removeImports);
        }
        return cu;
    }
}
