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

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

import org.openrewrite.ExecutionContext;
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

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddMissingTestBeforeAfterAnnotations extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add missing `@BeforeEach`, `@AfterEach`, `@Test` to overriding methods";
    }

    @Override
    public String getDescription() {
        return "Adds `@BeforeEach`, `@AfterEach`, `@Test` to methods overriding superclass methods if the annoations are present on the superclass method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AddMissingTestBeforeAfterAnnotationsVisitor();
    }

    private class AddMissingTestBeforeAfterAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (!method.hasModifier(J.Modifier.Type.Static) && !method.isConstructor()) {
                Optional<Method> superMethod = TypeUtils.findOverriddenMethod(method.getMethodType());
                if (superMethod.isPresent()) {
                    method = addMissingAnnotation(method, ctx, superMethod.get(), LifecyleAnnotation.BEFORE_EACH);
                    method = addMissingAnnotation(method, ctx, superMethod.get(), LifecyleAnnotation.AFTER_EACH);
                    method = addMissingAnnotation(method, ctx, superMethod.get(), LifecyleAnnotation.TEST);
                }
            }
            return super.visitMethodDeclaration(method, ctx);
        }

        private J.MethodDeclaration addMissingAnnotation(J.MethodDeclaration method, ExecutionContext ctx, Method superMethod, LifecyleAnnotation la) {
            if (superMethod.getAnnotations().stream().anyMatch(la.oldAnnotationPredicate) &&
                    method.getAllAnnotations().stream().noneMatch(a -> la.annotationMatcher.matches(a))) {
                method = JavaTemplate.builder(la.simpleAnnotation)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5.9"))
                        .imports(la.annotation).build().apply(getCursor(), method.getCoordinates()
                                .addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                maybeAddImport(la.annotation);
            }
            return method;
        }
    }

    enum LifecyleAnnotation {
        BEFORE_EACH("org.junit.jupiter.api.BeforeEach", "org.junit.Before"),
        AFTER_EACH("org.junit.jupiter.api.AfterEach", "org.junit.After"),
        TEST("org.junit.jupiter.api.Test", "org.junit.Test");

        String annotation;
        String oldAnnotation;
        String simpleAnnotation;
        Predicate<FullyQualified> oldAnnotationPredicate;
        AnnotationMatcher annotationMatcher;

        LifecyleAnnotation(String annotation, String oldAnnotation) {
            this.annotation = annotation;
            this.oldAnnotation = oldAnnotation;

            simpleAnnotation = "@" + annotation.substring(annotation.lastIndexOf(".") + 1);
            oldAnnotationPredicate = n -> n.getFullyQualifiedName().equals(oldAnnotation);
            annotationMatcher = new AnnotationMatcher("@" + annotation);
        }
    }

}
