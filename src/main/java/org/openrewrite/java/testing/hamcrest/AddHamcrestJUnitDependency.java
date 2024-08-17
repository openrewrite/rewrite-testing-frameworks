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

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.util.concurrent.atomic.AtomicBoolean;

public class AddHamcrestJUnitDependency extends ScanningRecipe<AtomicBoolean> {

    @Override
    public String getDisplayName() {
        return "Add Hamcrest JUnit dependency";
    }

    @Override
    public String getDescription() {
        return "Add Hamcrest JUnit dependency only if JUnit 4's `assertThat` or `assumeThat` is used.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean acc) {
        // No need to scan for AddDependency, as we'll unconditionally add the dependency if we find a match below
        MethodMatcher methodMatcher = new MethodMatcher("org.junit.Ass* *That(..)");
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (tree instanceof JavaSourceFile && !acc.get()) {
                    for (JavaType.Method type : ((JavaSourceFile) tree).getTypesInUse().getUsedMethods()) {
                        if (methodMatcher.matches(type)) {
                            acc.set(true);
                        }
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean acc) {
        if (acc.get()) {
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
            ).getVisitor();
        }
        return TreeVisitor.noop();
    }
}
