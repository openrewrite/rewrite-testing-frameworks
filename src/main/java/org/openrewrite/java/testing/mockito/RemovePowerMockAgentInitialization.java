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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

public class RemovePowerMockAgentInitialization extends Recipe {

    private static final String POWER_MOCK_AGENT = "org.powermock.modules.agent.PowerMockAgent";

    private static final MethodMatcher INITIALIZE = new MethodMatcher("org.powermock.modules.agent.PowerMockAgent initializeIfNeeded()");

    @Getter
    final String displayName = "Remove `PowerMockAgent.initializeIfNeeded()`";

    @Getter
    final String description = "Removes calls to `PowerMockAgent.initializeIfNeeded()`, which only attaches the PowerMock " +
            "Java agent that Mockito's inline mock maker has no use for, and the static initializer blocks that are " +
            "left empty by it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(INITIALIZE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = super.visitBlock(block, ctx);
                boolean classBody = getCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration;
                return b.withStatements(ListUtils.map(b.getStatements(), statement -> {
                    if (statement instanceof J.MethodInvocation && INITIALIZE.matches((J.MethodInvocation) statement)) {
                        maybeRemoveImport(POWER_MOCK_AGENT);
                        return null;
                    }
                    // A static initializer that held nothing else is not worth keeping
                    if (classBody && statement instanceof J.Block && ((J.Block) statement).isStatic() &&
                        ((J.Block) statement).getStatements().isEmpty() && isEmptiedByThisRecipe((J.Block) statement, block)) {
                        return null;
                    }
                    return statement;
                }));
            }

            /// Only the static initializers that had a call removed from them, not ones that were empty already.
            private boolean isEmptiedByThisRecipe(J.Block emptied, J.Block originalClassBody) {
                for (Statement original : originalClassBody.getStatements()) {
                    if (original.getId().equals(emptied.getId())) {
                        return !((J.Block) original).getStatements().isEmpty();
                    }
                }
                return false;
            }
        });
    }
}
