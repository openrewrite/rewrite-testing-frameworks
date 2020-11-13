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

import com.fasterxml.jackson.databind.introspect.Annotated;
import org.openrewrite.AutoConfigure;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Transforms the Junit4 @Category, which can list multiple categories, into one @Tag annotation per category listed
 */
@AutoConfigure
public class CategoryToTag extends JavaRefactorVisitor {
    private static final String categoryAnnotation = "org.junit.experimental.categories.Category";
    private static final JavaType.Class tagType = JavaType.Class.build("org.junit.jupiter.api.Tag");

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl cd = refactor(classDecl, super::visitClassDecl);
        if(!cd.findAnnotations("@" + categoryAnnotation).isEmpty()) {
            andThen(new Scoped(cd));
        }
        return cd;
    }

    @Override
    public J visitMethod(J.MethodDecl method) {
        J.MethodDecl cd = refactor(method, super::visitMethod);
        if(!cd.findAnnotations("@" + categoryAnnotation).isEmpty()) {
            andThen(new Scoped(cd));
        }
        return cd;
    }

    public static class Scoped extends JavaRefactorVisitor {
        final J statement;
        public Scoped(J statement) {
            this.statement = statement;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

            if (statement.isScope(classDecl) && !classDecl.findAnnotations("@" + categoryAnnotation).isEmpty()) {

                classDecl.findAnnotations("@" + categoryAnnotation).stream().forEach( annot -> {
                            Expression categoryArgs = annot.getArgs().getArgs().iterator().next();

                            Stream<Expression> categories = categoryArgs instanceof J.NewArray ?
                                        ((J.NewArray) categoryArgs).getInitializer().getElements().stream() :
                                        Stream.of(categoryArgs);

                            categories.forEach(arg -> {
                                andThen(new AddAnnotation.Scoped(
                                        classDecl,
                                        "org.junit.jupiter.api.Tag",
                                        arg.withPrefix("")));
                            });
                        }
                );

                c = c.withAnnotations(
                        c.getAnnotations().stream()
                                .filter(annot ->
                                    !TypeUtils.isOfClassType(annot.getAnnotationType().getType(), categoryAnnotation)
                                )
                                .collect(Collectors.toList())
                    );
            }
            maybeRemoveImport(categoryAnnotation);
            maybeAddImport(tagType);

            return c;
        }

        @Override
        public J visitMethod(J.MethodDecl methodDecl) {
            J.MethodDecl m = refactor(methodDecl, super::visitMethod);

            if (statement.isScope(methodDecl) && !methodDecl.findAnnotations("@" + categoryAnnotation).isEmpty()) {
                m = m.withAnnotations(m.getAnnotations().stream()
                        .flatMap(annot -> {
                            if(TypeUtils.isOfClassType(annot.getAnnotationType().getType(), categoryAnnotation)) {
                                if(annot.getArgs() == null) return Stream.empty();

                                AtomicInteger index = new AtomicInteger(0);

                                Expression value = annot.getArgs().getArgs().iterator().next();
                                //create stream of the values depending on what the value type is
                                Stream<Expression> categories = value instanceof J.NewArray ?
                                        ((J.NewArray) value).getInitializer().getElements().stream() :
                                        Stream.of(value);

                                return categories
                                        .map(arg -> {
                                            J.Annotation annotation =
                                                    J.Annotation.buildAnnotation(
                                                            annot.getFormatting(),
                                                            tagType,
                                                            Collections.singletonList(arg.withPrefix(""))
                                                    );

                                            if (index.getAndIncrement() != 0) {
                                                annotation = annotation.withPrefix("\n");
                                                andThen(new AutoFormat(annotation));
                                            }

                                            return annotation;
                                        });
                            }
                            return Stream.of(annot);
                        })
                        .collect(Collectors.toList()));
            }
            maybeRemoveImport(categoryAnnotation);
            maybeAddImport(tagType);

            return m;
        }
    }
}
