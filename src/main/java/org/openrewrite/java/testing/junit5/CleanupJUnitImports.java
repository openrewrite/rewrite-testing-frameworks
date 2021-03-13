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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;

import static org.openrewrite.Tree.randomId;

/**
 * Orders imports and removes unused imports from classes which import symbols from the "org.junit" package.
 */
public class CleanupJUnitImports extends Recipe {
    private static final J.Block EMPTY_BODY = new J.Block(randomId(), Space.EMPTY, Markers.EMPTY,
            new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
            Collections.emptyList(), Space.EMPTY);

    @Override
    public String getDisplayName() {
        return "Cleanup JUnit Imports";
    }

    @Override
    public String getDescription() {
        return "Orders imports and removes unused org.junit import symbols";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CleanupJUnitImportsVisitor();
    }

    public static class CleanupJUnitImportsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            J.CompilationUnit c = cu;

            List<J.Import> imports = ListUtils.map(c.getImports(), im -> {
                String packageName = im.getPackageName();
                if (packageName.startsWith("org.junit") && !packageName.contains("jupiter")) {
                    return null;
                }
                if (packageName.startsWith("junit")) {
                    return null;
                }
                return im;
            });

            //noinspection NewObjectEquality
            if (imports != c.getImports()) {
                J.CompilationUnit temp = c.withClasses(Collections.emptyList());
                c = maybeAutoFormat(temp, temp.withImports(imports), ctx).withClasses(c.getClasses());

                Cursor cursor = new Cursor(null, c);
                if(c.getPackageDeclaration() != null) {
                    c = c.withPackageDeclaration(autoFormat(c.getPackageDeclaration(), ctx, cursor));
                }
                c = c.withClasses(ListUtils.mapFirst(c.getClasses(), cd ->
                        autoFormat(cd.withBody(EMPTY_BODY), ctx, cursor).withBody(cd.getBody())));
            }

            return c;
        }
    }
}
