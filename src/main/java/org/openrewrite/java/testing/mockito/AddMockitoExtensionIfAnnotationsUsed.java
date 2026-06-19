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
        return check(and(new IsLikelyTest().getVisitor(),
                        new FindAnnotations("org.mockito.*", false).getVisitor(),
                        // Match any `@ExtendWith`/`@RunWith` (no `.class` argument): if a test already registers an extension or runner
                        // (e.g. `SpringExtension`), adding `MockitoExtension` is at best redundant and at worst breaks the test
                        // lifecycle. See https://github.com/openrewrite/rewrite-testing-frameworks/issues/875
                        or(
                                // JUnit 5: has JUnit 5 types and no `@ExtendWith` yet
                                and(new FindTypes("org.junit.jupiter..*", false).getVisitor(),
                                        not(new FindAnnotations("org.junit.jupiter.api.extension.ExtendWith", false).getVisitor())),
                                // JUnit 4: has @org.junit.Test and no `@RunWith` yet
                                and(new FindAnnotations("org.junit.Test", false).getVisitor(),
                                        not(new FindAnnotations("org.junit.runner.RunWith", false).getVisitor()))
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
                if (shouldSkip(classDecl)) {
                    return classDecl;
                }
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
                if (shouldSkip(classDecl)) {
                    return classDecl;
                }
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
                if (shouldSkip(classDecl)) {
                    return classDecl;
                }
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

    /**
     * Only add the extension/runner to a concrete test class that declares Mockito annotations. Whether an extension
     * or runner is already present is handled by the precondition; see
     * <a href="https://github.com/openrewrite/rewrite-testing-frameworks/issues/875">#875</a>.
     */
    private static boolean shouldSkip(J.ClassDeclaration classDecl) {
        // Interfaces, enums, annotations and abstract bases are not concrete Mockito test classes
        if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class ||
                classDecl.hasModifier(J.Modifier.Type.Abstract)) {
            return true;
        }
        // Only add to classes that declare Mockito annotations. The search spans the class subtree on purpose: a
        // `@Mock` in a `@Nested` inner class should annotate the enclosing class, since JUnit 5 propagates the
        // extension to nested classes (and the visitor only annotates the outermost class).
        return FindAnnotations.find(classDecl, "@org.mockito.*").isEmpty();
    }
}
