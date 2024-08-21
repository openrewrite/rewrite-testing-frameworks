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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("org.junit.jupiter.api.Test", false),
                        new UsesType<>("org.junit.jupiter.api.TestTemplate", false),
                        new UsesType<>("org.junit.jupiter.api.RepeatedTest", false),
                        new UsesType<>("org.junit.jupiter.params.ParameterizedTest", false),
                        new UsesType<>("org.junit.jupiter.api.TestFactory", false)
                ),
                new RemoveTestPrefixVisitor());
    }

    private static class RemoveTestPrefixVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final AnnotationMatcher ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.provider.MethodSource");

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                          ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            // Quickly reject invalid methods
            String simpleName = method.getSimpleName();
            int nameLength = simpleName.length();
            if (nameLength < 5
                    || !simpleName.startsWith("test")
                    || !(simpleName.charAt(4) == '_' || Character.isUpperCase(simpleName.charAt(4)))
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

            // Avoid reserved keywords
            String newMethodName = snakecase
                    ? NameCaseConvention.format(NameCaseConvention.LOWER_UNDERSCORE, simpleName.substring(5))
                    : NameCaseConvention.format(NameCaseConvention.LOWER_CAMEL, simpleName.substring(4));
            if (RESERVED_KEYWORDS.contains(newMethodName)) {
                return m;
            }

            // Prevent conflicts with existing methods
            JavaType.Method type = m.getMethodType();
            if (type == null || methodExists(type, newMethodName)) {
                return m;
            }

            // Skip implied methodSource
            for (J.Annotation annotation : method.getLeadingAnnotations()) {
                if (ANNOTATION_MATCHER.matches(annotation) &&
                    (annotation.getArguments() == null || annotation.getArguments().isEmpty())) {
                    return m;
                }
            }

            // Skip when calling a similarly named method
            AtomicBoolean skip = new AtomicBoolean(false);
            new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean atomicBoolean) {
                    if (method.getName().getSimpleName().equals(newMethodName) && method.getSelect() == null) {
                        skip.set(true);
                    }
                    return super.visitMethodInvocation(method, atomicBoolean);
                }
            }.visitMethodDeclaration(m, skip);
            if (skip.get()) {
                return m;
            }

            // Rename method and return
            type = type.withName(newMethodName);
            return m.withName(m.getName().withSimpleName(newMethodName).withType(type))
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
