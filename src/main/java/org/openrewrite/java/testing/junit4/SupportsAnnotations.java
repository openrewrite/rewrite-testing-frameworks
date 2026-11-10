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
package org.openrewrite.java.testing.junit4;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * JUnit 4 is driven by annotations, which Java 5 introduced, so code that compiles for Java 1.4 or older stays JUnit 3.
 */
final class SupportsAnnotations extends JavaIsoVisitor<ExecutionContext> {

    static TreeVisitor<?, ExecutionContext> precondition() {
        return new SupportsAnnotations();
    }

    @Override
    public J visit(Tree tree, ExecutionContext ctx) {
        if (tree instanceof J.CompilationUnit) {
            J.CompilationUnit cu = (J.CompilationUnit) tree;
            JavaVersion version = cu.getMarkers().findFirst(JavaVersion.class).orElse(null);
            if (version == null || majorVersion(version.getSourceCompatibility()) >= 5) {
                return SearchResult.found(cu);
            }
        }
        return (J) tree;
    }

    private static int majorVersion(String compatibility) {
        String version = compatibility.startsWith("1.") ? compatibility.substring(2) : compatibility;
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        return end == 0 ? Integer.MAX_VALUE : Integer.parseInt(version.substring(0, end));
    }
}
