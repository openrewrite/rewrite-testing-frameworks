/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;

public class CleanupKotlinJUnit5AssertionImports extends Recipe {

    // Functions declared in `org.junit.jupiter.api.AssertionsKt`
    private static final Set<String> KOTLIN_ASSERTIONS = new HashSet<>(Arrays.asList(
            "assertAll", "assertDoesNotThrow", "assertInstanceOf", "assertNotNull", "assertNull",
            "assertThrows", "assertTimeout", "assertTimeoutPreemptively", "fail"));

    @Getter
    final String displayName = "Remove JUnit 5 static Assertions imports in Kotlin when wildcard import is present";

    @Getter
    final String description = "In Kotlin, when both `import org.junit.jupiter.api.*` and static imports from " +
            "`org.junit.jupiter.api.Assertions` are present, there is overload resolution ambiguity between the Java " +
            "static methods and the Kotlin extension functions. This recipe removes the explicit imports of those " +
            "`Assertions` members that have a Kotlin counterpart when the wildcard import is present, allowing the " +
            "Kotlin functions to be used instead. Other `Assertions` imports are left alone.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.api.Assertions", false), new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, ExecutionContext ctx) {
                // Check if there's a wildcard import for org.junit.jupiter.api.*
                boolean hasWildcardImport = cu.getImports().stream()
                        .anyMatch(imp -> "org.junit.jupiter.api".equals(imp.getPackageName()) &&
                                "*".equals(imp.getQualid().getSimpleName()) &&
                                !imp.isStatic());

                if (!hasWildcardImport) {
                    return cu;
                }

                // Only drop explicit imports of members that have a Kotlin counterpart in `AssertionsKt`;
                // `Assertions.*` and other members such as `assertEquals` must stay to keep resolving
                List<J.Import> filteredImports = cu.getImports().stream()
                        .filter(imp -> !"org.junit.jupiter.api.Assertions".equals(imp.getTypeName()) ||
                                !KOTLIN_ASSERTIONS.contains(imp.getQualid().getSimpleName()))
                        .collect(toList());

                if (filteredImports.size() == cu.getImports().size()) {
                    return cu;
                }

                return cu.withImports(filteredImports);
            }
        });
    }
}
