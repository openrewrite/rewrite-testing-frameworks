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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * In Kotlin files, when both {@code import org.junit.jupiter.api.*} and static imports from
 * {@code org.junit.jupiter.api.Assertions} are present, the Kotlin compiler cannot resolve
 * which assertion method to use due to ambiguity between the Java static methods and the
 * Kotlin extension functions.
 * <p>
 * This recipe removes the static Assertions imports in Kotlin files when the wildcard import
 * {@code org.junit.jupiter.api.*} is present, allowing the Kotlin extension functions to be
 * used instead. The Kotlin extensions have better nullability handling for Kotlin code.
 */
public class CleanupKotlinJUnit5AssertionImports extends Recipe {

    @Getter
    final String displayName = "Remove JUnit 5 static Assertions imports in Kotlin when wildcard import is present";

    @Getter
    final String description = "In Kotlin, when both `import org.junit.jupiter.api.*` and static imports from " +
            "`org.junit.jupiter.api.Assertions` are present, there is overload resolution ambiguity between the Java " +
            "static methods and the Kotlin extension functions. This recipe removes the static Assertions imports " +
            "when the wildcard import is present, allowing the Kotlin extension functions to be used instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, ExecutionContext ctx) {
                // Check if there's a wildcard import for org.junit.jupiter.api.*
                boolean hasWildcardImport = cu.getImports().stream()
                        .anyMatch(imp -> "org.junit.jupiter.api".equals(imp.getPackageName()) &&
                                imp.getQualid().getSimpleName().equals("*") &&
                                !imp.isStatic());

                if (!hasWildcardImport) {
                    return cu;
                }

                // Check if there are any Assertions imports that should be removed
                boolean hasAssertionsImport = cu.getImports().stream()
                        .anyMatch(imp -> {
                            String typeName = imp.getTypeName();
                            return typeName != null && typeName.equals("org.junit.jupiter.api.Assertions");
                        });

                if (!hasAssertionsImport) {
                    return cu;
                }

                // Filter out the Assertions imports
                List<J.Import> filteredImports = cu.getImports().stream()
                        .filter(imp -> {
                            String typeName = imp.getTypeName();
                            // Keep imports that are NOT from org.junit.jupiter.api.Assertions
                            return typeName == null || !"org.junit.jupiter.api.Assertions".equals(typeName);
                        .collect(toList());
                        .collect(Collectors.toList());

                if (filteredImports.size() == cu.getImports().size()) {
                    return cu;
                }

                return cu.withImports(filteredImports);
            }
        };
    }
}
