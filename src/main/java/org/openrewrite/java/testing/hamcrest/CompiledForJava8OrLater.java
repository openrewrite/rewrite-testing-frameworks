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
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * AssertJ 3 requires Java 8, so the migration leaves Hamcrest in sources compiled for an earlier Java alone. A source
 * file without a {@link JavaVersion} marker is taken to be compiled for Java 8 or later.
 */
public class CompiledForJava8OrLater extends Recipe {

    @Getter
    final String displayName = "Find Java sources compiled for Java 8 or later";

    @Getter
    final String description = "Find the Java source files compiled for Java 8 or later, or whose Java version is " +
            "not known, which AssertJ 3 can be used in.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit)) {
                    return tree;
                }
                SourceFile cu = (SourceFile) tree;
                return isCompiledForJava8OrLater(cu) ? SearchResult.found(cu) : cu;
            }
        };
    }

    static boolean isCompiledForJava8OrLater(SourceFile sourceFile) {
        return sourceFile.getMarkers().findFirst(JavaVersion.class)
                .map(v -> v.getMajorVersion() >= 8)
                .orElse(true);
    }
}
