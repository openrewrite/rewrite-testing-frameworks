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

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

public class ReplaceArquillianInSequenceAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Arquillian JUnit 4 `@InSequence` to JUnit Jupiter `@Order`";
    }

    @Override
    public String getDescription() {
        return "Transforms the Arquillian JUnit 4 `@InSequence` to the JUnit Jupiter `@Order`";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.jboss.arquillian.junit.InSequence", false), new InSequenceToOrderVisitor());
    }

    public static class InSequenceToOrderVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.Class testMethodOrderType = JavaType.ShallowClass.build("org.junit.jupiter.api.TestMethodOrder");
        private static final JavaType.Class methodOrderType = JavaType.ShallowClass.build("org.junit.jupiter.api.MethodOrderer");
        private static final JavaType.Class orderType = JavaType.ShallowClass.build("org.junit.jupiter.api.Order");

        @Override
        public J.MethodDeclaration visitMethodDeclaration(final J.MethodDeclaration method, final ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            final Set<J.Annotation> inSequenceAnnotations = FindAnnotations.find(m, "@org.jboss.arquillian.junit.InSequence");
            if (!inSequenceAnnotations.isEmpty()) {
                m = m.withLeadingAnnotations(m.getLeadingAnnotations().stream()
                        .flatMap(this::inSequenceAnnotationToOrderAnnotation)
                        .collect(Collectors.toList()));

                maybeRemoveImport("org.jboss.arquillian.junit.InSequence");
                maybeAddImport(orderType);
                final J.ClassDeclaration classDeclaration = getCursor().dropParentUntil(org.openrewrite.java.tree.J.ClassDeclaration.class::isInstance)
                        .getValue();
                super.doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (cd.getName().equals(classDeclaration.getName())) {
                            if (FindAnnotations.find(cd, "@" + testMethodOrderType.getFullyQualifiedName()).isEmpty()) {
                                final List<J.Annotation> annotations = new ArrayList<>(cd.getLeadingAnnotations());
                                annotations.add(new J.Annotation(randomId(), Space.EMPTY, Markers.EMPTY,
                                        new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), testMethodOrderType.getClassName(), testMethodOrderType, null),
                                        JContainer.build(Space.EMPTY,
                                                Collections.singletonList(
                                                        new JRightPadded<>(
                                                                new J.Literal(
                                                                        randomId(),
                                                                        Space.EMPTY,
                                                                        Markers.EMPTY,
                                                                        methodOrderType,
                                                                        methodOrderType.getClassName() + ".class",
                                                                        null,
                                                                        JavaType.Primitive.None),
                                                                Space.EMPTY,
                                                                Markers.EMPTY
                                                        )
                                                ),
                                                Markers.EMPTY
                                        )
                                ));
                                cd = cd.withLeadingAnnotations(annotations);
                                maybeAddImport(testMethodOrderType);
                                maybeAddImport(methodOrderType);
                            }
                        }
                        cd = maybeAutoFormat(classDecl, cd, cd.getName(), ctx, getCursor().getParentTreeCursor());
                        return cd;
                    }

                });
            }
            m = maybeAutoFormat(method, m, m.getName(), ctx, getCursor().getParentTreeCursor());
            return m;
        }

        private Stream<J.Annotation> inSequenceAnnotationToOrderAnnotation(final J.Annotation maybeInSequence) {
            if (maybeInSequence.getArguments() != null && TypeUtils.isOfClassType(maybeInSequence.getAnnotationType()
                    .getType(), "org.jboss.arquillian.junit.InSequence")) {
                final Expression annotationArgument = maybeInSequence.getArguments().iterator().next();
                Stream<J.Literal> value = Stream.empty();
                if (annotationArgument instanceof J.Literal) {
                    value = Stream.of((J.Literal) annotationArgument);
                }
                return value.map(orderValue -> {
                    J.Annotation orderAnnotation = new J.Annotation(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), orderType.getClassName(), orderType, null),
                            JContainer.build(Space.EMPTY,
                                    Collections.singletonList(
                                            new JRightPadded<>(
                                                    new J.Literal(
                                                            randomId(),
                                                            Space.EMPTY,
                                                            Markers.EMPTY,
                                                            "" + orderValue,
                                                            orderValue.getValueSource(),
                                                            null,
                                                            JavaType.Primitive.Int
                                                    ),
                                                    Space.EMPTY,
                                                    Markers.EMPTY
                                            )
                                    ),
                                    Markers.EMPTY
                            )
                    );
                    return orderAnnotation;
                });
            } else {
                return Stream.of(maybeInSequence);
            }
        }
    }
}
