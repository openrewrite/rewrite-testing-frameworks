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
import org.openrewrite.Preconditions;
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>("org.junit.BeforeClass", false),
                        new UsesType<>("org.junit.Before", false),
                        new UsesType<>("org.junit.After", false),
                        new UsesType<>("org.junit.AfterClass", false)
                ),
                new UpdateBeforeAfterAnnotationsVisitor());
    }

    public static class UpdateBeforeAfterAnnotationsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J preVisit(J tree, ExecutionContext ctx) {
            stopAfterPreVisit();
            doAfterVisit(new ChangeType("org.junit.Before", "org.junit.jupiter.api.BeforeEach", true).getVisitor());
            doAfterVisit(new ChangeType("org.junit.After", "org.junit.jupiter.api.AfterEach", true).getVisitor());
            doAfterVisit(new ChangeType("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll", true).getVisitor());
            doAfterVisit(new ChangeType("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll", true).getVisitor());
            return tree;
        }
    }
}
