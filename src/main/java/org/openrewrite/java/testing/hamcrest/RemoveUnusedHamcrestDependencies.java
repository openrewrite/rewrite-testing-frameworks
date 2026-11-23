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
package org.openrewrite.java.testing.hamcrest;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;

/**
 * Removes the Hamcrest dependencies once no source file of the repository uses Hamcrest. Whether a module uses a
 * dependency it declares is no guide, since a dependency declared in a parent POM, which has no sources of its own, is
 * used by the modules that inherit it.
 */
public class RemoveUnusedHamcrestDependencies extends ScanningRecipe<AtomicBoolean> {

    private static final List<String> ARTIFACTS = asList("hamcrest", "hamcrest-core", "hamcrest-library",
            "hamcrest-all", "java-hamcrest", "hamcrest-junit", "hamcrest-integration");

    @Getter
    final String displayName = "Remove the Hamcrest dependencies once nothing uses Hamcrest";

    @Getter
    final String description = "Remove the Hamcrest dependencies from Maven and Gradle builds once no source file of " +
            "the repository uses Hamcrest, and the exclusions of Hamcrest from other dependencies, such as the " +
            "`hamcrest-core` that JUnit 4 runs with, in the builds they are removed from.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean usesHamcrest) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!usesHamcrest.get() && tree instanceof JavaSourceFile &&
                    new UsesType<>("org.hamcrest..*", true).visit(tree, ctx) != tree) {
                    usesHamcrest.set(true);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean usesHamcrest) {
        if (usesHamcrest.get()) {
            return TreeVisitor.noop();
        }
        List<TreeVisitor<?, ExecutionContext>> removals = new ArrayList<>();
        List<TreeVisitor<?, ExecutionContext>> exclusions = new ArrayList<>();
        for (String artifact : ARTIFACTS) {
            removals.add(new org.openrewrite.maven.RemoveDependency("org.hamcrest", artifact, null).getVisitor());
            removals.add(new org.openrewrite.gradle.RemoveDependency("org.hamcrest", artifact, null).getVisitor());
            exclusions.add(new org.openrewrite.maven.RemoveExclusion("*", "*", "org.hamcrest", artifact, null).getVisitor());
            exclusions.add(new org.openrewrite.gradle.RemoveExclusion("org.hamcrest", artifact).getVisitor());
        }
        // an exclusion of the whole group, such as `exclude group: 'org.hamcrest'`
        exclusions.add(new org.openrewrite.maven.RemoveExclusion("*", "*", "org.hamcrest", "*", null).getVisitor());
        exclusions.add(new org.openrewrite.gradle.RemoveExclusion("org.hamcrest", null).getVisitor());
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                Tree t = apply(removals, tree, ctx);
                // a build that supplied its own Hamcrest excluded the one of a dependency, such as the
                // `hamcrest-core` that JUnit 4 runs with, which it gets back once nothing else supplies it
                return t == tree ? t : apply(exclusions, t, ctx);
            }
        };
    }

    private static @Nullable Tree apply(List<TreeVisitor<?, ExecutionContext>> visitors, @Nullable Tree tree, ExecutionContext ctx) {
        Tree t = tree;
        for (TreeVisitor<?, ExecutionContext> visitor : visitors) {
            if (t instanceof SourceFile && visitor.isAcceptable((SourceFile) t, ctx)) {
                t = visitor.visit(t, ctx);
            }
        }
        return t;
    }
}
