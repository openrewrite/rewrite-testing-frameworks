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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.function.Predicate;

/**
 * One step of the migration of {@code TestCase} classes, which decides from the {@link JUnit3MigrationPlan} what to migrate.
 */
abstract class JUnit3MigrationVisitor extends JavaIsoVisitor<ExecutionContext> {

    final JUnit3MigrationPlan plan;

    JUnit3MigrationVisitor(JUnit3MigrationPlan plan) {
        this.plan = plan;
    }

    static JavaTemplate.Builder template(String code) {
        return JavaTemplate.builder(code).javaParser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"));
    }

    static @Nullable String fqn(J.@Nullable ClassDeclaration cd) {
        return cd == null || cd.getType() == null ? null : cd.getType().getFullyQualifiedName();
    }

    /**
     * Whether a class at or around the cursor, from the innermost outwards, matches.
     */
    static boolean inClass(Cursor cursor, Predicate<@Nullable String> fqnMatches) {
        for (Cursor c = cursor; c != null; c = c.getParent()) {
            if (c.getValue() instanceof J.ClassDeclaration && fqnMatches.test(fqn(c.getValue()))) {
                return true;
            }
        }
        return false;
    }

    boolean inMigratedClass() {
        return inClass(getCursor(), plan::isMigrated);
    }

    J.@Nullable ClassDeclaration directlyDeclaringClass() {
        Cursor parent = getCursor().getParentTreeCursor();
        if (parent.getValue() instanceof J.Block) {
            Cursor grandParent = parent.getParentTreeCursor();
            if (grandParent.getValue() instanceof J.ClassDeclaration) {
                return grandParent.getValue();
            }
        }
        return null;
    }

    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        J.Block b = super.visitBlock(block, ctx);
        // a first member that is removed leaves its successor with a prefix that is meant for a later member
        if (!block.getStatements().isEmpty() && !b.getStatements().isEmpty() &&
            b.getStatements().get(0).getId() != block.getStatements().get(0).getId()) {
            Space original = block.getStatements().get(0).getPrefix();
            b = b.withStatements(ListUtils.mapFirst(b.getStatements(), st -> st.withPrefix(original)));
        }
        return b;
    }
}
