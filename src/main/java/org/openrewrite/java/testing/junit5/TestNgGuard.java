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
package org.openrewrite.java.testing.junit5;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.dependencies.search.ModuleHasDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TestNgGuard extends ScanningRecipe<Set<JavaProject>> {
    @Override
    public String getDisplayName() {
        return "Find `TestNG`-free Maven / Gradle and Java files";
    }

    @Override
    public String getDescription() {
        return "Meant to be used as a precondition, it will return results for Maven / Gradle and Java files " +
                "that are part of a project that does not have TestNG dependencies nor usage of TestNG classes.";
    }

    @Override
    public Set<JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<JavaProject> acc) {
        return new ModuleHasDependency("org.testng", "testng*", null, null).getScanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<JavaProject> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Optional<JavaProject> maybeJp = tree.getMarkers().findFirst(JavaProject.class);
                return maybeJp.isPresent() && acc.contains(maybeJp.get()) ? tree : SearchResult.found(tree);
            }
        };
    }
}
