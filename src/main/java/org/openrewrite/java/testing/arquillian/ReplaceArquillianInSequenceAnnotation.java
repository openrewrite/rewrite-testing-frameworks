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
package org.openrewrite.java.testing.arquillian;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class ReplaceArquillianInSequenceAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Arquillian JUnit 4 `@InSequence` to JUnit Jupiter `@Order`";
    }

    @Override
    public String getDescription() {
        return "Transforms the Arquillian JUnit 4 `@InSequence` to the JUnit Jupiter `@Order`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.jboss.arquillian.junit.InSequence", false),
                new InSequenceToOrderVisitor());
    }

    public static class InSequenceToOrderVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType.Class testMethodOrderType = JavaType.ShallowClass.build("org.junit.jupiter.api.TestMethodOrder");
        private static final JavaType.Class methodOrderType = JavaType.ShallowClass.build("org.junit.jupiter.api.MethodOrderer");
        private static final JavaType.Class orderType = JavaType.ShallowClass.build("org.junit.jupiter.api.Order");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);



            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            Set<J.Annotation> inSequenceAnnotations = FindAnnotations.find(m, "@org.jboss.arquillian.junit.InSequence");
            if (!inSequenceAnnotations.isEmpty()) {
                doAfterVisit(new ChangeType(
                        "org.jboss.arquillian.junit.InSequence",
                        "org.junit.jupiter.api.Order",
                        true).getVisitor());

                J.ClassDeclaration classDeclaration = getCursor().dropParentUntil(org.openrewrite.java.tree.J.ClassDeclaration.class::isInstance)
                        .getValue();
                doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        if (cd.getName().equals(classDeclaration.getName())) {
                            if (FindAnnotations.find(cd, "@" + testMethodOrderType.getFullyQualifiedName()).isEmpty()) {
                                List<J.Annotation> annotations = new ArrayList<>(cd.getLeadingAnnotations());
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
                                maybeAddImport(methodOrderType.getFullyQualifiedName(), null, false);
                            }
                        }
                        cd = maybeAutoFormat(classDecl, cd, cd.getName(), ctx, getCursor().getParentTreeCursor());
                        return cd;
                    }
                });
            }
            return maybeAutoFormat(method, m, m.getName(), ctx, getCursor().getParentTreeCursor());
        }
    }
}
