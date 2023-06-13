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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.NonNull;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class AddMissingNested extends Recipe {
    private static final String NESTED = "org.junit.jupiter.api.Nested";
    private static final List<String> TEST_ANNOTATIONS = Arrays.asList(
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.TestTemplate",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.TestFactory");

    @SuppressWarnings("unchecked")
    private static final TreeVisitor<?, ExecutionContext> PRECONDITION =
            Preconditions.or(TEST_ANNOTATIONS.stream().map(r -> new UsesType<>(r, false)).toArray(UsesType[]::new));

    @Override
    public String getDisplayName() {
        return "JUnit 5 inner test classes should be annotated with `@Nested`";
    }

    @Override
    public String getDescription() {
        return "Adds `@Nested` to inner classes that contain JUnit 5 tests.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-5790");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(PRECONDITION, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                cd = cd.withBody((J.Block) new AddNestedAnnotationVisitor().visitNonNull(cd.getBody(), ctx, getCursor()));
                maybeAddImport(NESTED);
                return cd;
            }
        });
    }

    public static class AddNestedAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            boolean alreadyNested = classDecl.getLeadingAnnotations().stream()
                    .anyMatch(a -> TypeUtils.isOfClassType(a.getType(), NESTED));
            if (!alreadyNested && hasTestMethods(cd)) {
                cd = getNestedJavaTemplate(ctx).apply(updateCursor(cd),
                        cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                cd.getModifiers().removeIf(modifier -> modifier.getType().equals(J.Modifier.Type.Static));
            }
            return cd;
        }

        @NonNull
        private JavaTemplate getNestedJavaTemplate(ExecutionContext ctx) {
            return JavaTemplate.builder("@Nested")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api-5.9"))
                    .imports(NESTED)
                    .build();
        }

        private static boolean hasTestMethods(final J.ClassDeclaration cd) {
            return TEST_ANNOTATIONS.stream().anyMatch(ann -> !FindAnnotations.find(cd, "@" + ann).isEmpty());
        }
    }
}
