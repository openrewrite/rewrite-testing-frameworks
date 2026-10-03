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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.search.FindFieldsOfType;
import org.openrewrite.java.tree.J;

import java.util.Set;

public class AddMockitoJupiterForRules extends ScanningRecipe<AddDependency.Accumulator> {
    private static final AnnotationMatcher RULE = new AnnotationMatcher("@org.junit.Rule");

    @Getter
    final String displayName = "Add Mockito Jupiter integration for migrated Mockito rules";

    @Getter
    final String description = "Adds the Mockito Jupiter dependency for annotated MockitoRule and MockitoTestRule fields, " +
            "matching the project's Mockito version even when Mockito is already up to date.";

    private static AddDependency dependency() {
        return new AddDependency("org.mockito", "mockito-junit-jupiter", "4.x", null,
                "org.mockito.junit.Mockito*Rule", null, "org.mockito", null, null, "test",
                null, null, null, true);
    }

    @Override
    public AddDependency.Accumulator getInitialValue(ExecutionContext ctx) {
        return dependency().getInitialValue(ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AddDependency.Accumulator acc) {
        TreeVisitor<?, ExecutionContext> scanner = dependency().getScanner(acc);
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                Set<J.VariableDeclarations> fields = FindFieldsOfType.find(cu, "org.mockito.junit.MockitoRule");
                fields.addAll(FindFieldsOfType.find(cu, "org.mockito.junit.MockitoTestRule"));
                if (fields.stream().anyMatch(field -> field.getLeadingAnnotations().stream().anyMatch(RULE::matches))) {
                    scanner.visit(cu, ctx);
                }
                return cu;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AddDependency.Accumulator acc) {
        return dependency().getVisitor(acc);
    }
}
