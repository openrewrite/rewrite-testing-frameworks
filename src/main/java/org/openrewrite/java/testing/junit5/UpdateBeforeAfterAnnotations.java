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
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpdateBeforeAfterAnnotations extends ScanningRecipe<UpdateBeforeAfterAnnotations.Accumulator> {
    String displayName = "Migrate JUnit 4 lifecycle annotations to JUnit Jupiter";
    String description = "Replace JUnit 4's `@Before`, `@BeforeClass`, `@After`, and `@AfterClass` annotations with their JUnit Jupiter equivalents. " +
            "When multiple `@Before` or `@After` methods are found in the same class, they are consolidated into a single method.";

    private static final Map<String, String> JUNIT4_TO_JUPITER = createJunit4ToJupiterMap();

    private static Map<String, String> createJunit4ToJupiterMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("org.junit.Before", "org.junit.jupiter.api.BeforeEach");
        map.put("org.junit.After", "org.junit.jupiter.api.AfterEach");
        map.put("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll");
        map.put("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll");
        return map;
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator(new HashSet<>(), new HashSet<>());
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>("org.junit.BeforeClass", false),
                        new UsesType<>("org.junit.Before", false),
                        new UsesType<>("org.junit.After", false),
                        new UsesType<>("org.junit.AfterClass", false)
                ),
                new ScanForMultipleLifecycleMethods(acc));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                // First do Java-specific consolidation
                doAfterVisit(new JavaConsolidationVisitor(acc));
                // Then handle annotation type changes for all files (Java and Kotlin)
                doAfterVisit(new RemoveRedundantJUnit4LifecycleAnnotations());
                JUNIT4_TO_JUPITER.forEach((from, to) -> doAfterVisit(new ChangeType(from, to, true).getVisitor()));
                return tree;
            }
        };
    }

    @Value
    public static class Accumulator {
        Set<String> classesWithMultipleBefore;
        Set<String> classesWithMultipleAfter;
    }

    private static class ScanForMultipleLifecycleMethods extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher BEFORE_MATCHER = new AnnotationMatcher("@org.junit.Before");
        private static final AnnotationMatcher AFTER_MATCHER = new AnnotationMatcher("@org.junit.After");

        private final Accumulator acc;

        ScanForMultipleLifecycleMethods(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            // Count @Before methods
            long beforeCount = cd.getBody().getStatements().stream()
                    .filter(stmt -> stmt instanceof J.MethodDeclaration)
                    .map(stmt -> (J.MethodDeclaration) stmt)
                    .filter(method -> method.getLeadingAnnotations().stream()
                            .anyMatch(BEFORE_MATCHER::matches))
                    .count();

            // Count @After methods
            long afterCount = cd.getBody().getStatements().stream()
                    .filter(stmt -> stmt instanceof J.MethodDeclaration)
                    .map(stmt -> (J.MethodDeclaration) stmt)
                    .filter(method -> method.getLeadingAnnotations().stream()
                            .anyMatch(AFTER_MATCHER::matches))
                    .count();

            if (beforeCount > 1) {
                acc.classesWithMultipleBefore.add(cd.getType().getFullyQualifiedName());
            }
            if (afterCount > 1) {
                acc.classesWithMultipleAfter.add(cd.getType().getFullyQualifiedName());
            }

            return cd;
        }
    }

    public static class JavaConsolidationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher BEFORE_MATCHER = new AnnotationMatcher("@org.junit.Before");
        private static final AnnotationMatcher AFTER_MATCHER = new AnnotationMatcher("@org.junit.After");

        private final Accumulator acc;

        JavaConsolidationVisitor(Accumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            String className = cd.getType() != null ? cd.getType().getFullyQualifiedName() : null;
            if (className == null) {
                return cd;
            }

            // Consolidate multiple @Before methods if needed
            if (acc.classesWithMultipleBefore.contains(className)) {
                cd = consolidateLifecycleMethods(cd, BEFORE_MATCHER, "BeforeEach", "setUp",
                        "org.junit.Before", ctx);
            }

            // Consolidate multiple @After methods if needed
            if (acc.classesWithMultipleAfter.contains(className)) {
                cd = consolidateLifecycleMethods(cd, AFTER_MATCHER, "AfterEach", "tearDown",
                        "org.junit.After", ctx);
            }

            return cd;
        }

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            // First, visit the tree to perform consolidations
            J.CompilationUnit updated = super.visitCompilationUnit(cu, ctx);

            // After visiting all classes and consolidating, schedule the annotation type changes
            doAfterVisit(new RemoveRedundantJUnit4LifecycleAnnotations());
            JUNIT4_TO_JUPITER.forEach((from, to) -> doAfterVisit(new ChangeType(from, to, true).getVisitor()));

            return updated;
        }

        private J.ClassDeclaration consolidateLifecycleMethods(J.ClassDeclaration cd, AnnotationMatcher matcher,
                                                               String jupiterAnnotation, String consolidatedMethodName,
                                                               String junit4AnnotationFqn, ExecutionContext ctx) {
            // Save original for autoformat comparison
            J.ClassDeclaration original = cd;

            // Find all methods with the target annotation
            List<J.MethodDeclaration> targetMethods = cd.getBody().getStatements().stream()
                    .filter(stmt -> stmt instanceof J.MethodDeclaration)
                    .map(stmt -> (J.MethodDeclaration) stmt)
                    .filter(method -> method.getLeadingAnnotations().stream()
                            .anyMatch(matcher::matches))
                    .collect(Collectors.toList());

            if (targetMethods.size() <= 1) {
                return cd;
            }

            // Add necessary imports
            maybeRemoveImport(junit4AnnotationFqn);
            maybeAddImport("org.junit.jupiter.api." + jupiterAnnotation);

            // Remove the annotations from original methods - DON'T change spacing yet
            cd = cd.withBody(cd.getBody().withStatements(
                    ListUtils.map(cd.getBody().getStatements(), stmt -> {
                        if (stmt instanceof J.MethodDeclaration) {
                            J.MethodDeclaration method = (J.MethodDeclaration) stmt;
                            if (method.getLeadingAnnotations().stream().anyMatch(matcher::matches)) {
                                // Remove the annotation but keep the prefix
                                return method.withLeadingAnnotations(
                                        method.getLeadingAnnotations().stream()
                                                .filter(ann -> !matcher.matches(ann))
                                                .collect(Collectors.toList())
                                );
                            }
                        }
                        return stmt;
                    })
            ));

            // Create consolidated method
            String methodBody = targetMethods.stream()
                    .map(m -> m.getSimpleName() + "();")
                    .collect(Collectors.joining("\n        "));

            JavaTemplate template = JavaTemplate.builder(
                    "@" + jupiterAnnotation + "\n" +
                    "void " + consolidatedMethodName + "() {\n" +
                    "    " + methodBody + "\n" +
                    "}"
            )
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "junit-jupiter-api"))
                    .imports("org.junit.jupiter.api." + jupiterAnnotation)
                    .build();

            cd = template.apply(updateCursor(cd), cd.getBody().getCoordinates().firstStatement());

            // Manually fix spacing: ALL members should have a blank line before them
            cd = cd.withBody(cd.getBody().withStatements(
                    ListUtils.map(cd.getBody().getStatements(), stmt -> {
                        String whitespace = stmt.getPrefix().getWhitespace();
                        // Extract just the indentation from the last line
                        int lastNewline = whitespace.lastIndexOf('\n');
                        String indent = lastNewline >= 0 ? whitespace.substring(lastNewline + 1) : whitespace;

                        // All statements: two newlines (creating a blank line) + indent
                        return stmt.withPrefix(stmt.getPrefix().withWhitespace("\n\n" + indent));
                    })
            ));

            // Apply autoformat to clean up any trailing spaces on blank lines
            return maybeAutoFormat(original, cd, ctx);
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
