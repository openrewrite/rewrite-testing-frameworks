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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.stream.Collectors.toList;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpdateBeforeAfterAnnotations extends ScanningRecipe<Set<String>> {
    String displayName = "Migrate JUnit 4 lifecycle annotations to JUnit Jupiter";
    String description = "Replace JUnit 4's `@Before`, `@BeforeClass`, `@After`, and `@AfterClass` annotations with their JUnit Jupiter equivalents. " +
            "Consolidate multiple Java `@Before` or `@After` methods using JUnit 4's deterministic ordering, preserving cleanup after failures. " +
            "Methods with unsupported ordering, overridden lifecycle methods, and Kotlin methods receive annotation migration only.";

    private static final String BEFORE = "org.junit.Before";
    private static final String AFTER = "org.junit.After";
    private static final String FIX_METHOD_ORDER = "org.junit.FixMethodOrder";
    private static final AnnotationMatcher BEFORE_MATCHER = new AnnotationMatcher("@" + BEFORE);
    private static final AnnotationMatcher AFTER_MATCHER = new AnnotationMatcher("@" + AFTER);
    private static final AnnotationMatcher ORDER_MATCHER = new AnnotationMatcher("@" + FIX_METHOD_ORDER);
    private static final Map<String, String> JUNIT4_TO_JUPITER = createJunit4ToJupiterMap();

    private static Map<String, String> createJunit4ToJupiterMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(BEFORE, "org.junit.jupiter.api.BeforeEach");
        map.put(AFTER, "org.junit.jupiter.api.AfterEach");
        map.put("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll");
        map.put("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll");
        return map;
    }

    @Override
    public List<Recipe> getRecipeList() {
        return JUNIT4_TO_JUPITER.entrySet().stream()
                .map(e -> new ChangeType(e.getKey(), e.getValue(), true)).collect(toList());
    }

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> overriddenLifecycleTypes) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getType() == null) {
                    return cd;
                }
                for (JavaType.Method method : cd.getType().getMethods()) {
                    for (String annotation : Arrays.asList(BEFORE, AFTER)) {
                        if (method.getAnnotations().stream().noneMatch(a -> TypeUtils.isOfClassType(a, annotation))) {
                            continue;
                        }
                        for (JavaType.FullyQualified parent = cd.getType().getSupertype(); parent != null; parent = parent.getSupertype()) {
                            if (parent.getMethods().stream().anyMatch(inherited -> inherited.getName().equals(method.getName()) &&
                                    inherited.getParameterTypes().equals(method.getParameterTypes()) &&
                                    inherited.getAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a, annotation)))) {
                                // A wrapper would hide JUnit's per-method override detection and call the override twice.
                                overriddenLifecycleTypes.add(cd.getType().getFullyQualifiedName());
                                overriddenLifecycleTypes.add(parent.getFullyQualifiedName());
                            }
                        }
                    }
                }
                return cd;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> overriddenLifecycleTypes) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                doAfterVisit(new RemoveRedundantJUnit4LifecycleAnnotations());
                // JavaIsoVisitor also adapts to Kotlin, but the consolidation template is Java.
                if (tree instanceof J.CompilationUnit) {
                    doAfterVisit(new JavaConsolidationVisitor(overriddenLifecycleTypes));
                }
                return tree;
            }
        };
    }

    private static class JavaConsolidationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Set<String> overriddenLifecycleTypes;

        private JavaConsolidationVisitor(Set<String> overriddenLifecycleTypes) {
            this.overriddenLifecycleTypes = overriddenLifecycleTypes;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (cd.getType() == null || overriddenLifecycleTypes.contains(cd.getType().getFullyQualifiedName())) {
                return cd;
            }
            boolean hasOwnOrder = cd.getLeadingAnnotations().stream().anyMatch(ORDER_MATCHER::matches);
            if (!hasOwnOrder) {
                for (JavaType.FullyQualified parent = cd.getType().getSupertype(); parent != null; parent = parent.getSupertype()) {
                    if (parent.getAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a, FIX_METHOD_ORDER))) {
                        return cd;
                    }
                }
            }
            boolean nameAscending = false;
            for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                if (ORDER_MATCHER.matches(annotation)) {
                    if (annotation.getArguments() == null || annotation.getArguments().size() != 1) {
                        return cd;
                    }
                    J argument = annotation.getArguments().get(0);
                    if (argument instanceof J.Assignment) {
                        argument = ((J.Assignment) argument).getAssignment();
                    }
                    String order = argument instanceof J.FieldAccess ? ((J.FieldAccess) argument).getSimpleName() :
                            argument instanceof J.Identifier ? ((J.Identifier) argument).getSimpleName() : "";
                    if ("JVM".equals(order)) {
                        return cd;
                    }
                    if (!"NAME_ASCENDING".equals(order) && !"DEFAULT".equals(order)) {
                        return cd;
                    }
                    nameAscending = "NAME_ASCENDING".equals(order);
                }
            }
            cd = consolidateLifecycleMethods(cd, BEFORE_MATCHER, BEFORE, "BeforeEach", "setUp", true, nameAscending, ctx);
            return consolidateLifecycleMethods(cd, AFTER_MATCHER, AFTER, "AfterEach", "tearDown", false, nameAscending, ctx);
        }

        private J.ClassDeclaration consolidateLifecycleMethods(J.ClassDeclaration cd, AnnotationMatcher matcher,
                                                               String junit4Annotation, String jupiterAnnotation,
                                                               String preferredName, boolean before, boolean nameAscending,
                                                               ExecutionContext ctx) {
            List<J.MethodDeclaration> targets = cd.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .filter(method -> method.getLeadingAnnotations().stream().anyMatch(matcher::matches))
                    .collect(toList());
            if (targets.size() < 2) {
                return cd;
            }
            // JUnit 4 lifecycle methods take no arguments. Leave invalid or unsupported signatures alone.
            if (targets.stream().anyMatch(method -> method.getBody() == null ||
                    method.getParameters().stream().anyMatch(parameter -> !(parameter instanceof J.Empty)))) {
                return cd;
            }

            Comparator<J.MethodDeclaration> order = Comparator.comparing(J.MethodDeclaration::getSimpleName);
            if (!nameAscending) {
                order = Comparator.comparingInt((J.MethodDeclaration method) -> method.getSimpleName().hashCode()).thenComparing(order);
            }
            targets.sort(before ? order.reversed() : order);

            String name = uniqueMethodName(cd.getType(), preferredName, junit4Annotation);
            StringBuilder body = new StringBuilder();
            boolean throwsException = !before || targets.stream().anyMatch(method -> method.getThrows() != null && !method.getThrows().isEmpty());
            if (before) {
                for (J.MethodDeclaration target : targets) {
                    body.append(target.getSimpleName()).append("();\n");
                }
            } else {
                body.append("List<Throwable> failures = new ArrayList<>();\n");
                for (J.MethodDeclaration target : targets) {
                    body.append("try {\n").append(target.getSimpleName()).append("();\n")
                            .append("} catch (Throwable t) { failures.add(t); }\n");
                }
                body.append("if (failures.size() == 1) { throw failures.get(0); }\n")
                        .append("if (failures.size() > 1) { throw new MultipleFailuresError(\"Lifecycle failures\", failures); }\n");
            }

            Set<UUID> targetIds = new HashSet<>();
            targets.forEach(method -> targetIds.add(method.getId()));
            Cursor bodyCursor = new Cursor(getCursor(), cd.getBody());
            List<Statement> statements = ListUtils.map(cd.getBody().getStatements(), statement -> {
                if (!(statement instanceof J.MethodDeclaration) || !targetIds.contains(statement.getId())) {
                    return statement;
                }
                J.MethodDeclaration method = (J.MethodDeclaration) statement;
                // Exclude the body so local and anonymous classes keep their lifecycle annotations.
                J.MethodDeclaration updated = (J.MethodDeclaration) new RemoveAnnotationVisitor(matcher)
                        .visitNonNull(method.withBody(null), ctx, bodyCursor);
                return updated.withBody(method.getBody());
            });
            statements = ListUtils.mapFirst(statements, statement -> {
                String whitespace = statement.getPrefix().getWhitespace();
                long lineBreaks = whitespace.chars().filter(c -> c == '\n').count();
                if (lineBreaks < 2) {
                    return statement.withPrefix(statement.getPrefix().withWhitespace((lineBreaks == 0 ? "\n\n" : "\n") + whitespace));
                }
                return statement;
            });
            cd = cd.withBody(cd.getBody().withStatements(statements));
            maybeRemoveImport(junit4Annotation);
            maybeAddImport("org.junit.jupiter.api." + jupiterAnnotation);
            maybeAddImport("java.util.List");
            maybeAddImport("java.util.ArrayList");
            maybeAddImport("org.opentest4j.MultipleFailuresError");
            return JavaTemplate.builder("@" + jupiterAnnotation + "\nvoid " + name + "()" +
                            (throwsException ? " throws Throwable" : "") + " {\n" + body + "}")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "opentest4j"))
                    .imports("org.junit.jupiter.api." + jupiterAnnotation, "java.util.List", "java.util.ArrayList",
                            "org.opentest4j.MultipleFailuresError")
                    .build()
                    .apply(updateCursor(cd), cd.getBody().getCoordinates().firstStatement());
        }

        private static String uniqueMethodName(JavaType.FullyQualified type, String preferredName, String annotation) {
            Set<String> names = new HashSet<>();
            collectMethodNames(type, names, new HashSet<>());
            for (JavaType.FullyQualified parent = type.getSupertype(); parent != null; parent = parent.getSupertype()) {
                long lifecycleMethods = parent.getMethods().stream()
                        .filter(method -> method.getAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a, annotation)))
                        .count();
                if (lifecycleMethods > 1) {
                    // Reserve the wrapper an ancestor will generate during this recipe run, too.
                    names.add(uniqueMethodName(parent, preferredName, annotation));
                }
            }
            String name = preferredName;
            for (int suffix = 1; names.contains(name); suffix++) {
                name = preferredName + suffix;
            }
            return name;
        }

        private static void collectMethodNames(JavaType.FullyQualified type, Set<String> names, Set<String> visited) {
            if (!visited.add(type.getFullyQualifiedName())) {
                return;
            }
            type.getMethods().forEach(method -> names.add(method.getName()));
            if (type.getSupertype() != null) {
                collectMethodNames(type.getSupertype(), names, visited);
            }
            for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                collectMethodNames(anInterface, names, visited);
            }
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
