/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.testing.junit4;

import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

/**
 * The methods of a migrated class get the annotations and modifiers that JUnit 4 runs them by: {@code test*} methods
 * get {@code @Test}, {@code setUp} and {@code tearDown} get {@code @Before} and {@code @After}, and annotations that
 * JUnit 3 ignored but JUnit 4 would reject go.
 */
class LifecycleMethodsVisitor extends JUnit3MigrationVisitor {

    private static final AnnotationMatcher JUNIT4_TEST = new AnnotationMatcher("@org.junit.Test");
    private static final AnnotationMatcher JUNIT4_BEFORE = new AnnotationMatcher("@org.junit.Before");
    private static final AnnotationMatcher JUNIT4_AFTER = new AnnotationMatcher("@org.junit.After");
    private static final AnnotationMatcher OVERRIDE = new AnnotationMatcher("@java.lang.Override");
    private static final Set<String> SUPERTYPES_REMOVED_BY_MIGRATION = new HashSet<>(Arrays.asList(
            "junit.framework.TestCase", "junit.framework.Assert", "junit.framework.Test"));

    private final Set<String> typeNamesInFile = new HashSet<>();
    private final Set<String> methodsCalledInFile = new HashSet<>();

    LifecycleMethodsVisitor(JUnit3MigrationPlan plan) {
        super(plan);
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        typeNamesInFile.clear();
        methodsCalledInFile.clear();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, Integer p) {
                typeNamesInFile.add(cd.getSimpleName());
                return super.visitClassDeclaration(cd, p);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                methodsCalledInFile.add(m.getSimpleName());
                return super.visitMethodInvocation(m, p);
            }
        }.visit(cu, 0);
        return super.visitCompilationUnit(cu, ctx);
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        if (md.isConstructor() || md.getMethodType() == null || !plan.isMigrated(fqn(directlyDeclaringClass()))) {
            return md;
        }
        updateCursor(md);
        if (isNoArgVoid(md) && md.hasModifier(J.Modifier.Type.Static) && md.hasModifier(J.Modifier.Type.Public) &&
            md.getSimpleName().startsWith("test") && !methodsCalledInFile.contains(md.getSimpleName())) {
            // JUnit 3 ran static test methods, whereas JUnit 4 rejects them
            md = md.withModifiers(ListUtils.map(md.getModifiers(), m -> m.getType() == J.Modifier.Type.Static ? null : m));
        }
        if (isNoArgVoid(md) && !md.hasModifier(J.Modifier.Type.Static) && !md.hasModifier(J.Modifier.Type.Abstract)) {
            if (md.getSimpleName().startsWith("test") && md.hasModifier(J.Modifier.Type.Public) &&
                md.getLeadingAnnotations().stream().noneMatch(JUNIT4_TEST::matches)) {
                md = addAnnotation(md, "@Test", "org.junit.Test");
            } else if ("setUp".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT4_BEFORE::matches)) {
                md = makePublic(addAnnotation(md, "@Before", "org.junit.Before"));
            } else if ("tearDown".equals(md.getSimpleName()) && md.getLeadingAnnotations().stream().noneMatch(JUNIT4_AFTER::matches)) {
                md = makePublic(addAnnotation(md, "@After", "org.junit.After"));
            }
        }
        // JUnit 3 ignored @Test on a helper that is no test method, which JUnit 4 rejects
        if (md.getLeadingAnnotations().stream().anyMatch(JUNIT4_TEST::matches) &&
            (!isNoArgVoid(md) || md.hasModifier(J.Modifier.Type.Static) && !md.getSimpleName().startsWith("test"))) {
            md = (J.MethodDeclaration) new RemoveAnnotationVisitor(JUNIT4_TEST).visitNonNull(md, ctx, getCursor().getParentOrThrow());
        }
        // JUnit 3 ignored @BeforeClass on a method that ran for every test, and JUnit 4 demands that it is static
        if (!md.hasModifier(J.Modifier.Type.Static)) {
            for (String once : Arrays.asList("org.junit.BeforeClass", "org.junit.AfterClass")) {
                if (md.getLeadingAnnotations().stream().anyMatch(a -> new AnnotationMatcher("@" + once).matches(a))) {
                    md = (J.MethodDeclaration) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + once)).visitNonNull(md, ctx, getCursor().getParentOrThrow());
                    maybeRemoveImport(once);
                }
            }
        }
        // JUnit 3 ignored these annotations, JUnit 4 insists on public methods
        if (!md.hasModifier(J.Modifier.Type.Public) && md.getLeadingAnnotations().stream()
                .anyMatch(a -> JUNIT4_TEST.matches(a) || JUNIT4_BEFORE.matches(a) || JUNIT4_AFTER.matches(a))) {
            md = makeAccessPublic(md);
        }
        return removeObsoleteOverride(md, ctx);
    }

    private static boolean isNoArgVoid(J.MethodDeclaration md) {
        return md.getParameters().stream().allMatch(J.Empty.class::isInstance) &&
               md.getReturnTypeExpression() != null && md.getReturnTypeExpression().getType() == JavaType.Primitive.Void;
    }

    /**
     * Built by hand, as a template would resolve {@code @Test} to {@code junit.framework.Test} while that is imported.
     */
    private J.MethodDeclaration addAnnotation(J.MethodDeclaration md, String annotation, String fqn) {
        // a type of the file named like the annotation would be what the annotation refers to
        boolean qualify = typeNamesInFile.contains(annotation.substring(1));
        if (!qualify) {
            maybeAddImport(fqn);
        }
        Space newLine = Space.format("\n" + md.getPrefix().getIndent());
        NameTree annotationType = qualify ?
                (NameTree) TypeTree.build(fqn).withType(JavaType.ShallowClass.build(fqn)) :
                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), annotation.substring(1),
                        JavaType.ShallowClass.build(fqn), null);
        J.Annotation added = new J.Annotation(randomId(), md.getLeadingAnnotations().isEmpty() ? Space.EMPTY : newLine, Markers.EMPTY,
                annotationType, null);
        boolean first = md.getLeadingAnnotations().isEmpty();
        J.MethodDeclaration m = md.withLeadingAnnotations(ListUtils.concat(md.getLeadingAnnotations(), added));
        if (first) {
            if (!m.getModifiers().isEmpty()) {
                m = m.withModifiers(ListUtils.mapFirst(m.getModifiers(), mod -> mod.withPrefix(newLine)));
            } else if (m.getReturnTypeExpression() != null) {
                m = m.withReturnTypeExpression(m.getReturnTypeExpression().withPrefix(newLine));
            }
        }
        return m;
    }

    private static J.MethodDeclaration makePublic(J.MethodDeclaration md) {
        return md.withModifiers(ListUtils.map(md.getModifiers(), m -> m.getType() == J.Modifier.Type.Protected ? m.withType(J.Modifier.Type.Public) : m));
    }

    private static J.MethodDeclaration makeAccessPublic(J.MethodDeclaration md) {
        if (md.hasModifier(J.Modifier.Type.Protected) || md.hasModifier(J.Modifier.Type.Private)) {
            return md.withModifiers(ListUtils.map(md.getModifiers(), m ->
                    m.getType() == J.Modifier.Type.Protected || m.getType() == J.Modifier.Type.Private ? m.withType(J.Modifier.Type.Public) : m));
        }
        J.Modifier pub = new J.Modifier(randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Public, emptyList());
        if (!md.getModifiers().isEmpty()) {
            return md.withModifiers(ListUtils.concat(pub.withPrefix(md.getModifiers().get(0).getPrefix()),
                    ListUtils.mapFirst(md.getModifiers(), m -> m.withPrefix(Space.SINGLE_SPACE))));
        }
        if (md.getReturnTypeExpression() != null) {
            return md.withModifiers(singletonList(pub.withPrefix(md.getReturnTypeExpression().getPrefix())))
                    .withReturnTypeExpression(md.getReturnTypeExpression().withPrefix(Space.SINGLE_SPACE));
        }
        return md;
    }

    private J.MethodDeclaration removeObsoleteOverride(J.MethodDeclaration md, ExecutionContext ctx) {
        JavaType.Method type = md.getMethodType();
        if (type == null || md.getLeadingAnnotations().stream().noneMatch(OVERRIDE::matches) || stillOverrides(type)) {
            return md;
        }
        J.MethodDeclaration withoutBody = (J.MethodDeclaration) new RemoveAnnotationVisitor(OVERRIDE)
                .visitNonNull(md.withBody(null), ctx, getCursor().getParentOrThrow());
        return withoutBody.withBody(md.getBody());
    }

    private static boolean stillOverrides(JavaType.Method method) {
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
