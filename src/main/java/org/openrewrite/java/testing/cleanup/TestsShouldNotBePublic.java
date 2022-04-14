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
package org.openrewrite.java.testing.cleanup;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeMethodAccessLevelVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.Modifier;
import org.openrewrite.java.tree.J.Modifier.Type;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TestsShouldNotBePublic extends Recipe {

    @Option(displayName = "Remove protected modifiers",
            description = "Also remove protected modifiers from test methods",
            example = "true",
            required = false)
    @Nullable
    private Boolean removeProtectedModifiers;

    @Override
    public String getDisplayName() {
        return "Remove `public` visibility of JUnit5 tests";
    }

    @Override
    public String getDescription() {
        return "Remove `public` and optionally `protected` modifiers from methods with `@Test`, `@ParametrizedTest`, `@RepeatedTest`, `TestFactory`, `@BeforeEach` or `@AfterEach`. They no longer have to be public visibility to be usable by JUnit 5.";
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-5786");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TestsNotPublicVisitor(Boolean.TRUE.equals(removeProtectedModifiers));
    }

    private static class TestsNotPublicVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Boolean orProtected;

        private TestsNotPublicVisitor(Boolean orProtected) {
            this.orProtected = orProtected;
        }

        @Override
        public ClassDeclaration visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext executionContext) {
            ClassDeclaration c = super.visitClassDeclaration(classDecl, executionContext);

            if (c.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Public)
                    && c.getModifiers().stream().noneMatch(mod -> mod.getType() == Type.Abstract)) {

                boolean hasTestMethods = c.getBody().getStatements().stream()
                        .filter(statement -> statement instanceof J.MethodDeclaration)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(this::hasJUnit5MethodAnnotation);

                boolean hasPublicNonTestMethods = c.getBody().getStatements().stream()
                        .filter(statement -> statement instanceof J.MethodDeclaration)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(m -> m.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Public))
                        .anyMatch(method -> !hasJUnit5MethodAnnotation(method));

                boolean hasPublicVariableDeclarations = c.getBody().getStatements().stream()
                        .filter(statement -> statement instanceof J.VariableDeclarations)
                        .map(J.VariableDeclarations.class::cast)
                        .anyMatch(m -> m.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Public));

                if (hasTestMethods && !hasPublicNonTestMethods && !hasPublicVariableDeclarations) {
                    // Remove public modifier and move associated comment
                    final List<Comment> modifierComments = new ArrayList<>();
                    List<Modifier> modifiers = ListUtils.map(c.getModifiers(), mod -> {
                        if (mod.getType() == J.Modifier.Type.Public) {
                            modifierComments.addAll(mod.getComments());
                            return null;
                        }

                        // copy access level modifier comment to next modifier if it exists
                        if (!modifierComments.isEmpty()) {
                            J.Modifier nextModifier = mod.withComments(ListUtils.concatAll(new ArrayList<>(modifierComments), mod.getComments()));
                            modifierComments.clear();
                            return nextModifier;
                        }
                        return mod;
                    });
                    // if no following modifier exists, add comments to method itself
                    if (!modifierComments.isEmpty()) {
                        c = c.withComments(ListUtils.concatAll(c.getComments(), modifierComments));
                    }
                    c = maybeAutoFormat(c, c.withModifiers(modifiers), executionContext, getCursor().dropParentUntil(J.class::isInstance));
                }
            }
            return c;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

            if (m.getModifiers().stream().anyMatch(mod -> (mod.getType() == J.Modifier.Type.Public || (orProtected && mod.getType() == Type.Protected)))
                    && Boolean.FALSE.equals(TypeUtils.isOverride(method.getMethodType()))
                    && hasJUnit5MethodAnnotation(m)) {
                // remove public modifier
                doAfterVisit(new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(method), null));
            }

            return m;
        }

        private boolean hasJUnit5MethodAnnotation(MethodDeclaration method) {
            for (J.Annotation a : method.getLeadingAnnotations()) {
                if (TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.Test")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.RepeatedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.ParameterizedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestFactory")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.AfterEach")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.BeforeEach")) {
                    return true;
                }
            }
            return false;
        }
    }
}
