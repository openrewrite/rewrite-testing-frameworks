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
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.concurrent.atomic.AtomicBoolean;

public class RepositoryDoesNotTargetJavaBelow11 extends ScanningRecipe<AtomicBoolean> {

    @Getter
    final String displayName = "Repository does not target Java older than 11";

    @Getter
    final String description = "Searches for every source file of a repository, unless one of its Java sources is " +
            "compiled for a Java version older than 11. Meant as a precondition for upgrading to Mockito 5, which " +
            "requires Java 11. A repository without Java sources, such as one holding only a parent POM, is found as well.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean targetsJavaBelow11) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof JavaSourceFile) {
                    tree.getMarkers().findFirst(JavaVersion.class).ifPresent(javaVersion -> {
                        if (javaVersion.getMajorVersion() < 11) {
                            targetsJavaBelow11.set(true);
                        }
                    });
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean targetsJavaBelow11) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                return !targetsJavaBelow11.get() && tree instanceof SourceFile ? SearchResult.found(tree) : tree;
            }
        };
    }
}
