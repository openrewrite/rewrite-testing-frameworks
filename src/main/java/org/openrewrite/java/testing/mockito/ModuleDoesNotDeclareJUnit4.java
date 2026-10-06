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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;

import java.util.HashSet;
import java.util.Set;

public class ModuleDoesNotDeclareJUnit4 extends ScanningRecipe<Set<JavaProject>> {

    @Getter
    final String displayName = "Module does not declare JUnit 4";

    @Getter
    final String description = "Searches for the sources of modules that do not declare `junit:junit` themselves, " +
            "whether or not another dependency brings it in. Meant as a precondition: adding a dependency " +
            "to a Maven module that already declares it upgrades the declared version.";

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> declaringJUnit4) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                tree.getMarkers().findFirst(MavenResolutionResult.class).ifPresent(mrr -> {
                    if (mrr.findDependencies("junit", "junit", null).stream().anyMatch(ResolvedDependency::isDirect)) {
                        tree.getMarkers().findFirst(JavaProject.class).ifPresent(declaringJUnit4::add);
                    }
                });
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> declaringJUnit4) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                return declaringJUnit4.contains(project) ? tree : SearchResult.found(tree);
            }
        };
    }
}
