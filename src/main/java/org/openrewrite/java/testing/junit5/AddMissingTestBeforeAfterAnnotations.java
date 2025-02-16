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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.JavaType.Method;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddMissingTestBeforeAfterAnnotations extends Recipe {
    @Override
    public String getDisplayName() {
        return "Add missing `@BeforeEach`, `@AfterEach`, `@Test` to overriding methods";
    }

    @Override
    public String getDescription() {
        return "Adds `@BeforeEach`, `@AfterEach`, `@Test` to methods overriding superclass methods if the annotations are present on the superclass method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (classDecl.getExtends() != null) {
                    // Only classes that extend other classes can have override methods with missing annotations
                    return SearchResult.found(classDecl);
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        }, new AddMissingTestBeforeAfterAnnotationsVisitor());
    }

    private static class AddMissingTestBeforeAfterAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (!method.hasModifier(J.Modifier.Type.Static) && !method.isConstructor()) {
                Method currMethod = method.getMethodType();
                Optional<Method> superMethod = TypeUtils.findOverriddenMethod(currMethod);
                while (superMethod.isPresent()) {
                    method = maybeAddMissingAnnotation(method, superMethod.get(), LifecyleAnnotation.BEFORE_EACH, ctx);
                    method = maybeAddMissingAnnotation(method, superMethod.get(), LifecyleAnnotation.AFTER_EACH, ctx);
                    method = maybeAddMissingAnnotation(method, superMethod.get(), LifecyleAnnotation.TEST, ctx);
                    currMethod = superMethod.get();
                    superMethod = TypeUtils.findOverriddenMethod(currMethod);
                }
            }
            return super.visitMethodDeclaration(method, ctx);
        }

        private J.MethodDeclaration maybeAddMissingAnnotation(J.MethodDeclaration method, Method superMethod, LifecyleAnnotation la, ExecutionContext ctx) {
            if (la.needsAnnotation(method, superMethod)) {
                maybeAddImport(la.newAnnotation);
                return JavaTemplate.builder(la.newAnnotationSimple)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                        .imports(la.newAnnotation)
                        .build()
                        .apply(getCursor(), method.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }
            return method;
        }
    }

    enum LifecyleAnnotation {
        BEFORE_EACH("org.junit.Before", "org.junit.jupiter.api.BeforeEach"),
        AFTER_EACH("org.junit.After", "org.junit.jupiter.api.AfterEach"),
        TEST("org.junit.Test", "org.junit.jupiter.api.Test");

        String newAnnotation;
        String newAnnotationSimple;
        private AnnotationMatcher newAnnotationMatcher;
        private Predicate<FullyQualified> newAnnotationPredicate;
        private Predicate<FullyQualified> oldAnnotationPredicate;

        LifecyleAnnotation(String oldAnnotation, String newAnnotation) {
            this.newAnnotation = newAnnotation;
            this.newAnnotationSimple = "@" + newAnnotation.substring(newAnnotation.lastIndexOf(".") + 1);
            this.newAnnotationMatcher = new AnnotationMatcher("@" + newAnnotation);
            this.newAnnotationPredicate = n -> TypeUtils.isOfClassType(n, newAnnotation);
            this.oldAnnotationPredicate = n -> TypeUtils.isOfClassType(n, oldAnnotation);
        }

        boolean needsAnnotation(J.MethodDeclaration method, Method superMethod) {
            boolean superMethodHasAnnotation = superMethod.getAnnotations().stream().anyMatch(oldAnnotationPredicate.or(newAnnotationPredicate));
            return superMethodHasAnnotation && !method.getAllAnnotations().stream().anyMatch(newAnnotationMatcher::matches);
        }
    }
}
