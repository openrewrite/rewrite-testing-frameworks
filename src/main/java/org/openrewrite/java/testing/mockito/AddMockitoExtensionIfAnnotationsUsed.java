/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.FindTypes;
import org.openrewrite.java.search.IsLikelyTest;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.KotlinTemplate;
import org.openrewrite.kotlin.tree.K;

import static java.util.Comparator.comparing;
import static org.openrewrite.Preconditions.*;

public class AddMockitoExtensionIfAnnotationsUsed extends Recipe {
    @Getter
    final String displayName = "Adds Mockito extensions to Mockito tests";

    @Getter
    final String description = "Adds `@ExtendWith(MockitoExtension.class)` to JUnit 5 tests or " +
            "`@RunWith(MockitoJUnitRunner.class)` to JUnit 4 tests using Mockito annotations like `@Mock` or `@Captor`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        TreeVisitor<?, ExecutionContext> hasExtendedWithAnnotation = new FindAnnotations("org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)", false).getVisitor();
        TreeVisitor<?, ExecutionContext> hasRunWithAnnotation = new FindAnnotations("org.junit.runner.RunWith", false).getVisitor();
        @SuppressWarnings("unchecked")
        TreeVisitor<?, ExecutionContext>[] hasAnyMockitoAnnotation = new TreeVisitor[]{
                // see https://www.baeldung.com/mockito-annotations for examples
                new FindAnnotations("org.mockito.Captor", false).getVisitor(),
                new FindAnnotations("org.mockito.Mock", false).getVisitor(),
                new FindAnnotations("org.mockito.Spy", false).getVisitor(),
                new FindAnnotations("org.mockito.InjectMocks", false).getVisitor(),
        };

        return check(and(new IsLikelyTest().getVisitor(),
                        or(hasAnyMockitoAnnotation),
                        or(
                                // JUnit 5: has JUnit 5 types and no @ExtendWith(MockitoExtension.class)
                                and(new FindTypes("org.junit.jupiter..*", false).getVisitor(),
                                        not(hasExtendedWithAnnotation)),
                                // JUnit 4: has @org.junit.Test, no @RunWith
                                and(new FindAnnotations("org.junit.Test", false).getVisitor(),
                                        not(hasRunWithAnnotation))
                        )),
                new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public @Nullable Tree preVisit(Tree tree, ExecutionContext ctx) {
                        stopAfterPreVisit();
                        if (tree instanceof J.CompilationUnit) {
                            if (!FindAnnotations.find((J) tree, "@org.junit.Test").isEmpty()) {
                                return getJunit4JavaVisitor().visit(tree, ctx);
                            }
                            return getJavaVisitor().visit(tree, ctx);
                        }
                        if (tree instanceof K.CompilationUnit) {
                            return getKotlinVisitor().visit(tree, ctx);
                        }
                        return tree;
                    }
                });
    }

    private JavaIsoVisitor<ExecutionContext> getJavaVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                maybeAddImport("org.mockito.junit.jupiter.MockitoExtension");
                maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");

                return JavaTemplate.builder("@ExtendWith(MockitoExtension.class)")
                        .imports("org.mockito.junit.jupiter.MockitoExtension")
                        .imports("org.junit.jupiter.api.extension.ExtendWith")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api", "mockito-junit-jupiter"))
                        .build()
                        .apply(getCursor(), classDecl.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> getJunit4JavaVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                maybeAddImport("org.mockito.junit.MockitoJUnitRunner");
                maybeAddImport("org.junit.runner.RunWith");

                return JavaTemplate.builder("@RunWith(MockitoJUnitRunner.class)")
                        .imports("org.mockito.junit.MockitoJUnitRunner")
                        .imports("org.junit.runner.RunWith")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-4", "mockito-core"))
                        .build()
                        .apply(getCursor(), classDecl.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }
        };
    }

    private KotlinIsoVisitor<ExecutionContext> getKotlinVisitor() {
        return new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                maybeAddImport("org.mockito.junit.jupiter.MockitoExtension");
                maybeAddImport("org.junit.jupiter.api.extension.ExtendWith");

                return KotlinTemplate.builder("@ExtendWith(MockitoExtension::class)")
                        .imports("org.mockito.junit.jupiter.MockitoExtension")
                        .imports("org.junit.jupiter.api.extension.ExtendWith")
                        .parser(KotlinParser.builder().classpathFromResources(ctx, "junit-jupiter-api", "mockito-junit-jupiter"))
                        .build()
                        .apply(getCursor(), classDecl.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }
        };
    }
}
