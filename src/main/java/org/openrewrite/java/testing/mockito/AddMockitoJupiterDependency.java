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
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.FindTypes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AddMockitoJupiterDependency extends ScanningRecipe<AddMockitoJupiterDependency.Accumulator> {

    private static final String EXTEND_WITH_MOCKITO_EXTENSION =
            "org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)";
    private static final String JUNIT_JUPITER_TYPES = "org.junit.jupiter..*";
    private static final String[] MOCKITO_ANNOTATIONS = {
            "org.mockito.Captor",
            "org.mockito.Mock",
            "org.mockito.Spy",
            "org.mockito.InjectMocks",
    };

    @Getter
    final String displayName = "Add mockito-junit-jupiter dependency";

    @Getter
    final String description = "Adds `org.mockito:mockito-junit-jupiter` dependency if `@ExtendWith(MockitoExtension.class)` " +
            "will be added to any test class, i.e. when Mockito annotations are used in JUnit 5 tests without the extension already present.";

    static class Accumulator {
        Map<JavaProject, Boolean> needsMockitoJupiter = new HashMap<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @SuppressWarnings("unchecked")
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> hasExtendWith =
                new FindAnnotations(EXTEND_WITH_MOCKITO_EXTENSION, false).getVisitor();
        TreeVisitor<?, ExecutionContext>[] hasAnyMockitoAnnotation = Arrays.stream(MOCKITO_ANNOTATIONS)
                .map(a -> new FindAnnotations(a, false).getVisitor())
                .toArray(TreeVisitor[]::new);

        return Preconditions.check(
                Preconditions.and(
                        Preconditions.or(hasAnyMockitoAnnotation),
                        new FindTypes(JUNIT_JUPITER_TYPES, false).getVisitor(),
                        Preconditions.not(hasExtendWith)
                ),
                new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public Tree preVisit(Tree tree, ExecutionContext ctx) {
                        stopAfterPreVisit();
                        JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                        acc.needsMockitoJupiter.put(project, true);
                        return tree;
                    }
                }
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.needsMockitoJupiter.isEmpty()) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                if (Boolean.TRUE.equals(acc.needsMockitoJupiter.get(project))) {
                    doAfterVisit(new AddDependency(
                            "org.mockito",
                            "mockito-junit-jupiter",
                            "3.x",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "test",
                            null,
                            null,
                            null,
                            true
                    ).getVisitor());
                }
                return tree;
            }
        };
    }
}
