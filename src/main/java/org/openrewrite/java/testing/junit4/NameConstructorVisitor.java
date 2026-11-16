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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

/**
 * The {@code (String name)} constructor of a migrated class becomes a no-argument one, or goes, and its callers in any
 * source file stop passing the name.
 */
class NameConstructorVisitor extends JUnit3MigrationVisitor {

    NameConstructorVisitor(JUnit3MigrationPlan plan) {
        super(plan);
    }

    @Override
    public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        String declaringFqn = fqn(directlyDeclaringClass());
        if (!md.isConstructor() || !plan.isMigrated(declaringFqn)) {
            return md;
        }
        boolean nameConstructor = plan.testCasesByFqn.get(declaringFqn).nameConstructor;
        if (nameConstructor && md.getParameters().stream().anyMatch(J.VariableDeclarations.class::isInstance)) {
            md = md.withParameters(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)));
        }
        if (md.getBody() != null) {
            // an explicit super() adds nothing
            J.Block body = md.getBody();
            List<Statement> statements = ListUtils.map(body.getStatements(), (i, s) ->
                    i == 0 && s instanceof J.MethodInvocation && "super".equals(((J.MethodInvocation) s).getSimpleName()) &&
                    ((J.MethodInvocation) s).getArguments().stream().allMatch(J.Empty.class::isInstance) ? null : s);
            md = md.withBody(body.withStatements(statements));
            // a constructor that throws stays, as a subclass constructor may need to declare what its superclass throws
            if (statements.isEmpty() && md.getThrows() == null && md.getParameters().stream().allMatch(J.Empty.class::isInstance)) {
                return null;
            }
        }
        return md.withModifiers(ListUtils.map(md.getModifiers(), m ->
                m.getType() == J.Modifier.Type.Protected || m.getType() == J.Modifier.Type.Private ? m.withType(J.Modifier.Type.Public) : m));
    }

    @Override
    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        JavaType.Method type = m.getMethodType();
        if (type == null || !"<constructor>".equals(type.getName()) || !inMigratedClass()) {
            return m;
        }
        String declaring = type.getDeclaringType().getFullyQualifiedName();
        if (JUnit3MigrationPlan.TEST_CASE.equals(declaring)) {
            return getCursor().getParentTreeCursor().getValue() instanceof J.Block ? null : m;
        }
        if (plan.dropsNameArgument(declaring) && m.getArguments().size() == 1) {
            return m.withArguments(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)));
        }
        return m;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J.NewClass nc = super.visitNewClass(newClass, ctx);
        JavaType.FullyQualified created = TypeUtils.asFullyQualified(nc.getType());
        if (created != null && plan.dropsNameArgument(created.getFullyQualifiedName()) && nc.getArguments().size() == 1 &&
            !(nc.getArguments().get(0) instanceof J.Empty)) {
            return nc.withArguments(singletonList(new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY)));
        }
        return nc;
    }
}
