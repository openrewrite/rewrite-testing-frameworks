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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NormalizeKotlinJUnit4Aliases extends Recipe {
    @Getter
    final String displayName = "Normalize Kotlin JUnit 4 function aliases";

    @Getter
    final String description = "Use canonical JUnit assertion and assumption names before migrating Kotlin static imports.";

    private static boolean junit4(JavaType type) {
        return TypeUtils.isOfClassType(type, "org.junit.Assert") || TypeUtils.isOfClassType(type, "org.junit.Assume");
    }

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

        private static final class Visitor extends KotlinIsoVisitor<ExecutionContext> {
            final Map<String, String> aliases = new HashMap<>();
            final Set<String> otherImports = new HashSet<>();
            boolean otherWildcard;

            @Override
            public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, ExecutionContext ctx) {
                aliases.clear();
                otherImports.clear();
                otherWildcard = false;
                for (J.Import anImport : cu.getImports()) {
                    if (anImport.isStatic() && anImport.getAlias() != null && junit4(anImport.getQualid().getTarget().getType())) {
                        aliases.put(anImport.getAlias().getSimpleName(), anImport.getQualid().getSimpleName());
                    } else if (!legacy(anImport.getTypeName())) {
                        String name = anImport.getAlias() == null ? anImport.getQualid().getSimpleName() :
                                anImport.getAlias().getSimpleName();
                        otherImports.add(name);
                        otherWildcard |= "*".equals(name);
                    }
                }
                K.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                return c.withImports(ListUtils.map(c.getImports(), anImport ->
                        anImport.isStatic() && junit4(anImport.getQualid().getTarget().getType()) ?
                                collides(anImport.getQualid().getSimpleName()) ? null : anImport.withAlias(null) : anImport));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(invocation, ctx);
                if (m.getMethodType() != null && junit4(m.getMethodType().getDeclaringType())) {
                    JavaType.Method type = m.getMethodType().withName(aliases.getOrDefault(m.getSimpleName(), m.getMethodType().getName()));
                    m = m.withMethodType(type).withName(m.getName().withSimpleName(type.getName()).withType(type));
                    return collides(type.getName()) ? m.withSelect(TypeTree.build(type.getDeclaringType().getFullyQualifiedName())
                            .withType(type.getDeclaringType())) : m;
                }
                return m;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference reference, ExecutionContext ctx) {
                J.MemberReference r = super.visitMemberReference(reference, ctx);
                if (r.getMethodType() != null && junit4(r.getMethodType().getDeclaringType())) {
                    JavaType.Method type = r.getMethodType().withName(aliases.getOrDefault(r.getReference().getSimpleName(), r.getMethodType().getName()));
                    r = r.withMethodType(type).withReference(r.getReference().withSimpleName(type.getName()).withType(type));
                    return collides(type.getName()) ? r.withContaining(TypeTree.build(type.getDeclaringType().getFullyQualifiedName())
                            .withType(type.getDeclaringType())) : r;
                }
                return r;
            }

            private boolean collides(String name) {
                return otherWildcard || otherImports.contains(name);
            }
        }
    }

    private static boolean legacy(String name) {
        return name.startsWith("junit.framework.") || name.startsWith("junitparams.") ||
                name.startsWith("org.junit.") && !name.startsWith("org.junit.jupiter.") &&
                        !name.startsWith("org.junit.platform.") && !name.startsWith("org.junitpioneer.");
    }
}
