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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.Modifier.Type;
import org.openrewrite.java.tree.JavaSourceFile;

import java.time.Duration;
import java.util.stream.Collectors;

public class LifecycleNonPrivate extends Recipe {

    @Override
    public String getDisplayName() {
        return "Make lifecycle methods non private";
    }

    @Override
    public String getDescription() {
        return "Make JUnit 5's `@AfterAll`, `@AfterEach`, `@BeforeAll` and `@BeforeEach` non private.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitJavaSourceFile(JavaSourceFile cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.AfterAll"));
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.AfterEach"));
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.BeforeAll"));
                doAfterVisit(new UsesType<>("org.junit.jupiter.api.BeforeEach"));
                return cu;
            }
        };
    }

    @Override
    protected LifecycleNonPrivateVisitor getVisitor() {
        return new LifecycleNonPrivateVisitor();
    }

    private static class LifecycleNonPrivateVisitor extends JavaVisitor<ExecutionContext> {
        @Override
        public J visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
            J.MethodDeclaration md = (MethodDeclaration) super.visitMethodDeclaration(method, p);
            if (md.getModifiers().stream().anyMatch(mod -> mod.getType() == Type.Private)) {
                return md
                        .withModifiers(md.getModifiers().stream()
                        .filter(mod -> mod.getType() != Type.Private)
                        .collect(Collectors.toList()));
            }
            return md;
        }
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

}
