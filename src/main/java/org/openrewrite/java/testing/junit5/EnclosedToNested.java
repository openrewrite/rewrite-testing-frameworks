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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.time.Duration;
import java.util.Comparator;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class EnclosedToNested extends Recipe {
    private static final String ENCLOSED = "org.junit.experimental.runners.Enclosed";
    private static final String RUN_WITH = "org.junit.runner.RunWith";
    private static final String NESTED = "org.junit.jupiter.api.Nested";
    private static final String TEST_JUNIT4 = "org.junit.Test";
    private static final String TEST_JUNIT_JUPITER = "org.junit.jupiter.api.Test";

    @Override
    public String getDisplayName() {
        return "JUnit 4 `@RunWith(Enclosed.class)` to JUnit Jupiter `@Nested`";
    }

    @Override
    public String getDescription() {
        return "Removes the `Enclosed` specification from a class, and adds `Nested` to its inner classes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(ENCLOSED, false);
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @SuppressWarnings("ConstantConditions")
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                final Set<J.Annotation> runWithEnclosedAnnotationSet = FindAnnotations.find(cd.withBody(null),
                        String.format("@%s(%s.class)", RUN_WITH, ENCLOSED));
                for (J.Annotation runWithEnclosed : runWithEnclosedAnnotationSet) {
                    cd.getLeadingAnnotations().remove(runWithEnclosed);
                    cd = cd.withBody((J.Block) new AddNestedAnnotationVisitor().visit(cd.getBody(), ctx, getCursor()));

                    maybeRemoveImport(ENCLOSED);
                    maybeRemoveImport(RUN_WITH);
                    maybeAddImport(NESTED);
                }
                return cd;
            }
        };
    }

    public static class AddNestedAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (hasTestMethods(cd)) {
                cd = cd.withTemplate(getNestedJavaTemplate(ctx), cd.getCoordinates().addAnnotation(Comparator.comparing(
                        J.Annotation::getSimpleName)));
                cd.getModifiers().removeIf(modifier -> modifier.getType().equals(J.Modifier.Type.Static));
            }
            return cd;
        }

        @NonNull
        private JavaTemplate getNestedJavaTemplate(ExecutionContext ctx) {
            return JavaTemplate.builder(this::getCursor, "@Nested")
                    .javaParser(() -> JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9.2")
                            .build())
                    .imports(NESTED)
                    .build();
        }

        private boolean hasTestMethods(final J.ClassDeclaration cd) {
            return !FindAnnotations.find(cd, "@" + TEST_JUNIT4).isEmpty()
                    || !FindAnnotations.find(cd, "@" + TEST_JUNIT_JUPITER).isEmpty();
        }
    }
}
