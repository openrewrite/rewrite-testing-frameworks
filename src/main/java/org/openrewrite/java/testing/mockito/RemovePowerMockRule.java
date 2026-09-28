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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class RemovePowerMockRule extends Recipe {

    private static final String POWER_MOCK_RULE = "org.powermock.modules.junit4.rule.PowerMockRule";

    @Getter
    final String displayName = "Remove `PowerMockRule` fields";

    @Getter
    final String description = "Removes JUnit 4 `@Rule PowerMockRule` fields, which bootstrap PowerMock like the " +
            "`PowerMockRunner` does, and are not needed with Mockito.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(POWER_MOCK_RULE, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                if (!(getCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration)) {
                    return b;
                }
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    if (statement instanceof J.VariableDeclarations &&
                        TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(), POWER_MOCK_RULE)) {
                        maybeRemoveImport(POWER_MOCK_RULE);
                        maybeRemoveImport("org.junit.Rule");
                        return null;
                    }
                    return statement;
                }));
            }
        });
    }
}
