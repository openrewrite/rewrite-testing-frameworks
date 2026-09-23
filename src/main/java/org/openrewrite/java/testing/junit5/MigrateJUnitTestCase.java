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
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

public class MigrateJUnitTestCase extends ScanningRecipe<MigrateJUnitTestCase.Constructors> {

    private static final AnnotationMatcher JUNIT_TEST_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUNIT_AFTER_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.*After*");
    private static final AnnotationMatcher JUNIT_BEFORE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.junit.*Before*");

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

    static class Constructors {
        final Map<String, J.MethodDeclaration> declarations = new HashMap<>();
        final Set<String> referencedUnsafely = new HashSet<>();

        Set<String> removable() {
            Set<String> removed = new HashSet<>();
            boolean changed;
            do {
                changed = false;
                for (Map.Entry<String, J.MethodDeclaration> entry : declarations.entrySet()) {
                    J.Block body = entry.getValue().getBody();
                    if (body == null) {
                        continue;
                    }
                    List<Statement> statements = body.getStatements();
                    if (statements.isEmpty() || statements.size() == 1 &&
                            statements.get(0) instanceof J.MethodInvocation &&
                            redundantDelegation((J.MethodInvocation) statements.get(0), removed)) {
                        changed |= removed.add(entry.getKey());
                    }
                }
            } while (changed);

            // Removing every constructor leaves an implicit no-arg constructor. If any
            // overload must stay, retain the others too rather than inventing a new overload.
            do {
                Set<String> retainedClasses = new HashSet<>();
                for (String constructor : declarations.keySet()) {
                    if (!removed.contains(constructor) || referencedUnsafely.contains(constructor)) {
                        retainedClasses.add(declarations.get(constructor).getMethodType().getDeclaringType().getFullyQualifiedName());
                    }
                }
                changed = removed.removeIf(constructor -> {
                    if (retainedClasses.contains(declarations.get(constructor).getMethodType().getDeclaringType().getFullyQualifiedName())) {
                        return true;
                    }
                    J.Block body = declarations.get(constructor).getBody();
                    return body != null && !body.getStatements().isEmpty() &&
                           !redundantDelegation((J.MethodInvocation) body.getStatements().get(0), removed);
                });
            } while (changed);
            return removed;
        }

        private static boolean redundantDelegation(J.MethodInvocation invocation, Set<String> removed) {
            return ("super".equals(invocation.getSimpleName()) || "this".equals(invocation.getSimpleName())) &&
                   safeToDiscard(invocation.getArguments()) &&
                   (TestCaseVisitor.TEST_CASE_SUPER_MATCHER.matches(invocation) || removed.contains(signature(invocation.getMethodType())));
        }
    }

    private static String signature(JavaType.@Nullable Method method) {
        // ChangeType updates the declaring type's hierarchy during migration. The
        // signature, unlike JavaType.Method equality, remains stable across files/cycles.
        return method == null ? "" : method.getDeclaringType().getFullyQualifiedName() +
                                     "#" + method.getName() + method.getParameterTypes();
    }

    private static boolean safeToDiscard(List<Expression> arguments) {
        return arguments.stream().allMatch(argument -> {
            if (argument instanceof J.Identifier) {
                JavaType.Variable variable = ((J.Identifier) argument).getFieldType();
                // Only local/parameter reads are safe: field reads can trigger class
                // initialization or have volatile memory semantics.
                return variable != null && variable.getOwner() instanceof JavaType.Method;
            }
            return argument instanceof J.Literal || argument instanceof J.Empty;
        });
    }

    @Override
    public Constructors getInitialValue(ExecutionContext ctx) {
        return new Constructors();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Constructors acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                JavaType.Method type = method.getMethodType();
                if (method.isConstructor() && type != null && isSupertypeTestCase(type.getDeclaringType())) {
                    acc.declarations.put(signature(type), method);
                    if (method.getThrows() != null && !method.getThrows().isEmpty() ||
                            !method.getLeadingAnnotations().isEmpty()) {
                        acc.referencedUnsafely.add(signature(type));
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (newClass.getConstructorType() != null && !safeToDiscard(newClass.getArguments())) {
                    acc.referencedUnsafely.add(signature(newClass.getConstructorType()));
                }
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getMethodType() != null && !safeToDiscard(method.getArguments())) {
                    acc.referencedUnsafely.add(signature(method.getMethodType()));
                }
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                if (memberRef.getMethodType() != null) {
                    acc.referencedUnsafely.add(signature(memberRef.getMethodType()));
                }
                return super.visitMemberReference(memberRef, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Constructors acc) {
        Set<String> removed = acc.removable();
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                doAfterVisit(getMigrationVisitor());
                return c;
            }

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                return removed.contains(signature(method.getMethodType())) ? null : super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = super.visitNewClass(newClass, ctx);
                JavaType.Method constructor = nc.getConstructorType();
                if (constructor != null && removed.contains(signature(constructor))) {
                    nc = nc.withArguments(emptyList())
                            .withConstructorType(constructor.withParameterNames(emptyList()).withParameterTypes(emptyList()));
                }
                return nc;
            }

            @Override
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (("super".equals(method.getSimpleName()) || "this".equals(method.getSimpleName())) &&
                        removed.contains(signature(method.getMethodType()))) {
                    return null;
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }

    private TreeVisitor<?, ExecutionContext> getMigrationVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>("junit.framework.TestCase", false),
                        new UsesType<>("junit.framework.Assert", false)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                        doAfterVisit(new TestCaseVisitor());
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
