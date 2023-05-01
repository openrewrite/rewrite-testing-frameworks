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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.openrewrite.Tree.randomId;

public class CategoryToTag extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit 4 `@Category` to JUnit Jupiter `@Tag`";
    }

    @Override
    public String getDescription() {
        return "Transforms the JUnit 4 `@Category`, which can list multiple categories, into one `@Tag` annotation per category listed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.experimental.categories.Category", false), new CategoryToTagVisitor());
    }

    public static class CategoryToTagVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.Class tagType = JavaType.ShallowClass.build("org.junit.jupiter.api.Tag");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<J.Annotation> categoryAnnotations = FindAnnotations.find(cd, "@" + "org.junit.experimental.categories.Category");
            if (!categoryAnnotations.isEmpty()) {
                cd = cd.withLeadingAnnotations(cd.getLeadingAnnotations().stream()
                        .flatMap(this::categoryAnnotationToTagAnnotations)
                        .collect(Collectors.toList()));
                maybeRemoveImport("org.junit.experimental.categories.Category");
                maybeAddImport(tagType);
            }
            cd = maybeAutoFormat(classDecl, cd, cd.getName(), ctx, getCursor().getParentTreeCursor());
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            Set<J.Annotation> categoryAnnotations = FindAnnotations.find(m, "@" + "org.junit.experimental.categories.Category");
            if (!categoryAnnotations.isEmpty()) {
                m = m.withLeadingAnnotations(m.getLeadingAnnotations().stream()
                        .flatMap(this::categoryAnnotationToTagAnnotations)
                        .collect(Collectors.toList()));

                maybeRemoveImport("org.junit.experimental.categories.Category");
                maybeAddImport(tagType);
            }
            m = maybeAutoFormat(method, m, m.getName(), ctx, getCursor().getParentTreeCursor());
            return m;
        }

        private Stream<J.Annotation> categoryAnnotationToTagAnnotations(J.Annotation maybeCategory) {
            if (maybeCategory.getArguments() != null && TypeUtils.isOfClassType(maybeCategory.getAnnotationType().getType(), "org.junit.experimental.categories.Category")) {
                Expression annotationArgument = maybeCategory.getArguments().iterator().next();
                if (annotationArgument instanceof J.Assignment) {
                    annotationArgument = ((J.Assignment) annotationArgument).getAssignment();
                }

                Stream<J.FieldAccess> categories = Stream.empty();
                if (annotationArgument instanceof J.NewArray) {
                    J.NewArray argArray = (J.NewArray) annotationArgument;
                    if (argArray.getInitializer() != null) {
                        categories = argArray.getInitializer().stream()
                                .map(J.FieldAccess.class::cast);
                    }
                }
                if (annotationArgument instanceof J.FieldAccess) {
                    categories = Stream.of((J.FieldAccess) annotationArgument);
                }
                return categories.map(category -> {
                    String targetName = ((J.Identifier) category.getTarget()).getSimpleName();
                    J.Annotation tagAnnotation = new J.Annotation(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, tagType.getClassName(), tagType, null),
                            JContainer.build(Space.EMPTY,
                                    Collections.singletonList(
                                            new JRightPadded<>(
                                                    new J.Literal(
                                                            randomId(),
                                                            Space.EMPTY,
                                                            Markers.EMPTY,
                                                            targetName,
                                                            "\"" + targetName + "\"",
                                                            null,
                                                            JavaType.Primitive.String
                                                    ),
                                                    Space.EMPTY,
                                                    Markers.EMPTY
                                            )
                                    ),
                                    Markers.EMPTY
                            )
                    );
                    maybeRemoveImport(TypeUtils.asFullyQualified(category.getTarget().getType()));
                    return tagAnnotation;
                });
            } else {
                return Stream.of(maybeCategory);
            }
        }
    }
}
