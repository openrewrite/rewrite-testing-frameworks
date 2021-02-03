/*
 * Copyright 2020 the original author or authors.
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

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

/**
 * Transforms the Junit4 @Category, which can list multiple categories, into one @Tag annotation per category listed
 */
@AutoConfigure
public class CategoryToTag extends JavaIsoRefactorVisitor {
    private static final String categoryAnnotation = "org.junit.experimental.categories.Category";
    private static final JavaType.Class tagType = JavaType.Class.build("org.junit.jupiter.api.Tag");

    @Override
    public J.ClassDecl visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl cd = super.visitClassDecl(classDecl);
        List<J.Annotation> categoryAnnotations = cd.findAnnotationsOnClass("@" + categoryAnnotation);
        if(!categoryAnnotations.isEmpty()) {
            andThen(new Scoped(cd));
        }
        return cd;
    }

    @Override
    public J.MethodDecl visitMethod(J.MethodDecl method) {
        J.MethodDecl m = super.visitMethod(method);
        List<J.Annotation> categoryAnnotations = m.findAnnotations("@" + categoryAnnotation);
        if(!categoryAnnotations.isEmpty()) {
            andThen(new Scoped(m));
        }
        return m;
    }

    public static class Scoped extends JavaIsoRefactorVisitor {
        final J scope;
        public Scoped(J scope) {
            this.scope = scope;
        }

        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl c = super.visitClassDecl(classDecl);
            if (scope.isScope(classDecl)) {
                AtomicInteger index = new AtomicInteger(0);
                c = c.withAnnotations(c.getAnnotations().stream()
                        .flatMap(this::categoryAnnotationToTagAnnotations)
                        .map(annotation -> {
                            andThen(new AutoFormat(annotation));
                            if (index.getAndIncrement() != 0) {
                                return annotation.withPrefix("\n");
                            } else {
                                return annotation;
                            }
                        })
                        .collect(Collectors.toList()));

                maybeRemoveImport(categoryAnnotation);
                maybeAddImport(tagType);
            }

            return c;
        }

        @Override
        public J.MethodDecl visitMethod(J.MethodDecl m) {
            if (scope.isScope(m)) {
                AtomicInteger index = new AtomicInteger(0);
                m = m.withAnnotations(m.getAnnotations().stream()
                        .flatMap(this::categoryAnnotationToTagAnnotations)
                        .map(annotation -> {
                            andThen(new AutoFormat(annotation));
                            if (index.getAndIncrement() != 0) {
                                return annotation.withPrefix("\n");
                            } else {
                                return annotation;
                            }
                        })
                        .collect(Collectors.toList()));

                maybeRemoveImport(categoryAnnotation);
                maybeAddImport(tagType);
            }
            return m;
        }

        private Stream<J.Annotation> categoryAnnotationToTagAnnotations(J.Annotation maybeCategory) {
                if(TypeUtils.isOfClassType(maybeCategory.getAnnotationType().getType(), categoryAnnotation)) {
                    Expression annotationArgument = maybeCategory.getArgs().getArgs().iterator().next();

                    Stream<J.FieldAccess> categories;
                    if (annotationArgument instanceof J.NewArray) {
                        categories = ((J.NewArray) annotationArgument).getInitializer().getElements().stream()
                                .map(J.FieldAccess.class::cast);
                    } else {
                        categories = Stream.of((J.FieldAccess) annotationArgument);
                    }

                    return categories.map(category -> {
                            String targetName = ((J.Ident) category.getTarget()).getSimpleName();
                            J.Literal tagValue = new J.Literal(
                                    randomId(),
                                    targetName,
                                    "\"" + targetName + "\"",
                                    JavaType.Primitive.String,
                                    Formatting.EMPTY);
                            J.Annotation tagAnnotation = J.Annotation.buildAnnotation(
                                    maybeCategory.getFormatting(),
                                    tagType,
                                    Collections.singletonList(tagValue)
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
