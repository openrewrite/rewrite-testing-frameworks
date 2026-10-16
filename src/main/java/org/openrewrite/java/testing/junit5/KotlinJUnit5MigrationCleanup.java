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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.kotlin.KotlinVisitor;
import org.openrewrite.kotlin.format.ImportReorderingVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class KotlinJUnit5MigrationCleanup extends Recipe {
    @Getter
    final String displayName = "Resolve Kotlin imports after JUnit Jupiter migration";

    @Getter
    final String description = "Disambiguate migrated Jupiter annotations and assertions in Kotlin source files.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        if (!KotlinJUnit5Support.available()) {
            return TreeVisitor.noop();
        }
        return KotlinVisitors.create();
    }

    private static final class KotlinVisitors {
        private static TreeVisitor<?, ExecutionContext> create() {
            return new Visitor();
        }

        private static final class Visitor extends KotlinVisitor<ExecutionContext> {
            final Set<String> declaredNames = new HashSet<>();
            final Set<String> declaredMethods = new HashSet<>();
            final Set<String> qualifiedAnnotations = new HashSet<>();
            final Set<String> qualifiedMethods = new HashSet<>();
            final Map<String, JavaType.FullyQualified> annotationImports = new LinkedHashMap<>();
            final Map<String, J.Identifier> methodImports = new LinkedHashMap<>();

            @Override
            public J visitCompilationUnit(K.CompilationUnit cu, ExecutionContext ctx) {
                // Import collisions and aliased calls in the composite are reproduced in
                // https://github.com/openrewrite/rewrite-testing-frameworks/issues/1188.
                // Remove this repair when Kotlin's import visitors preserve those bindings across recipe composition.
                declaredNames.clear();
                declaredMethods.clear();
                qualifiedAnnotations.clear();
                qualifiedMethods.clear();
                annotationImports.clear();
                methodImports.clear();
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration clazz, ExecutionContext ctx) {
                        declaredNames.add(clazz.getSimpleName());
                        return super.visitClassDeclaration(clazz, ctx);
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        declaredMethods.add(method.getSimpleName());
                        return super.visitMethodDeclaration(method, ctx);
                    }
                }.visit(cu, ctx);
                K.CompilationUnit c = (K.CompilationUnit) super.visitCompilationUnit(cu, ctx);
                c = c.withImports(ListUtils.map(c.getImports(), anImport ->
                        anImport.getAlias() == null && qualifiedAnnotations.contains(anImport.getTypeName()) ? null : anImport));
                c = c.withImports(ListUtils.map(c.getImports(), anImport -> {
                    String name = anImport.getQualid().printTrimmed(getCursor());
                    return anImport.isStatic() && qualifiedMethods.contains(name) &&
                            methodImports.keySet().stream().noneMatch(key -> key.startsWith(name + ":")) ? null : anImport;
                }));
                Set<String> wildcardImports = new HashSet<>();
                for (J.Import anImport : c.getImports()) {
                    if (anImport.isStatic() && "*".equals(anImport.getQualid().getSimpleName())) {
                        wildcardImports.add(anImport.getQualid().getTarget().printTrimmed(getCursor()));
                    }
                }
                c = c.withImports(ListUtils.map(c.getImports(), anImport -> anImport.isStatic() && anImport.getAlias() == null &&
                        !"*".equals(anImport.getQualid().getSimpleName()) &&
                        wildcardImports.contains(anImport.getQualid().getTarget().printTrimmed(getCursor())) ? null : anImport));
                Set<String> retainedImports = new HashSet<>();
                c = c.withImports(ListUtils.map(c.getImports(), anImport -> {
                    String key = anImport.getQualid().printTrimmed(getCursor()) + ":" + anImport.getAlias();
                    return retainedImports.add(key) || !anImport.getComments().isEmpty() ? anImport : null;
                }));
                for (Map.Entry<String, J.Identifier> entry : methodImports.entrySet()) {
                    String name = entry.getKey().substring(0, entry.getKey().indexOf(':'));
                    String owner = name.substring(0, name.lastIndexOf('.'));
                    J.Identifier identifier = entry.getValue();
                    String method = name.substring(name.lastIndexOf('.') + 1);
                    if (!(wildcardImports.contains(owner) && identifier.getSimpleName().equals(method)) &&
                            c.getImports().stream().noneMatch(anImport ->
                                    name.equals(anImport.getQualid().printTrimmed(getCursor())) &&
                                            identifier.getSimpleName().equals(anImport.getAlias() == null ?
                                                    anImport.getQualid().getSimpleName() : anImport.getAlias().getSimpleName()))) {
                        JLeftPadded<J.Identifier> alias = identifier.getSimpleName().equals(method) ? null :
                                new JLeftPadded<>(Space.SINGLE_SPACE, identifier.withPrefix(Space.SINGLE_SPACE), Markers.EMPTY);
                        c = c.withImports(ListUtils.concat(c.getImports(), new J.Import(randomId(), Space.format("\n"),
                                Markers.EMPTY, new JLeftPadded<>(Space.EMPTY, true, Markers.EMPTY),
                                TypeTree.build(name).withPrefix(Space.SINGLE_SPACE), alias)));
                    }
                }
                for (Map.Entry<String, JavaType.FullyQualified> entry : annotationImports.entrySet()) {
                    String name = entry.getKey();
                    String packageName = name.substring(0, name.lastIndexOf('.'));
                    if (c.getImports().stream().noneMatch(anImport -> !anImport.isStatic() &&
                            (name.equals(anImport.getTypeName()) || packageName.equals(anImport.getPackageName()) &&
                                    "*".equals(anImport.getQualid().getSimpleName())))) {
                        c = c.withImports(ListUtils.concat(c.getImports(), new J.Import(randomId(), Space.format("\n"),
                                Markers.EMPTY, new JLeftPadded<>(Space.EMPTY, false, Markers.EMPTY),
                                TypeTree.build(name).withPrefix(Space.format(" ")), null)));
                    }
                }
                return c == cu ? cu : new ImportReorderingVisitor<ExecutionContext>()
                        .visitNonNull(c, ctx, getCursor().getParentTreeCursor());
            }

            @Override
            public J visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = (J.Annotation) super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                if (type != null && type.getFullyQualifiedName().startsWith("org.junit.jupiter.") &&
                        a.getAnnotationType() instanceof J.Identifier &&
                        declaredNames.contains(((J.Identifier) a.getAnnotationType()).getSimpleName())) {
                    a = a.withAnnotationType(TypeTree.build(type.getFullyQualifiedName().replace('$', '.')).withType(type));
                    qualifiedAnnotations.add(type.getFullyQualifiedName());
                    maybeRemoveImport(type.getFullyQualifiedName());
                } else if (type != null && type.getFullyQualifiedName().startsWith("org.junit.jupiter.") &&
                        a.getAnnotationType() instanceof J.Identifier) {
                    annotationImports.put(type.getFullyQualifiedName(), type);
                }
                return a;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(invocation, ctx);
                if (m.getSelect() == null && m.getMethodType() != null &&
                        TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.jupiter.api.AssertionsKt")) {
                    methodImports.put("org.junit.jupiter.api." + m.getMethodType().getName() + ":" +
                            m.getSimpleName(), m.getName());
                }
                if (m.getSelect() != null && m.getMethodType() != null &&
                        (TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assertions") ||
                                TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assumptions"))) {
                    qualifiedMethods.add(m.getMethodType().getDeclaringType().getFullyQualifiedName() + "." +
                            m.getMethodType().getName());
                }
                if (m.getSelect() == null && m.getMethodType() != null &&
                        (TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assertions") ||
                                TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assumptions"))) {
                    methodImports.put(m.getMethodType().getDeclaringType().getFullyQualifiedName() + "." +
                            m.getMethodType().getName() + ":" + m.getSimpleName(), m.getName());
                }
                if (m.getSelect() == null && m.getMethodType() != null &&
                        declaredMethods.contains(m.getMethodType().getName()) &&
                        (TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assertions") ||
                                TypeUtils.isOfClassType(m.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assumptions"))) {
                    JavaType.FullyQualified type = m.getMethodType().getDeclaringType();
                    qualifiedMethods.add(type.getFullyQualifiedName() + "." + m.getMethodType().getName());
                    methodImports.remove(type.getFullyQualifiedName() + "." + m.getMethodType().getName() + ":" + m.getSimpleName());
                    Expression select = declaredNames.contains(type.getClassName()) ?
                            (Expression) TypeTree.build(type.getFullyQualifiedName()).withType(type) :
                            new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), type.getClassName(), type, null);
                    m = m.withSelect(select).withName(m.getName().withSimpleName(m.getMethodType().getName()));
                    if (select instanceof J.Identifier) {
                        maybeAddImport(type.getFullyQualifiedName());
                    }
                    maybeRemoveImport(type.getFullyQualifiedName() + "." + m.getSimpleName());
                    maybeRemoveImport(type.getFullyQualifiedName() + ".*");
                }
                return m;
            }

            @Override
            public J visitMemberReference(J.MemberReference reference, ExecutionContext ctx) {
                J.MemberReference r = (J.MemberReference) super.visitMemberReference(reference, ctx);
                if (r.getContaining() instanceof J.Empty && r.getMethodType() != null &&
                        (TypeUtils.isOfClassType(r.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assertions") ||
                                TypeUtils.isOfClassType(r.getMethodType().getDeclaringType(), "org.junit.jupiter.api.Assumptions"))) {
                    methodImports.put(r.getMethodType().getDeclaringType().getFullyQualifiedName() + "." +
                            r.getMethodType().getName() + ":" + r.getReference().getSimpleName(), r.getReference());
                }
                return r;
            }
        }
    }
}
