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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.DeclaresMethod;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Arrays.asList;

public class MigrateJUnitTestCase extends Recipe {

    private static final AnnotationMatcher OVERRIDE_ANNOTATION_MATCHER = new AnnotationMatcher("@java.lang.Override");
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

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.and(
                        Preconditions.or(
                                new UsesType<>("junit.framework.TestCase", false),
                                new UsesType<>("junit.framework.Assert", false)
                        ),
                        // A TestCase overriding run(TestResult) participates in the JUnit 3 execution model,
                        // which has no Jupiter equivalent, so leave it untouched rather than half-migrate it
                        Preconditions.not(new DeclaresMethod<>(TEST_CASE_RUN_MATCHER))
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                        return sourceFile instanceof J.CompilationUnit && super.isAcceptable(sourceFile, ctx);
                    }

                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                        boolean[] unsupported = {false};
                        J.CompilationUnit checked = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                                if (owner == null || !isSupertypeTestCase(owner.getType()) || !isSuite(method) ||
                                    !containsTestSetup(method, getCursor().getParentTreeCursor()) || testSetupLifecycleMethods(method, owner, getCursor()) != null) {
                                    return super.visitMethodDeclaration(method, executionContext);
                                }
                                unsupported[0] = true;
                                return SearchResult.found(method, "Migrate this TestSetup fixture manually to JUnit Jupiter lifecycle methods");
                            }
                        }.visitNonNull(cu, ctx, getCursor().getParentTreeCursor());
                        if (unsupported[0]) {
                            return checked;
                        }
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

    private static boolean isSuite(J.MethodDeclaration method) {
        return "suite".equals(method.getSimpleName()) && method.hasModifier(J.Modifier.Type.Static) &&
               method.getMethodType() != null &&
               (TypeUtils.isOfClassType(method.getMethodType().getReturnType(), "junit.framework.Test") ||
                TypeUtils.isOfClassType(method.getMethodType().getReturnType(), "junit.framework.TestSuite"));
    }

    private static boolean containsTestSetup(J.MethodDeclaration method, Cursor parent) {
        boolean[] found = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, boolean[] result) {
                if (TypeUtils.isAssignableTo("junit.extensions.TestSetup", newClass.getType())) {
                    result[0] = true;
                }
                return super.visitNewClass(newClass, result);
            }
        }.visitNonNull(method, found, parent);
        return found[0];
    }

    private static @Nullable List<J.MethodDeclaration> testSetupLifecycleMethods(J.MethodDeclaration suite, J.ClassDeclaration owner, Cursor suiteCursor) {
        if (suite.getMethodType() == null || !suite.getMethodType().getParameterTypes().isEmpty() ||
            suite.getTypeParameters() != null && !suite.getTypeParameters().isEmpty() ||
            suite.getBody() == null || suite.getBody().getStatements().size() != 1 ||
            !(suite.getBody().getStatements().get(0) instanceof J.Return)) {
            return null;
        }
        J.Return returned = (J.Return) suite.getBody().getStatements().get(0);
        if (!(returned.getExpression() instanceof J.NewClass)) {
            return null;
        }
        J.NewClass decorator = (J.NewClass) returned.getExpression();
        if (decorator.getClazz() == null || !TypeUtils.isOfClassType(decorator.getClazz().getType(), "junit.extensions.TestSetup") ||
            decorator.getBody() == null || decorator.getArguments().size() != 1 ||
            !(decorator.getArguments().get(0) instanceof J.NewClass)) {
            return null;
        }
        J.NewClass wrapped = (J.NewClass) decorator.getArguments().get(0);
        if (wrapped.getBody() != null || !TypeUtils.isOfClassType(wrapped.getType(), "junit.framework.TestSuite") ||
            wrapped.getArguments().size() != 1 || !(wrapped.getArguments().get(0) instanceof J.FieldAccess)) {
            return null;
        }
        J.FieldAccess testClass = (J.FieldAccess) wrapped.getArguments().get(0);
        if (!"class".equals(testClass.getSimpleName()) || !TypeUtils.isOfType(testClass.getTarget().getType(), owner.getType())) {
            return null;
        }
        List<J.MethodDeclaration> methods = new ArrayList<>();
        Cursor returnCursor = new Cursor(new Cursor(suiteCursor, suite.getBody()), returned);
        Cursor decoratorBodyCursor = new Cursor(new Cursor(returnCursor, decorator), decorator.getBody());
        for (Statement statement : decorator.getBody().getStatements()) {
            if (!(statement instanceof J.MethodDeclaration)) {
                return null;
            }
            J.MethodDeclaration method = (J.MethodDeclaration) statement;
            if (!("setUp".equals(method.getSimpleName()) || "tearDown".equals(method.getSimpleName())) ||
                method.getBody() == null || method.getMethodType() == null ||
                method.hasModifier(J.Modifier.Type.Synchronized) ||
                method.getLeadingAnnotations().stream().anyMatch(annotation -> !OVERRIDE_ANNOTATION_MATCHER.matches(annotation)) ||
                !method.getMethodType().getParameterTypes().isEmpty()) {
                return null;
            }
            boolean[] instanceReference = {false};
            new JavaIsoVisitor<boolean[]>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, boolean[] result) {
                    JavaType.Variable field = identifier.getFieldType();
                    if ("this".equals(identifier.getSimpleName()) || "super".equals(identifier.getSimpleName()) ||
                        field != null && field.getOwner() instanceof JavaType.FullyQualified && !field.hasFlags(Flag.Static) &&
                        TypeUtils.isAssignableTo(((JavaType.FullyQualified) field.getOwner()).getFullyQualifiedName(), decorator.getType())) {
                        result[0] = true;
                    }
                    return super.visitIdentifier(identifier, result);
                }

                @Override
                public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, boolean[] result) {
                    JavaType.Method type = invocation.getMethodType();
                    if (invocation.getSelect() == null && (type == null ||
                        !type.hasFlags(Flag.Static))) {
                        result[0] = true;
                    }
                    return super.visitMethodInvocation(invocation, result);
                }
            }.visitNonNull(method.getBody(), instanceReference, new Cursor(decoratorBodyCursor, method));
            if (instanceReference[0]) {
                return null;
            }
            methods.add(method);
        }
        return methods;
    }

    private static class TestCaseVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher TEST_CASE_SUPER_MATCHER = new MethodMatcher("junit.framework.TestCase <constructor>(..)");
        private static final Set<String> SUPERTYPES_REMOVED_BY_MIGRATION = new HashSet<>(asList(
                "junit.framework.TestCase", "junit.framework.Assert", "junit.framework.Test"));

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (!isSupertypeTestCase(classDecl.getType())) {
                return classDecl;
            }
            Set<String> methodNames = new HashSet<>();
            if (classDecl.getType() != null) {
                classDecl.getType().getVisibleMethods().forEachRemaining(method -> methodNames.add(method.getName()));
            }
            for (Statement statement : classDecl.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    methodNames.add(((J.MethodDeclaration) statement).getSimpleName());
                }
            }
            J.ClassDeclaration withFixtures = classDecl.withBody(classDecl.getBody().withStatements(
                    ListUtils.flatMap(classDecl.getBody().getStatements(), statement -> {
                        if (!(statement instanceof J.MethodDeclaration) || !isSuite((J.MethodDeclaration) statement)) {
                            return statement;
                        }
                        List<J.MethodDeclaration> lifecycleMethods = testSetupLifecycleMethods((J.MethodDeclaration) statement, classDecl,
                                new Cursor(new Cursor(getCursor(), classDecl.getBody()), statement));
                        if (lifecycleMethods == null) {
                            return statement;
                        }
                        maybeRemoveImport("junit.extensions.TestSetup");
                        maybeRemoveImport("junit.framework.Test");
                        maybeRemoveImport("junit.framework.TestSuite");
                        return ListUtils.mapFirst(ListUtils.map(lifecycleMethods, method -> {
                            boolean setup = "setUp".equals(method.getSimpleName());
                            String baseName = setup ? "beforeAll" : "afterAll";
                            String name = baseName;
                            for (int suffix = 1; !methodNames.add(name); suffix++) {
                                name = baseName + suffix;
                            }
                            JavaType.Method methodType = method.getMethodType();
                            if (methodType != null) {
                                methodType = methodType.withName(name).withDeclaringType(classDecl.getType())
                                        .withFlags(EnumSet.of(Flag.Public, Flag.Static));
                            }
                            String annotation = setup ? "BeforeAll" : "AfterAll";
                            Cursor bodyCursor = new Cursor(getCursor(), classDecl.getBody());
                            J.MethodDeclaration lifecycle = JavaTemplate.builder("@" + annotation + " public static void " + name + "() {}")
                                    .contextSensitive()
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                    .imports("org.junit.jupiter.api." + annotation).build()
                                    .apply(new Cursor(bodyCursor, method), method.getCoordinates().replace());
                            lifecycle = lifecycle.withBody(method.getBody()).withThrows(method.getThrows())
                                    .withPrefix(method.getPrefix()).withMethodType(methodType)
                                    .withName(lifecycle.getName().withType(methodType));
                            maybeAddImport("org.junit.jupiter.api." + annotation);
                            return maybeAutoFormat(method, lifecycle, ctx, bodyCursor);
                        }), first -> first.withPrefix(statement.getPrefix()));
                    })));
            J.ClassDeclaration cd = super.visitClassDeclaration(withFixtures, ctx);
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

            // After super.visitMethodDeclaration (which triggers visitMethodInvocation
            // to remove super(testName)), check if this is now an empty constructor
            if (md.isConstructor() &&
                (md.getBody() == null || md.getBody().getStatements().isEmpty())) {
                return null;
            }

            // Remove suite() methods that return junit.framework.Test or TestSuite
            if (isSuite(md)) {
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
            // Plenty of edge cases around classes which extend classes which extend TestCase this doesn't account for
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
