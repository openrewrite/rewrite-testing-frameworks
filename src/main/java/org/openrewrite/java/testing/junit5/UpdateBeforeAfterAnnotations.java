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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateBeforeAfterAnnotations extends Recipe {
    @Getter
    final String displayName = "Migrate JUnit 4 lifecycle annotations to JUnit Jupiter";

    @Getter
    final String description = "Replace JUnit 4's `@Before`, `@BeforeClass`, `@After`, and `@AfterClass` annotations with their JUnit Jupiter equivalents.";

    private static final Map<String, String> JUNIT4_TO_JUPITER = new LinkedHashMap<String, String>() {{
        put("org.junit.Before", "org.junit.jupiter.api.BeforeEach");
        put("org.junit.After", "org.junit.jupiter.api.AfterEach");
        put("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll");
        put("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll");
    }};

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
            // Remove the JUnit 4 annotation where the Jupiter equivalent is already present, to avoid creating a
            // duplicate annotation when the type is subsequently changed below.
            doAfterVisit(new RemoveRedundantJUnit4LifecycleAnnotations());
            JUNIT4_TO_JUPITER.forEach((from, to) -> doAfterVisit(new ChangeType(from, to, true).getVisitor()));
            return tree;
        }
    }

    private static class RemoveRedundantJUnit4LifecycleAnnotations extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            for (Map.Entry<String, String> pair : JUNIT4_TO_JUPITER.entrySet()) {
                AnnotationMatcher junit4 = new AnnotationMatcher("@" + pair.getKey());
                AnnotationMatcher jupiter = new AnnotationMatcher("@" + pair.getValue());
                boolean hasJunit4 = md.getLeadingAnnotations().stream().anyMatch(junit4::matches);
                boolean hasJupiter = md.getLeadingAnnotations().stream().anyMatch(jupiter::matches);
                if (hasJunit4 && hasJupiter) {
                    md = (J.MethodDeclaration) new RemoveAnnotationVisitor(junit4)
                            .visitNonNull(md, ctx, getCursor().getParentOrThrow());
                }
            }
            return md;
        }
    }
}
