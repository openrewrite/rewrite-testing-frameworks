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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

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
        return Preconditions.check(
                new UsesType<>("org.jboss.arquillian.junit.InSequence", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    private final String IN_SEQUENCE = "org.jboss.arquillian.junit.InSequence";
                    private final String TEST_METHOD_ORDER = "org.junit.jupiter.api.TestMethodOrder";
                    private final String METHOD_ORDERER = "org.junit.jupiter.api.MethodOrderer";
                    private final AnnotationMatcher IN_SEQUENCE_MATCHER = new AnnotationMatcher("@" + IN_SEQUENCE);
                    private final AnnotationMatcher TEST_METHOD_ORDER_MATCHER = new AnnotationMatcher("@" + TEST_METHOD_ORDER);

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        doAfterVisit(new ChangeType(IN_SEQUENCE, "org.junit.jupiter.api.Order", true).getVisitor());
                        return super.visitClassDeclaration(classDecl, ctx);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                        if (service(AnnotationService.class).matches(updateCursor(m), IN_SEQUENCE_MATCHER)) {
                            J.ClassDeclaration classWithInSequenceMethods = getCursor().firstEnclosing(J.ClassDeclaration.class);
                            if (classWithInSequenceMethods != null) {
                                doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                                    @Override
                                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                                        if (cd.getName().equals(classWithInSequenceMethods.getName()) &&
                                                !service(AnnotationService.class).matches(updateCursor(cd), TEST_METHOD_ORDER_MATCHER)) {
                                            maybeAddImport(METHOD_ORDERER);
                                            maybeAddImport(TEST_METHOD_ORDER);
                                            return JavaTemplate.builder("@TestMethodOrder(MethodOrderer.class)")
                                                    .imports(METHOD_ORDERER, TEST_METHOD_ORDER)
                                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                                    .build()
                                                    .apply(getCursor(), classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                                        }
                                        return cd;
                                    }
                                });
                            }
                        }
                        return m;
                    }
                }
        );
    }
}
