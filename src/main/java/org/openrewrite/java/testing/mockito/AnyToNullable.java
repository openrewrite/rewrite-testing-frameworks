/*
 * Copyright 2024 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.dependencies.FindDependency;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.concurrent.atomic.AtomicBoolean;

public class AnyToNullable extends ScanningRecipe<AtomicBoolean> {
    @Getter
    final String displayName = "Replace Mockito 1.x `anyString()`/`any()` with `nullable(Class)`";

    @Getter
    final String description = "Since Mockito 2.10 `anyString()` and `any()` no longer matches null values. Use `nullable(Class)` instead.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean acc) {
        FindDependency findDependency = new FindDependency("org.mockito", "mockito-all", null, null, null);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!acc.get() && tree != findDependency.getVisitor().visit(tree, ctx)) {
                    acc.set(true);
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
                    doAfterVisit(new ChangeMethodTargetToStatic("org.mockito.Mockito nullable(java.lang.Class)", "org.mockito.ArgumentMatchers", null, null, false).getVisitor());
                    doAfterVisit(new AnyStringToNullable().getVisitor());
                }
                return super.preVisit(tree, ctx);
            }
        });
    }
}
