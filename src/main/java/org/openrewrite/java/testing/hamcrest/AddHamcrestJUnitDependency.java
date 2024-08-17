/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.hamcrest;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.util.concurrent.atomic.AtomicBoolean;

public class AddHamcrestJUnitDependency extends ScanningRecipe<AddHamcrestJUnitDependency.Accumulator> {

    @Override
    public String getDisplayName() {
        return "Add Hamcrest JUnit dependency";
    }

    @Override
    public String getDescription() {
        return "Add Hamcrest JUnit dependency only if JUnit 4's `assertThat` or `assumeThat` is used.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(
                new AtomicBoolean(false),
                getAddDependency().getInitialValue(ctx));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<Tree, ExecutionContext> usesMethodVisitor = getUsesMethodVisitor(acc.shouldAdd);
        TreeVisitor<?, ExecutionContext> addDependencyScanner = getAddDependency().getScanner(acc.delegate);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!acc.shouldAdd.get()) {
                    usesMethodVisitor.visit(tree, ctx);
                    addDependencyScanner.visit(tree, ctx);
                }
                //noinspection DataFlowIssue
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.shouldAdd.get()) {
            return getAddDependency().getVisitor(acc.delegate);
        }
        return TreeVisitor.noop();
    }

    private static TreeVisitor<Tree, ExecutionContext> getUsesMethodVisitor(AtomicBoolean shouldAdd) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            private final MethodMatcher methodMatcher = new MethodMatcher("org.junit.Ass* *That(..)");

            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof JavaSourceFile) {
                    for (JavaType.Method type : ((JavaSourceFile) tree).getTypesInUse().getUsedMethods()) {
                        if (methodMatcher.matches(type)) {
                            shouldAdd.set(true);
                        }
                    }
                }
                return tree;
            }
        };
    }

    private static AddDependency getAddDependency() {
        // We can unconditionally add the dependency here
        return new AddDependency(
                "org.hamcrest",
                "hamcrest-junit",
                "2.x",
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
        );
    }

    @Value
    public static class Accumulator {
        AtomicBoolean shouldAdd;
        AddDependency.Accumulator delegate;
    }
}
