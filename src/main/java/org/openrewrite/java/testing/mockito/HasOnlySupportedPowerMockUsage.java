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
import org.openrewrite.*;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.concurrent.atomic.AtomicBoolean;

public class HasOnlySupportedPowerMockUsage extends ScanningRecipe<AtomicBoolean> {

    @Getter
    final String displayName = "Repository has only PowerMock usage that can be migrated to Mockito";

    @Getter
    final String description = "Matches all source files of a repository whose PowerMock usage can be migrated to " +
            "Mockito in full. Repositories that use PowerMock features without a Mockito equivalent do not match, so " +
            "that they can be left on PowerMock rather than partially migrated. The decision is made per repository " +
            "rather than per module, as modules typically share PowerMock versions managed by a parent.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean unsupported) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (unsupported.get()) {
                    return tree;
                }
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    if (usesPowerMock(sourceFile) && !UnsupportedPowerMockUsage.find(sourceFile, ctx).isEmpty()) {
                        unsupported.set(true);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean unsupported) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                return unsupported.get() ? tree : SearchResult.found(tree);
            }
        };
    }

    private static boolean usesPowerMock(JavaSourceFile sourceFile) {
        for (JavaType type : sourceFile.getTypesInUse().getTypesInUse()) {
            if (UnsupportedPowerMockUsage.isPowerMock(TypeUtils.asFullyQualified(type))) {
                return true;
            }
        }
        return false;
    }
}
