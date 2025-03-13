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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Removes unused "org.mockito" imports.
 */
public class CleanupMockitoImports extends Recipe {

    @Override
    public String getDisplayName() {
        return "Cleanup Mockito imports";
    }

    @Override
    public String getDescription() {
        return "Removes unused `org.mockito` import symbols, unless its possible they are associated with method invocations having null or unknown type information.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.mockito.*", false),
                new CleanupMockitoImportsVisitor());
    }

    private static class CleanupMockitoImportsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final List<String> MOCKITO_METHOD_NAMES = Arrays.asList(
                "after",
                "atLeast",
                "atLeastOnce",
                "atMost",
                "calls",
                "clearInvocations",
                "doAnswer",
                "doCallRealMethod",
                "doNothing",
                "doReturn",
                "doThrow",
                "given",
                "ignoreStubs",
                "inOrder",
                "mock",
                "mockingDetails",
                "never",
                "only",
                "reset",
                "spy",
                "stub",
                "stubVoid",
                "then",
                "timeout",
                "times",
                "verify",
                "verifyNoInteractions",
                "verifyNoMoreInteractions",
                "verifyZeroInteractions",
                "when",
                "whenever", // org.mockito.kotlin
                "will",
                "willAnswer",
                "willCallRealMethod",
                "willDoNothing",
                "willReturn",
                "willThrow");

        @Override
        public @Nullable J preVisit(J tree, ExecutionContext ctx) {
            stopAfterPreVisit();
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile sf = (JavaSourceFile) tree;

                // Determine Mockito methods that are present with valid type information
                List<String> mockitoMethodsUsed = new MockitoMethodTypeVisitor().reduce(sf, new ArrayList<>());

                // Prevent removing mockito imports when an associated mockito method type is not well formed
                List<String> unknownTypeMethodInvocationNames = new WellFormedMockitoMethodTypeVisitor().reduce(sf, new ArrayList<>());
                List<String> qualifiedMethodInvocationNames = new QualifiedMockitoMethodTypeVisitor().reduce(sf, new ArrayList<>());

                for (J.Import _import : sf.getImports()) {
                    if (_import.getPackageName().startsWith("org.mockito")) {
                        boolean isMockitoKotlinImport = _import.getPackageName().startsWith("org.mockito.kotlin");
                        if (_import.isStatic() || isMockitoKotlinImport) {
                            String staticName = _import.getQualid().getSimpleName();
                            if (mockitoMethodsUsed.contains(staticName)) {
                                continue;
                            }
                            if ("*".equals(staticName)) {
                                maybeRemoveImport(_import.getPackageName() + "." + _import.getClassName());
                            } else if (!unknownTypeMethodInvocationNames.contains(staticName)) {
                                String fullyQualifiedName = _import.getPackageName();
                                if (!isMockitoKotlinImport) {
                                    fullyQualifiedName += "." + _import.getClassName();
                                }
                                fullyQualifiedName += "." + staticName;
                                maybeRemoveImport(fullyQualifiedName);
                            }
                        } else if (qualifiedMethodInvocationNames.isEmpty()) {
                            maybeRemoveImport(_import.getPackageName() + "." + _import.getClassName());
                        }
                    }
                }
            }
            return tree;
        }

        private static class MockitoMethodTypeVisitor extends JavaIsoVisitor<List<String>> {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<String> methods) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, methods);
                if (MOCKITO_METHOD_NAMES.contains(mi.getSimpleName()) && TypeUtils.isWellFormedType(mi.getType())) {
                    methods.add(mi.getSimpleName());
                }
                return mi;
            }
        }

        private static class WellFormedMockitoMethodTypeVisitor extends JavaIsoVisitor<List<String>> {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<String> missingMethods) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, missingMethods);
                if (MOCKITO_METHOD_NAMES.contains(mi.getSimpleName()) && !TypeUtils.isWellFormedType(mi.getType())) {
                    missingMethods.add(mi.getSimpleName());
                }
                return mi;
            }
        }

        private static class QualifiedMockitoMethodTypeVisitor extends JavaIsoVisitor<List<String>> {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, List<String> qualifiedMethods) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, qualifiedMethods);
                if (MOCKITO_METHOD_NAMES.contains(mi.getSimpleName()) &&
                    mi.getSelect() != null &&
                    TypeUtils.isAssignableTo("org.mockito.Mockito", mi.getSelect().getType())) {
                    qualifiedMethods.add(mi.getSimpleName());
                }
                return mi;
            }
        }
    }
}
