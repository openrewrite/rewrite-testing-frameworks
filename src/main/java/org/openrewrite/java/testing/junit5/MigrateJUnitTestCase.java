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
import org.jspecify.annotations.Nullable;
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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.UseStaticImport;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

public class MigrateJUnitTestCase extends ScanningRecipe<MigrateJUnitTestCase.Accumulator> {

    private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUNIT_AFTER_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.*After*");
    private static final AnnotationMatcher JUNIT_BEFORE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.*Before*");
    private static final MethodMatcher TEST_CASE_RUN_MATCHER = new MethodMatcher("junit.framework.TestCase run(junit.framework.TestResult)", true);

    private static boolean isSupertypeTestCase(JavaType.@Nullable FullyQualified fullyQualified) {
        if (fullyQualified == null || fullyQualified.getSupertype() == null || "java.lang.Object".equals(fullyQualified.getFullyQualifiedName())) {
            return false;
        }

        JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(fullyQualified);
        if (fqType != null && "junit.framework.TestCase".equals(fqType.getFullyQualifiedName())) {
            return true;
        }
        return isSupertypeTestCase(fullyQualified.getSupertype());
    }

    @Getter
    final String displayName = "Migrate JUnit 4 `TestCase` to JUnit Jupiter";

    @Getter
    final String description = "Convert JUnit 4 `TestCase` to JUnit Jupiter.";

    static class Accumulator {
        final Map<@Nullable JavaProject, ProjectState> projects = new HashMap<>();
        final Set<UUID> skippedCompilationUnits = new HashSet<>();
        private @Nullable Map<ProjectState, Set<String>> redundant;

        private @Nullable ProjectState constructorOwner(ProjectState project, String signature) {
            if (project.constructors.containsKey(signature)) {
                return project;
            }
            ProjectState owner = null;
            for (ProjectState candidate : projects.values()) {
                if (candidate.constructors.containsKey(signature)) {
                    if (owner != null) {
                        return null;
                    }
                    owner = candidate;
                }
            }
            return owner;
        }

        Map<ProjectState, Set<String>> redundantConstructors() {
            if (redundant != null) {
                return redundant;
            }
            Map<ProjectState, Set<String>> retained = new HashMap<>();
            Map<ProjectState, Set<String>> redundant = new HashMap<>();
            for (ProjectState project : projects.values()) {
                retained.put(project, new HashSet<>());
                redundant.put(project, new HashSet<>());
            }
            for (ProjectState project : projects.values()) {
                for (Map.Entry<String, Boolean> reference : project.references.entrySet()) {
                    ProjectState owner = constructorOwner(project, reference.getKey());
                    if (owner == null) {
                        // A caller outside projects declaring the same signature has ambiguous ownership.
                        for (ProjectState candidate : projects.values()) {
                            if (candidate.constructors.containsKey(reference.getKey())) {
                                retained.get(candidate).add(reference.getKey());
                            }
                        }
                    } else if (reference.getValue()) {
                        retained.get(owner).add(reference.getKey());
                    }
                }
            }
            for (ProjectState project : projects.values()) {
                for (ProjectState candidate : projects.values()) {
                    for (Map.Entry<String, J.MethodDeclaration> entry : candidate.constructors.entrySet()) {
                        JavaType.Method type = entry.getValue().getMethodType();
                        if (type != null && project.nonJavaReferencedClasses.contains(type.getDeclaringType().getFullyQualifiedName())) {
                            ProjectState owner = constructorOwner(project, entry.getKey());
                            if (owner == null || owner == candidate) {
                                retained.get(candidate).add(entry.getKey());
                            }
                        }
                    }
                }
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (ProjectState project : projects.values()) {
                    for (Map.Entry<String, J.MethodDeclaration> entry : project.constructors.entrySet()) {
                        J.MethodDeclaration declaration = entry.getValue();
                        JavaType.Method type = declaration.getMethodType();
                        if (type == null || !isSupertypeTestCase(type.getDeclaringType()) || declaration.getBody() == null ||
                            retained.get(project).contains(entry.getKey()) || redundant.get(project).contains(entry.getKey()) ||
                            !type.getThrownExceptions().isEmpty() || type.hasFlags(Flag.Varargs) ||
                            type.getParameterTypes().stream().anyMatch(MigrateJUnitTestCase::hasGenericParameter)) {
                            continue;
                        }
                        if (hasRedundantBody(project, declaration, redundant)) {
                            changed |= redundant.get(project).add(entry.getKey());
                        }
                    }
                }
            }
            changed = true;
            while (changed) {
                changed = false;
                Map<ProjectState, Set<String>> removed = new HashMap<>();
                for (ProjectState project : projects.values()) {
                    Set<String> signatures = redundant.get(project);
                    Set<String> rejected = new HashSet<>();
                    removed.put(project, rejected);
                    for (String signature : signatures) {
                        J.MethodDeclaration declaration = project.constructors.get(signature);
                        JavaType.Method type = declaration.getMethodType();
                        if (type == null) {
                            rejected.add(signature);
                            continue;
                        }
                        List<J.MethodDeclaration> constructors = project.constructorsByClass.get(type.getDeclaringType().getFullyQualifiedName());
                        long redundantCount = constructors.stream()
                                .filter(constructor -> signatures.contains(constructorSignature(constructor.getMethodType())))
                                .count();
                        boolean retainedNoArgumentConstructor = constructors.stream().anyMatch(constructor ->
                                constructor.getMethodType() != null && constructor.getMethodType().getParameterTypes().isEmpty() &&
                                !signatures.contains(constructorSignature(constructor.getMethodType())));
                        boolean incompatibleConstructors = redundantCount > 1 && constructors.stream()
                                .filter(constructor -> signatures.contains(constructorSignature(constructor.getMethodType())))
                                .anyMatch(constructor -> !constructor.getLeadingAnnotations().isEmpty() ||
                                        constructor.hasModifier(J.Modifier.Type.Public) != declaration.hasModifier(J.Modifier.Type.Public) ||
                                        constructor.hasModifier(J.Modifier.Type.Protected) != declaration.hasModifier(J.Modifier.Type.Protected) ||
                                        constructor.hasModifier(J.Modifier.Type.Private) != declaration.hasModifier(J.Modifier.Type.Private));
                        if (retainedNoArgumentConstructor || incompatibleConstructors ||
                            !hasRedundantBody(project, declaration, redundant)) {
                            rejected.add(signature);
                        }
                    }
                }
                for (Map.Entry<ProjectState, Set<String>> entry : removed.entrySet()) {
                    changed |= redundant.get(entry.getKey()).removeAll(entry.getValue());
                }
            }
            this.redundant = redundant;
            return redundant;
        }

        private boolean hasRedundantBody(ProjectState project, J.MethodDeclaration declaration,
                                         Map<ProjectState, Set<String>> redundant) {
            if (declaration.getBody() == null) {
                return false;
            }
            List<Statement> statements = declaration.getBody().getStatements();
            if (statements.isEmpty()) {
                return true;
            }
            if (statements.size() != 1 || !(statements.get(0) instanceof J.MethodInvocation)) {
                return false;
            }
            J.MethodInvocation delegation = (J.MethodInvocation) statements.get(0);
            String signature = constructorSignature(delegation.getMethodType());
            ProjectState owner = signature == null ? null : constructorOwner(project, signature);
            return safeArguments(delegation.getArguments()) &&
                   (TestCaseVisitor.TEST_CASE_SUPER_MATCHER.matches(delegation) ||
                    owner != null && redundant.get(owner).contains(signature));
        }
    }

    static class ProjectState {
        final Map<String, J.MethodDeclaration> constructors = new HashMap<>();
        final Map<String, Boolean> references = new HashMap<>();
        final Set<String> nonJavaReferencedClasses = new HashSet<>();
        final Map<String, List<J.MethodDeclaration>> constructorsByClass = new HashMap<>();
    }

    private static @Nullable String constructorSignature(JavaType.@Nullable Method method) {
        return method == null ? null : MethodMatcher.methodPattern(method);
    }

    private static boolean hasGenericParameter(JavaType type) {
        if (type instanceof JavaType.GenericTypeVariable) {
            return true;
        }
        if (type instanceof JavaType.Array) {
            return hasGenericParameter(((JavaType.Array) type).getElemType());
        }
        return type instanceof JavaType.Parameterized &&
               ((JavaType.Parameterized) type).getTypeParameters().stream().anyMatch(MigrateJUnitTestCase::hasGenericParameter);
    }

    private static boolean safeArguments(List<Expression> arguments) {
        return arguments.stream().allMatch(argument -> argument instanceof J.Empty || argument instanceof J.Literal ||
                argument instanceof J.Identifier && ((J.Identifier) argument).getFieldType() != null &&
                ((J.Identifier) argument).getFieldType().getOwner() instanceof JavaType.Method);
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();
                if (!(tree instanceof JavaSourceFile)) {
                    return tree;
                }
                @Nullable JavaProject project = tree.getMarkers().findFirst(JavaProject.class).orElse(null);
                ProjectState state = acc.projects.computeIfAbsent(project, ignored -> new ProjectState());
                if (tree instanceof J.CompilationUnit) {
                    // Preserve upstream's exclusion throughout constructor analysis and caller rewriting.
                    boolean skipped = new DeclaresMethod<>(TEST_CASE_RUN_MATCHER).visit(tree, ctx) != tree;
                    if (skipped) {
                        acc.skippedCompilationUnits.add(tree.getId());
                    }
                    return constructorScanner(state, skipped).visitNonNull(tree, ctx, getCursor().getParentOrThrow());
                }
                for (JavaType.Method method : ((JavaSourceFile) tree).getTypesInUse().getUsedMethods()) {
                    if (method.isConstructor()) {
                        state.nonJavaReferencedClasses.add(method.getDeclaringType().getFullyQualifiedName());
                    }
                }
                return tree;
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> constructorScanner(ProjectState project, boolean skipped) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                JavaType.Method type = method.getMethodType();
                if (method.isConstructor() && type != null) {
                    project.constructors.put(MethodMatcher.methodPattern(type), method);
                    if (skipped) {
                        project.references.put(MethodMatcher.methodPattern(type), true);
                    }
                    project.constructorsByClass.computeIfAbsent(type.getDeclaringType().getFullyQualifiedName(), ignored -> new ArrayList<>())
                            .add(method);
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                JavaType.Method type = newClass.getConstructorType();
                if (type != null) {
                    project.references.merge(MethodMatcher.methodPattern(type), skipped || !safeArguments(newClass.getArguments()), Boolean::logicalOr);
                }
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                JavaType.Method type = method.getMethodType();
                if (type != null && type.isConstructor()) {
                    project.references.merge(MethodMatcher.methodPattern(type), skipped || !safeArguments(method.getArguments()), Boolean::logicalOr);
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                JavaType.Method type = memberRef.getMethodType();
                if (type != null && type.isConstructor()) {
                    project.references.put(MethodMatcher.methodPattern(type), true);
                }
                return super.visitMemberReference(memberRef, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        Map<ProjectState, Set<String>> redundantByProject = acc.redundantConstructors();
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (acc.skippedCompilationUnits.contains(cu.getId())) {
                    return cu;
                }
                @Nullable JavaProject marker = cu.getMarkers().findFirst(JavaProject.class).orElse(null);
                ProjectState project = acc.projects.get(marker);
                Set<String> redundant = new HashSet<>();
                if (project != null) {
                    for (Map.Entry<ProjectState, Set<String>> entry : redundantByProject.entrySet()) {
                        for (String signature : entry.getValue()) {
                            if (acc.constructorOwner(project, signature) == entry.getKey()) {
                                redundant.add(signature);
                            }
                        }
                    }
                }
                J.CompilationUnit c = redundant.isEmpty() ? cu :
                        (J.CompilationUnit) constructorVisitor(redundant).visitNonNull(cu, ctx, getCursor().getParentOrThrow());
                return (J.CompilationUnit) migrationVisitor(redundant).visitNonNull(c, ctx, getCursor().getParentOrThrow());
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> constructorVisitor(Set<String> redundant) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (method.getBody() == null || !redundant.contains(constructorSignature(method.getMethodType()))) {
                    return super.visitMethodDeclaration(method, ctx);
                }
                return method.withBody(method.getBody().withStatements(emptyList()));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                JavaType.Method type = n.getConstructorType();
                if (type == null || type.getParameterTypes().isEmpty() || !redundant.contains(MethodMatcher.methodPattern(type))) {
                    return n;
                }
                return n.withArguments(emptyList())
                        .withConstructorType(type.withParameterTypes(emptyList()).withParameterNames(emptyList()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (type == null || type.getParameterTypes().isEmpty() || !redundant.contains(MethodMatcher.methodPattern(type))) {
                    return m;
                }
                return m.withArguments(emptyList())
                        .withMethodType(type.withParameterTypes(emptyList()).withParameterNames(emptyList()));
            }
        };
    }

    private TreeVisitor<?, ExecutionContext> migrationVisitor(Set<String> redundant) {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>("junit.framework.TestCase", false),
                        new UsesType<>("junit.framework.Assert", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                        doAfterVisit(new TestCaseVisitor(redundant));
                        // ChangeType for org.junit.Assert method invocations because TestCase extends org.junit.Assert
                        doAfterVisit(new ChangeType("junit.framework.TestCase", "org.junit.Assert", true).getVisitor());
                        doAfterVisit(new ChangeType("junit.framework.Assert", "org.junit.Assert", true).getVisitor());
                        doAfterVisit(new AssertToAssertions.AssertToAssertionsVisitor());
                        doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions assert*(..)").getVisitor());
                        doAfterVisit(new UseStaticImport("org.junit.jupiter.api.Assertions fail*(..)").getVisitor());
                        return c;
                    }

                    @SuppressWarnings("ConstantConditions")
                    @Override
                    public   J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if ((mi.getSelect() != null && TypeUtils.isOfClassType(mi.getSelect().getType(), "junit.framework.TestCase")) ||
                            (mi.getMethodType() != null && TypeUtils.isOfClassType(mi.getMethodType().getDeclaringType(), "junit.framework.TestCase"))) {
                            String name = mi.getSimpleName();
                            // setUp and tearDown will be invoked via Before and After annotations
                            if ("setUp".equals(name) || "tearDown".equals(name)) {
                                return null;
                            }
                            if ("setName".equals(name)) {
                                mi = mi.withPrefix(mi.getPrefix().withComments(ListUtils.concat(mi.getPrefix().getComments(), new TextComment(false, "", "", Markers.EMPTY))));
                            }
                        }
                        return mi;
                    }
                });
    }

    private static class TestCaseVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher OVERRIDE_ANNOTATION_MATCHER = new AnnotationMatcher("@java.lang.Override");
        private static final MethodMatcher TEST_CASE_SUPER_MATCHER = new MethodMatcher("junit.framework.TestCase <constructor>(..)");
        private static final Set<String> SUPERTYPES_REMOVED_BY_MIGRATION = new HashSet<>(asList(
                "junit.framework.TestCase", "junit.framework.Assert", "junit.framework.Test"));

        private final Set<String> redundant;

        TestCaseVisitor(Set<String> redundant) {
            this.redundant = redundant;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (!isSupertypeTestCase(classDecl.getType())) {
                return classDecl;
            }
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            if (cd.getExtends() != null && cd.getExtends().getType() != null) {
                JavaType.FullyQualified fullQualifiedExtension = TypeUtils.asFullyQualified(cd.getExtends().getType());
                if (fullQualifiedExtension != null && "junit.framework.TestCase".equals(fullQualifiedExtension.getFullyQualifiedName())) {
                    cd = cd.withExtends(null);
                }
            }
            maybeRemoveImport("junit.framework.TestCase");
            return cd;
        }

        @Override
        public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            updateCursor(md);

            JavaType.Method constructorType = md.getMethodType();
            if (md.isConstructor() && constructorType != null && redundant.contains(MethodMatcher.methodPattern(constructorType))) {
                J.ClassDeclaration owner = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                J.MethodDeclaration noArgumentConstructor = owner.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(other -> other.isConstructor() && redundant.contains(constructorSignature(other.getMethodType())))
                        .min(Comparator.comparingInt(other -> other.getMethodType() == null ? Integer.MAX_VALUE : other.getMethodType().getParameterTypes().size()))
                        .orElse(md);
                if (!md.getId().equals(noArgumentConstructor.getId())) {
                    return null;
                }
                boolean hasOtherConstructors = owner.getBody().getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(other -> other.isConstructor() && !redundant.contains(constructorSignature(other.getMethodType())));
                if (!hasOtherConstructors && md.hasModifier(J.Modifier.Type.Public) && md.getLeadingAnnotations().isEmpty()) {
                    return null;
                }
                md = md.withParameters(emptyList())
                        .withMethodType(constructorType.withParameterTypes(emptyList()).withParameterNames(emptyList()));
            }

            // Remove suite() methods that return junit.framework.Test or TestSuite
            if ("suite".equals(md.getSimpleName()) &&
                md.hasModifier(J.Modifier.Type.Static) &&
                md.getMethodType() != null &&
                (TypeUtils.isOfClassType(md.getMethodType().getReturnType(), "junit.framework.Test") ||
                 TypeUtils.isOfClassType(md.getMethodType().getReturnType(), "junit.framework.TestSuite"))) {
                maybeRemoveImport("junit.framework.Test");
                maybeRemoveImport("junit.framework.TestSuite");
                return null;
            }

            if (md.getSimpleName().startsWith("test") && md.getLeadingAnnotations().stream().noneMatch(JUNIT_TEST_ANNOTATION_MATCHER::matches)) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@Test", "org.junit.jupiter.api.Test", ctx);
            } else if ("setUp".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT_BEFORE_ANNOTATION_MATCHER::matches)) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@BeforeEach", "org.junit.jupiter.api.BeforeEach", ctx);
            } else if ("tearDown".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT_AFTER_ANNOTATION_MATCHER::matches)) {
                md = updateMethodDeclarationAnnotationAndModifier(md, "@AfterEach", "org.junit.jupiter.api.AfterEach", ctx);
            }
            return maybeRemoveOverrideAnnotation(md, ctx);
        }

        @Override
        public  J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            // If the class no longer extends TestCase there should no longer be calls to TestCase.super()
            if (TEST_CASE_SUPER_MATCHER.matches(method)) {
                //noinspection DataFlowIssue
                return null;
            }
            return super.visitMethodInvocation(method, ctx);
        }

        private J.MethodDeclaration updateMethodDeclarationAnnotationAndModifier(J.MethodDeclaration methodDeclaration, String annotation, String fullyQualifiedAnnotation, ExecutionContext ctx) {
            J.MethodDeclaration md = methodDeclaration;
            if (FindAnnotations.find(methodDeclaration.withBody(null), "@" + fullyQualifiedAnnotation).isEmpty()) {
                md = JavaTemplate.builder(annotation)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "junit-jupiter-api-5"))
                        .imports(fullyQualifiedAnnotation).build()
                        .apply(getCursor(), methodDeclaration.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                md = maybeAddPublicModifier(md);
                maybeAddImport(fullyQualifiedAnnotation);
            }
            return md;
        }

        private J.MethodDeclaration maybeAddPublicModifier(J.MethodDeclaration md) {
            List<J.Modifier> modifiers = ListUtils.map(md.getModifiers(), modifier -> {
                if (modifier.getType() == J.Modifier.Type.Protected) {
                    return modifier.withType(J.Modifier.Type.Public);
                }
                return modifier;
            });
            return md.withModifiers(modifiers);
        }

        private J.MethodDeclaration maybeRemoveOverrideAnnotation(J.MethodDeclaration md, ExecutionContext ctx) {
            JavaType.Method methodType = md.getMethodType();
            if (methodType == null ||
                md.getLeadingAnnotations().stream().noneMatch(OVERRIDE_ANNOTATION_MATCHER::matches) ||
                stillOverridesAfterMigration(methodType)) {
                return md;
            }
            J.MethodDeclaration withoutBody = (J.MethodDeclaration) new RemoveAnnotationVisitor(OVERRIDE_ANNOTATION_MATCHER)
                    .visitNonNull(md.withBody(null), ctx, getCursor().getParentOrThrow());
            return withoutBody.withBody(md.getBody());
        }

        private static boolean stillOverridesAfterMigration(JavaType.Method method) {
            Optional<JavaType.Method> overridden = TypeUtils.findOverriddenMethod(method);
            while (overridden.isPresent()) {
                if (!SUPERTYPES_REMOVED_BY_MIGRATION.contains(overridden.get().getDeclaringType().getFullyQualifiedName())) {
                    return true;
                }
                overridden = TypeUtils.findOverriddenMethod(overridden.get());
            }
            return false;
        }
    }
}
