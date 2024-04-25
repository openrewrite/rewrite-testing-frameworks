/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AddParameterizedTestAnnotation extends Recipe {
    private static final AnnotationMatcher TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.jupiter.api.Test");
    private static final AnnotationMatcher PARAM_TEST_MATCHER = new AnnotationMatcher("@org.junit.jupiter.params.ParameterizedTest");
    private static final List<AnnotationMatcher> SOURCE_ANNOTATIONS = Stream.of(
            "ValueSource",
            "CsvSource",
            "MethodSource",
            "NullSource",
            "EmptySource",
            "NullAndEmptySource",
            "EnumSource",
            "CsvFileSource",
            "ArgumentsSource"
    ).map(annotation -> new AnnotationMatcher("@org.junit.jupiter.params.provider." + annotation)).collect(Collectors.toList());

    @Override
    public String getDisplayName() {
        return "Add missing `@ParameterizedTest` annotation when `@ValueSource` is used or " +
               "replace `@Test` with `@ParameterizedTest`";
    }

    @Override
    public String getDescription() {
        return "Add missing `@ParameterizedTest` annotation when `@ValueSource` is used or " +
               "replace `@Test` with `@ParameterizedTest`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.jupiter.params.provider.*", false), new AnnotatedMethodVisitor());
    }

    private static class AnnotatedMethodVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);

            // Return early if already annotated with @ParameterizedTest or not annotated with any @...Source annotation
            if (m.getLeadingAnnotations().stream().anyMatch(PARAM_TEST_MATCHER::matches) ||
                m.getLeadingAnnotations().stream().noneMatch(ann -> SOURCE_ANNOTATIONS.stream().anyMatch(matcher -> matcher.matches(ann)))) {
                return m;
            }

            // Add parameterized test annotation at the start
            JavaCoordinates coordinates = m.getCoordinates().addAnnotation((o1, o2) -> -1);
            m = JavaTemplate.builder("@ParameterizedTest")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-params-5.9"))
                    .imports("org.junit.jupiter.params.ParameterizedTest")
                    .build()
                    .apply(getCursor(), coordinates);
            maybeAddImport("org.junit.jupiter.params.ParameterizedTest");

            // Remove @Test annotation if present
            maybeRemoveImport("org.junit.jupiter.api.Test");
            return new RemoveAnnotationVisitor(TEST_ANNOTATION_MATCHER).visitMethodDeclaration(m, ctx);
        }
    }
}
