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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodAccessLevelVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class TestMethodsNotPublic extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `public` visibility of JUnit5 tests";
    }

    @Override
    public String getDescription() {
        return "Remove `public` modifier from methods with `@Test`, `@ParametrizedTest`, `@RepeatedTest`, `TestFactory`, `@BeforeEach` or `@AfterEach`. They no longer have to be public visibility to be usable by JUnit 5.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TestMethodsNotPublicVisitor();
    }

    private static class TestMethodsNotPublicVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

            if (m.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Public)) {
                for (J.Annotation a : m.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.Test") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.RepeatedTest") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.ParameterizedTest") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestFactory") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.AfterEach") ||
                        TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.BeforeEach")) {
                        // remove public modifier
                        doAfterVisit(new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(method), null));
                    }
                }
            }

            return m;
        }
    }
}
