/*
 * Copyright 2020 the original author or authors.
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
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class UpdateBeforeAfterAnnotations extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate JUnit 4 lifecycle annotations to JUnit Jupiter";
    }

    @Override
    public String getDescription() {
        return "Replace JUnit 4's `@Before`, `@BeforeClass`, `@After`, and `@AfterClass` annotations with their JUnit Jupiter equivalents.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                doAfterVisit(new UsesType<>("org.junit.BeforeClass"));
                doAfterVisit(new UsesType<>("org.junit.Before"));
                doAfterVisit(new UsesType<>("org.junit.After"));
                doAfterVisit(new UsesType<>("org.junit.AfterClass"));
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new UpdateBeforeAfterAnnotationsVisitor();
    }

    public static class UpdateBeforeAfterAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            //This visitor handles changing the method visibility for any method annotated with one of the four before/after
            //annotations. It registers visitors that will sweep behind it making the type changes.
            doAfterVisit(new ChangeType("org.junit.Before", "org.junit.jupiter.api.BeforeEach"));
            doAfterVisit(new ChangeType("org.junit.After", "org.junit.jupiter.api.AfterEach"));
            doAfterVisit(new ChangeType("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll"));
            doAfterVisit(new ChangeType("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll"));

            return super.visitCompilationUnit(cu, ctx);
        }
    }
}
