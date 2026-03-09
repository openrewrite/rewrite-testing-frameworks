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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.util.HashMap;
import java.util.Map;

public class ReplacePowerMockDependencies extends ScanningRecipe<ReplacePowerMockDependencies.Accumulator> {

    @Getter
    final String displayName = "Replace PowerMock dependencies with Mockito equivalents";

    @Getter
    final String description = "Replaces PowerMock API dependencies with `mockito-inline` when `mockStatic()`, " +
            "`whenNew()`, or `@PrepareForTest` usage is detected, or `mockito-core` otherwise. PowerMock features " +
            "like static mocking, constructor mocking, and final class mocking require the inline mock maker " +
            "which is bundled in `mockito-inline` for Mockito 3.x/4.x.";

    static class Accumulator {
        Map<JavaProject, Boolean> needsInlineMocking = new HashMap<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    private static final String PREPARE_FOR_TEST = "org.powermock.core.classloader.annotations.PrepareForTest";

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        MethodMatcher mockStaticMatcher = new MethodMatcher("org.powermock.api.mockito.PowerMockito mockStatic(..)");
        MethodMatcher whenNewMatcher = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(..)");
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof JavaSourceFile) {
                    JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                    if (Boolean.TRUE.equals(acc.needsInlineMocking.get(project))) {
                        return tree;
                    }
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    for (JavaType.Method type : sourceFile.getTypesInUse().getUsedMethods()) {
                        if (mockStaticMatcher.matches(type) || whenNewMatcher.matches(type)) {
                            acc.needsInlineMocking.put(project, true);
                            return tree;
                        }
                    }
                    for (JavaType type : sourceFile.getTypesInUse().getTypesInUse()) {
                        if (type instanceof JavaType.FullyQualified &&
                                PREPARE_FOR_TEST.equals(((JavaType.FullyQualified) type).getFullyQualifiedName())) {
                            acc.needsInlineMocking.put(project, true);
                            return tree;
                        }
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                String targetArtifact = Boolean.TRUE.equals(acc.needsInlineMocking.get(project)) ?
                        "mockito-inline" : "mockito-core";
                doAfterVisit(new ChangeDependency(
                        "org.powermock", "powermock-api-mockito",
                        "org.mockito", targetArtifact, "3.x",
                        null, null, null).getVisitor());
                doAfterVisit(new ChangeDependency(
                        "org.powermock", "powermock-api-mockito2",
                        "org.mockito", targetArtifact, "3.x",
                        null, null, null).getVisitor());
                return tree;
            }
        };
    }
}
