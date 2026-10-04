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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AddHamcrestIfUsed extends ScanningRecipe<AddDependency.Accumulator> {
    private static final MethodMatcher EXPECT_MESSAGE = new MethodMatcher("org.junit.rules.ExpectedException expectMessage(..)");
    private static final MethodMatcher EXPECT_CAUSE = new MethodMatcher("org.junit.rules.ExpectedException expectCause(..)");
    private static final MethodMatcher EXPECT_MATCHER = new MethodMatcher("org.junit.rules.ExpectedException expect(org.hamcrest.Matcher)");

    @Getter
    final String displayName = "Add `org.hamcrest:hamcrest` if it is used";

    @Getter
    final String description = "JUnit Jupiter does not include Hamcrest as a transitive dependency. " +
            "Add a direct dependency for existing Hamcrest usage or ExpectedException assertions that generate Hamcrest usage during migration.";

    @Getter
    final Set<String> tags = new HashSet<>(Arrays.asList("testing", "hamcrest", "junit"));

    @Override
    public AddDependency.Accumulator getInitialValue(ExecutionContext ctx) {
        return dependency("org.hamcrest..*").getInitialValue(ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AddDependency.Accumulator acc) {
        TreeVisitor<?, ExecutionContext> hamcrest = dependency("org.hamcrest..*").getScanner(acc);
        TreeVisitor<?, ExecutionContext> expectedException = dependency("org.junit.rules.ExpectedException").getScanner(acc);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                hamcrest.visit(tree, ctx);
                // Scanning precedes Java edits, so account for matchers the migration will generate
                // without relying on a second recipe cycle. Reuse the dependency scanner to retain
                // module isolation and infer the scope from the source set containing the rule.
                if (tree instanceof JavaSourceFile && generatesHamcrestAssertions((JavaSourceFile) tree)) {
                    expectedException.visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean generatesHamcrestAssertions(JavaSourceFile source) {
        for (JavaType.Method method : source.getTypesInUse().getUsedMethods()) {
            if (EXPECT_MESSAGE.matches(method) || EXPECT_CAUSE.matches(method) || EXPECT_MATCHER.matches(method)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AddDependency.Accumulator acc) {
        return dependency("org.hamcrest..*").getVisitor(acc);
    }

    private static AddDependency dependency(String onlyIfUsing) {
        return new AddDependency("org.hamcrest", "hamcrest", "2.x", null,
                onlyIfUsing, null, null, null, null, null, null, null, null, true);
    }
}
