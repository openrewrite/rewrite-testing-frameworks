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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.dependencies.RemoveDependency;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.maven.RemoveManagedDependency;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Removes {@code junit:junit} and its managed version, unless some source file still uses the JUnit 3 suites and runners that
 * JUnit Jupiter has no equivalent for, which only {@code junit:junit} provides.
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveJUnit4DependencyUnlessJUnit3Used extends ScanningRecipe<RemoveJUnit4DependencyUnlessJUnit3Used.Accumulator> {

    private static final String[] JUNIT3_PACKAGES = {"junit.framework.", "junit.textui.", "junit.awtui.", "junit.swingui.", "junit.runner.", "junit.extensions."};

    @Getter
    String displayName = "Remove `junit:junit` unless JUnit 3 types are still used";

    @Getter
    String description = "Removes the `junit:junit` dependency, unless a source file still uses `junit.framework` or the other " +
                         "JUnit 3 packages, such as test suites and runners that were not migrated.";

    @Value
    public static class Accumulator {
        AtomicBoolean junit3Used;
        RemoveDependency remove;
        RemoveDependency.Accumulator removeAcc;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        RemoveDependency remove = new RemoveDependency("junit", "junit", null, null, null);
        return new Accumulator(new AtomicBoolean(), remove, remove.getInitialValue(ctx));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> removeScanner = acc.remove.getScanner(acc.removeAcc);
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                removeScanner.visit(tree, ctx);
                return super.visit(tree, ctx);
            }

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (!acc.junit3Used.get()) {
                    for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
                        if (type instanceof JavaType.FullyQualified && isJUnit3((JavaType.FullyQualified) type)) {
                            acc.junit3Used.set(true);
                            break;
                        }
                    }
                }
                return cu;
            }
        };
    }

    private static boolean isJUnit3(JavaType.FullyQualified type) {
        String fqn = type.getFullyQualifiedName();
        for (String p : JUNIT3_PACKAGES) {
            if (fqn.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        TreeVisitor<?, ExecutionContext> remove = acc.remove.getVisitor(acc.removeAcc);
        // the version of a dependency that stays is managed where it was
        TreeVisitor<?, ExecutionContext> removeManaged = new RemoveManagedDependency("junit", "junit", null).getVisitor();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (acc.junit3Used.get()) {
                    return tree;
                }
                return removeManaged.visit(remove.visit(tree, ctx), ctx);
            }
        };
    }
}
