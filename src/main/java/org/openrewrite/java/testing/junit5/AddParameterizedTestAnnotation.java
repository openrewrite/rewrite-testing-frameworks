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
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Comparator;
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
    ).map(annotation -> new AnnotationMatcher("@org.junit.jupiter.params.provider."+annotation)).collect(Collectors.toList());

    @Override
    public String getDisplayName() {
        return "Add missing @ParameterizedTest annotation when @ValueSource is used or " +
               "replace @Test with @ParameterizedTest";
    }

    @Override
    public String getDescription() {
        return "Add missing @ParameterizedTest annotation when @ValueSource is used or " +
               "replace @Test with @ParameterizedTest.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            public boolean checkForSourceAnnotation(J.Annotation ann) {
                // checks if annotations matches ValueSource and siblings
                return SOURCE_ANNOTATIONS.stream().anyMatch(matcher -> matcher.matches(ann));
            }

            public List<J.Annotation> reorderAnnotations(List<J.Annotation> annotations) {
                // if @ParameterizedTest is already at start then return early
                if (PARAM_TEST_MATCHER.matches(annotations.get(0))) { return annotations; }
                List<J.Annotation> ordered = new ArrayList<>(annotations);
                ordered.removeIf(PARAM_TEST_MATCHER::matches);

                return ordered;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(md, ctx);

                // return early if @ValueSource and siblings are not detected
                if (m.getLeadingAnnotations().stream().anyMatch(PARAM_TEST_MATCHER::matches) ||
                    m.getLeadingAnnotations().stream().noneMatch(this::checkForSourceAnnotation)) {
                    return m;
                }

                JavaCoordinates coordinates = m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName));

                for (J.Annotation ann : m.getLeadingAnnotations()) {
                    if (TEST_ANNOTATION_MATCHER.matches(ann)) {
                        coordinates = ann.getCoordinates().replace();
                        break;
                    }
                }

                m = JavaTemplate.builder("@ParameterizedTest")
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "junit-jupiter-api-5.9", "junit-jupiter-params-5.9"))
                        .imports("org.junit.jupiter.params.ParameterizedTest")
                        .build()
                        .apply(getCursor(), coordinates);
                maybeAddImport("org.junit.jupiter.params.ParameterizedTest");
                maybeRemoveImport("org.junit.jupiter.api.Test");
                List<J.Annotation> ordered = reorderAnnotations(m.getLeadingAnnotations());

                return maybeAutoFormat(m, m.withLeadingAnnotations(ordered), ctx);
            }
        };
    }
}