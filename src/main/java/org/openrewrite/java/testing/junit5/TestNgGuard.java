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

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.dependencies.search.DoesNotIncludeDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TestNgGuard extends ScanningRecipe<TestNgGuard.Accumulator> {
    @Override
    public String getDisplayName() {
        return "Find `TestNG`-free Maven / Gradle and Java files";
    }

    @Override
    public String getDescription() {
        return "Meant to be used as a precondition, it will return results for Maven / Gradle and Java files " +
                "that are part of a project that does not have TestNG dependencies nor usage of TestNG classes.";
    }

    @Value
    public static class Accumulator {
        Set<JavaProject> projectsWithoutTestNgDependency;
        Set<JavaProject> projectsWithoutTestNgTypeUsage;
        Set<JavaProject> projectsWithTestNgDependency;
        Set<JavaProject> projectsWithTestNgTypeUsage;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final TreeVisitor<?, ExecutionContext> ut = new UsesType<>("org.testng..*", true);
            private final TreeVisitor<?, ExecutionContext> dnid = new DoesNotIncludeDependency("org.testng", "testng*", null, null, null).getVisitor();

            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree != null;
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile s = (SourceFile) tree;
                if (dnid.isAcceptable(s, ctx)) {
                    Tree after = dnid.visit(tree, ctx);
                    if (after != tree) {
                        tree.getMarkers().findFirst(JavaProject.class).ifPresent(acc.projectsWithoutTestNgDependency::add);
                    } else {
                        tree.getMarkers().findFirst(JavaProject.class).ifPresent(acc.projectsWithTestNgDependency::add);
                    }
                } else if (ut.isAcceptable(s, ctx)) {
                    Tree after = ut.visit(tree, ctx);
                    // Note: Uses `UsesType` for inverted checking because `DoesNotUseType` will mark non Java-code files as well
                    if (after == tree) {
                        tree.getMarkers().findFirst(JavaProject.class).ifPresent(acc.projectsWithoutTestNgTypeUsage::add);
                    } else {
                        tree.getMarkers().findFirst(JavaProject.class).ifPresent(acc.projectsWithTestNgTypeUsage::add);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final TreeVisitor<?, ExecutionContext> ut = new UsesType<>("org.testng..*", true);
            private final TreeVisitor<?, ExecutionContext> dnid = new DoesNotIncludeDependency("org.testng", "testng*", null, null, null).getVisitor();

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return ut.isAcceptable(sourceFile, ctx) || dnid.isAcceptable(sourceFile, ctx);
            }

            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree != null;
                Optional<JavaProject> maybeJp = tree.getMarkers().findFirst(JavaProject.class);
                if (!maybeJp.isPresent()) {
                    return tree;
                }
                JavaProject jp = maybeJp.get();
                boolean noTestNgDep = acc.getProjectsWithoutTestNgDependency().contains(jp);
                boolean noTestNgUsage = acc.getProjectsWithoutTestNgTypeUsage().contains(jp);
                boolean testNgDep = acc.getProjectsWithTestNgDependency().contains(jp);
                boolean testNgUsage = acc.getProjectsWithTestNgTypeUsage().contains(jp);
                // if any TestNG, break
                if (testNgUsage || testNgDep) {
                    return tree;
                }
                // if not a scanned project
                if (!noTestNgDep && !noTestNgUsage) {
                    return tree;
                }
                boolean depFreeOrVacuous = noTestNgDep || acc.getProjectsWithoutTestNgDependency().isEmpty();
                boolean usageFreeOrVacuous = noTestNgUsage || acc.getProjectsWithoutTestNgTypeUsage().isEmpty();
                if (depFreeOrVacuous && usageFreeOrVacuous) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }
}
