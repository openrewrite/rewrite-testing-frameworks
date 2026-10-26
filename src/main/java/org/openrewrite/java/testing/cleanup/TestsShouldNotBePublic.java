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
package org.openrewrite.java.testing.cleanup;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeMethodAccessLevelVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class TestsShouldNotBePublic extends ScanningRecipe<TestsShouldNotBePublic.Accumulator> {

    @Option(displayName = "Remove protected modifiers",
            description = "Also remove protected modifiers from test methods",
            example = "true",
            required = false)
    @Nullable
    private Boolean removeProtectedModifiers;

    @Getter
    final String displayName = "Remove `public` visibility of JUnit 5 tests";

    @Getter
    final String description = "Remove `public` and optionally `protected` modifiers from methods with `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@BeforeEach`, `@AfterEach`, `@BeforeAll`, or `@AfterAll`. They no longer have to be public visibility to be usable by JUnit 5.";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5786");

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);
                if (cd.getExtends() != null) {
                    addExtendedClasses(cd.getExtends().getType());
                }
                return cd;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (n.getBody() != null && n.getClazz() != null) {
                    addExtendedClasses(n.getClazz().getType());
                }
                return n;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                if (!i.isStatic() && "*".equals(i.getQualid().getSimpleName())) {
                    return i;
                }
                JavaSourceFile cu = getCursor().firstEnclosing(JavaSourceFile.class);
                if (cu != null) {
                    String packageName = cu.getPackageDeclaration() == null ? "" :
                            cu.getPackageDeclaration().getPackageName();
                    if (!packageName.equals(i.getPackageName())) {
                        String name = i.getTypeName().replace('$', '.');
                        while (name.contains(".")) {
                            acc.referencedFromOtherPackages.add(name);
                            name = name.substring(0, name.lastIndexOf('.'));
                        }
                    }
                }
                return i;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                JavaType.FullyQualified referenced = TypeUtils.asFullyQualified(id.getType());
                JavaSourceFile cu = getCursor().firstEnclosing(JavaSourceFile.class);
                if (referenced != null && cu != null) {
                    String packageName = cu.getPackageDeclaration() == null ? "" :
                            cu.getPackageDeclaration().getPackageName();
                    if (!packageName.equals(referenced.getPackageName())) {
                        Set<String> seen = new HashSet<>();
                        while (referenced != null && seen.add(referenced.getFullyQualifiedName())) {
                            acc.referencedFromOtherPackages.add(referenced.getFullyQualifiedName().replace('$', '.'));
                            referenced = referenced.getOwningClass();
                        }
                    }
                }
                return id;
            }

            private void addExtendedClasses(@Nullable JavaType type) {
                JavaType.FullyQualified superclass = TypeUtils.asFullyQualified(type);
                if (superclass == null) {
                    acc.extendedClasses.add(className(type));
                    return;
                }
                Set<String> seen = new HashSet<>();
                while (superclass != null && seen.add(superclass.getFullyQualifiedName())) {
                    acc.extendedClasses.add(superclass.getFullyQualifiedName());
                    superclass = superclass.getSupertype();
                }
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TestsNotPublicVisitor(Boolean.TRUE.equals(removeProtectedModifiers), acc);
    }

    public static class Accumulator {
        Set<String> extendedClasses = new HashSet<>();
        Set<String> referencedFromOtherPackages = new HashSet<>();
    }

    private static String className(@Nullable JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? String.valueOf(type) : fq.getFullyQualifiedName();
    }

    @RequiredArgsConstructor
    private static final class TestsNotPublicVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final Boolean orProtected;
        private final Accumulator acc;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

            if (c.getKind() != J.ClassDeclaration.Kind.Type.Interface &&
                    c.hasModifier(J.Modifier.Type.Public) &&
                    c.getType() != null &&
                    !c.hasModifier(J.Modifier.Type.Abstract) &&
                    !acc.extendedClasses.contains(className(c.getType())) &&
                    !acc.referencedFromOtherPackages.contains(className(c.getType()).replace('$', '.'))) {
                boolean hasTestMethods = c.getBody().getStatements().stream()
                        .filter(org.openrewrite.java.tree.J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .anyMatch(this::hasJUnit5MethodAnnotation);

                boolean hasPublicNonTestMethods = c.getBody().getStatements().stream()
                        .filter(org.openrewrite.java.tree.J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(m -> m.hasModifier(J.Modifier.Type.Public))
                        .anyMatch(method -> !hasJUnit5MethodAnnotation(method));

                boolean hasPublicVariableDeclarations = c.getBody().getStatements().stream()
                        .filter(org.openrewrite.java.tree.J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .anyMatch(m -> m.hasModifier(J.Modifier.Type.Public));

                if (hasTestMethods && !hasPublicNonTestMethods && !hasPublicVariableDeclarations) {
                    // Remove public modifier and move associated comment
                    final List<Comment> modifierComments = new ArrayList<>();
                    List<J.Modifier> modifiers = ListUtils.map(c.getModifiers(), mod -> {
                        if (mod.getType() == J.Modifier.Type.Public) {
                            modifierComments.addAll(mod.getComments());
                            return null;
                        }

                        // copy access level modifier comment to next modifier if it exists
                        if (!modifierComments.isEmpty()) {
                            J.Modifier nextModifier = mod.withComments(ListUtils.concatAll(new ArrayList<>(modifierComments), mod.getComments()));
                            modifierComments.clear();
                            return nextModifier;
                        }
                        return mod;
                    });
                    // if no following modifier exists, add comments to method itself
                    if (!modifierComments.isEmpty()) {
                        c = c.withComments(ListUtils.concatAll(c.getComments(), modifierComments));
                    }
                    c = maybeAutoFormat(c, c.withModifiers(modifiers), c.getName(), ctx, getCursor().getParentTreeCursor());
                }
            }
            return c;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

            if (method.getMethodType() == null ||
                    method.getMethodType().getDeclaringType().hasFlags(Flag.Abstract, Flag.Public) ||
                    acc.extendedClasses.contains(className(method.getMethodType().getDeclaringType()))) {
                return m;
            }

            if (m.hasModifier(J.Modifier.Type.Abstract) || TypeUtils.isOverride(method.getMethodType())) {
                return m;
            }

            if ((m.hasModifier(J.Modifier.Type.Public) || (orProtected && m.hasModifier(J.Modifier.Type.Protected))) &&
                hasJUnit5MethodAnnotation(m)) {
                // remove public modifier
                doAfterVisit(new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(method), null));
            }

            return m;
        }

        private static final List<AnnotationMatcher> JUNIT5_METHOD_ANNOTATIONS = Arrays.asList(
                "org.junit.jupiter.api.Test",
                "org.junit.jupiter.api.RepeatedTest",
                "org.junit.jupiter.params.ParameterizedTest",
                "org.junit.jupiter.api.TestFactory",
                "org.junit.jupiter.api.TestTemplate",
                "org.junit.jupiter.api.AfterEach",
                "org.junit.jupiter.api.BeforeEach",
                "org.junit.jupiter.api.AfterAll",
                "org.junit.jupiter.api.BeforeAll").stream()
                .map(fqn -> new AnnotationMatcher("@" + fqn, true))
                .collect(toList());

        private boolean hasJUnit5MethodAnnotation(J.MethodDeclaration method) {
            for (J.Annotation a : method.getLeadingAnnotations()) {
                for (AnnotationMatcher matcher : JUNIT5_METHOD_ANNOTATIONS) {
                    if (matcher.matches(a)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
