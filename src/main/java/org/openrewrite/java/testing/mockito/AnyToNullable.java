/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.xml.tree.Xml;

import java.util.concurrent.atomic.AtomicBoolean;

public class AnyToNullable extends ScanningRecipe<AtomicBoolean> {
    @Override
    public String getDisplayName() {
        return "Replace Mockito 1.x `anyString()`/`any()` with `nullable(Class)`";
    }

    @Override
    public String getDescription() {
        return "Since Mockito 2.10 `anyString()` and `any()` no longer matches null values. Use `nullable(Class)` instead.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean acc) {
        org.openrewrite.maven.search.FindDependency mavenFindDependency =
                new org.openrewrite.maven.search.FindDependency("org.mockito", "mockito-all", null, null);
        org.openrewrite.gradle.search.FindDependency gradleFindDependency =
                new org.openrewrite.gradle.search.FindDependency("org.mockito", "mockito-all", null);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!acc.get()) {
                    if (tree instanceof Xml.Document && tree != mavenFindDependency.getVisitor().visit(tree, ctx)) {
                        acc.set(true);
                    } else if (tree instanceof J && tree != gradleFindDependency.getVisitor().visit(tree, ctx)) {
                        acc.set(true);
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean acc) {
        return Preconditions.check(acc.get(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree preVisit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    stopAfterPreVisit();
                    doAfterVisit(new ChangeMethodName(
                            "org.mockito.Mockito any(java.lang.Class)", "nullable", null, null).getVisitor());
                    doAfterVisit(new ChangeMethodTargetToStatic(
                            "org.mockito.Mockito nullable(java.lang.Class)", "org.mockito.ArgumentMatchers", null, null).getVisitor());
                    doAfterVisit(new AnyStringToNullable().getVisitor());
                }
                return super.preVisit(tree, ctx);
            }
        });
    }
}
