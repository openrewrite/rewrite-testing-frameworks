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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class CategoryToTag extends Recipe {

    @Getter
    final String displayName = "JUnit 4 `@Category` to JUnit Jupiter `@Tag`";

    @Getter
    final String description = "Transforms the JUnit 4 `@Category`, which can list multiple categories, into one `@Tag` annotation per category listed.";

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
                        .collect(toList()));
                maybeRemoveImport("org.junit.experimental.categories.Category");
                maybeAddImport(tagType);
            }
            return maybeAutoFormat(classDecl, cd, cd.getName(), ctx, getCursor().getParentTreeCursor());
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            Set<J.Annotation> categoryAnnotations = FindAnnotations.find(m, "@" + "org.junit.experimental.categories.Category");
            if (!categoryAnnotations.isEmpty()) {
                m = m.withLeadingAnnotations(m.getLeadingAnnotations().stream()
                        .flatMap(this::categoryAnnotationToTagAnnotations)
                        .collect(toList()));

                maybeRemoveImport("org.junit.experimental.categories.Category");
                maybeAddImport(tagType);
            }
            return maybeAutoFormat(method, m, m.getName(), ctx, getCursor().getParentTreeCursor());
        }

        private Stream<J.Annotation> categoryAnnotationToTagAnnotations(J.Annotation maybeCategory) {
            if (maybeCategory.getArguments() != null && TypeUtils.isOfClassType(maybeCategory.getAnnotationType().getType(), "org.junit.experimental.categories.Category")) {
                List<Expression> categories = maybeCategory.getArguments().stream().flatMap(argument -> {
                    Expression value = argument instanceof J.Assignment ? ((J.Assignment) argument).getAssignment() : argument;
                    if (value instanceof J.NewArray) {
                        return ((J.NewArray) value).getInitializer() == null ? Stream.empty() :
                                ((J.NewArray) value).getInitializer().stream();
                    }
                    if (KotlinJUnit5Support.isKotlin(getCursor()) && value instanceof K.ListLiteral) {
                        return ((K.ListLiteral) value).getElements().stream();
                    }
                    if (value instanceof J.MethodInvocation && "arrayOf".equals(((J.MethodInvocation) value).getSimpleName())) {
                        return ((J.MethodInvocation) value).getArguments().stream();
                    }
                    return Stream.of(value);
                }).collect(toList());
                if (categories.stream().anyMatch(category -> !(category instanceof J.FieldAccess) &&
                        !(category instanceof J.MemberReference))) {
                    return Stream.of(maybeCategory);
                }
                return categories.stream().map(category -> {
                    Expression target = category instanceof J.MemberReference ?
                            ((J.MemberReference) category).getContaining() : ((J.FieldAccess) category).getTarget();
                    String targetName = convertToTagName(target);
                    J.Annotation tagAnnotation = new J.Annotation(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), tagType.getClassName(), tagType, null),
                            JContainer.build(Space.EMPTY,
                                    singletonList(
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
                    maybeRemoveImport(TypeUtils.asFullyQualified(target.getType()));
                    return tagAnnotation;
                });
            }
            return Stream.of(maybeCategory);
        }

        private static String convertToTagName(Expression category) {
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(category.getType());
            if (type != null) {
                String name = type.getClassName();
                return name.substring(Math.max(name.lastIndexOf('$'), name.lastIndexOf('.')) + 1);
            }
            if (category instanceof J.Identifier) {
                return ((J.Identifier) category).getSimpleName();
            }
            if (category instanceof J.FieldAccess) {
                return ((J.FieldAccess) category).getSimpleName();
            }
            return category.toString();
        }
    }
}
