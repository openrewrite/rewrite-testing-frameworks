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
package org.openrewrite.java.testing.gatling;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

/**
 * Finds Gatling simulations by their imports rather than by type attribution, since Scala sources
 * are not reliably attributed against the Gatling Scala DSL.
 */
final class GatlingSources {

    private GatlingSources() {
    }

    static boolean isScala(JavaSourceFile sourceFile) {
        String path = sourceFile.getSourcePath().toString();
        return path.endsWith(".scala") || path.endsWith(".sc");
    }

    static boolean importsGatling(JavaSourceFile sourceFile) {
        return sourceFile.getImports().stream()
                .anyMatch(anImport -> anImport.getPackageName().startsWith("io.gatling."));
    }

    static boolean isKotlin(JavaSourceFile sourceFile) {
        return sourceFile.getSourcePath().toString().endsWith(".kt");
    }

    /**
     * @param includeJavaAndKotlin whether Java and Kotlin sources qualify as well, or only Scala ones
     */
    static TreeVisitor<?, ExecutionContext> importingGatling(boolean includeJavaAndKotlin) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    JavaSourceFile sourceFile = (JavaSourceFile) tree;
                    boolean language = isScala(sourceFile) ||
                            (includeJavaAndKotlin && (sourceFile instanceof J.CompilationUnit || isKotlin(sourceFile)));
                    if (language && importsGatling(sourceFile)) {
                        return SearchResult.found(tree);
                    }
                }
                return tree;
            }
        };
    }
}
