/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.TypeUtils;

public class RemoveTestPrefix extends Recipe {

    private static final List<String> RESERVED_KEYWORDS = Arrays.asList("abstract", "continue", "for", "new", "switch",
            "assert", "default", "if", "package", "synchronized", "boolean", "do", "goto", "private", "this", "break",
            "double", "implements", "protected", "throw", "byte", "else", "import", "public", "throws", "case", "enum",
            "instanceof", "return", "transient", "catch", "extends", "int", "short", "try", "char", "final",
            "interface", "static", "void", "class", "finally", "long", "strictfp", "volatile", "const", "float",
            "native", "super", "while",
            // Non keywords that still result in an error
            "null", "clone", "finalize", "hashCode", "notify", "notifyAll", "toString", "wait");

    @Override
    public String getDisplayName() {
        return "Remove `test` prefix from JUnit 5 tests";
    }

    @Override
    public String getDescription() {
        return "Remove `test` from methods with `@Test`, `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory`. They no longer have to prefix test to be usable by JUnit 5.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitJavaSourceFile(JavaSourceFile cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.Test"));
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.TestTemplate"));
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.RepeatedTest"));
                doAfterVisit(new UsesType<>("org.junit.jupiter.params.ParameterizedTest"));
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.TestFactory"));
                return cu;
            }
        };
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveTestPrefixVisitor();
    }

    private static class RemoveTestPrefixVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

            // Quickly reject invalid methods
            String simpleName = method.getSimpleName();
            int nameLength = simpleName.length();
            if (nameLength < 5
                    || !simpleName.startsWith("test")
                    || TypeUtils.isOverride(method.getMethodType())
                    || !hasJUnit5MethodAnnotation(method)) {
                return m;
            }

            // Reject invalid start character
            boolean snakecase = simpleName.charAt(4) == '_'
                    && 5 < nameLength
                    && Character.isAlphabetic(simpleName.charAt(5));
            if (!snakecase && !Character.isAlphabetic(simpleName.charAt(4))) {
                return m;
            }

            // Rename method
            String newMethodName = snakecase
                    ? Character.toLowerCase(simpleName.charAt(5)) + simpleName.substring(6)
                    : Character.toLowerCase(simpleName.charAt(4)) + simpleName.substring(5);
            if (RESERVED_KEYWORDS.contains(newMethodName)) {
                return m;
            }

            JavaType.Method type = m.getMethodType();

            if (type == null || methodExists(type, newMethodName)) {
                return m;
            }

            type = type.withName(newMethodName);
            return m.withName(m.getName()
                    .withSimpleName(newMethodName))
                    .withMethodType(type);
        }

        private boolean methodExists(JavaType.Method method, String newName) {
            return TypeUtils.findDeclaredMethod(method.getDeclaringType(), newName, method.getParameterTypes()).orElse(null) != null;
        }

        private static boolean hasJUnit5MethodAnnotation(MethodDeclaration method) {
            for (J.Annotation a : method.getLeadingAnnotations()) {
                if (TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.Test")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestTemplate")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.RepeatedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.ParameterizedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestFactory")) {
                    return true;
                }
            }
            return false;
        }
    }

}
