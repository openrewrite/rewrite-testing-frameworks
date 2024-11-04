/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.jmockit;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.openrewrite.java.testing.jmockit.JMockitBlockType.getSupportedTypesStr;
import static org.openrewrite.java.testing.jmockit.JMockitBlockType.values;

@Value
@EqualsAndHashCode(callSuper = false)
public class JMockitBlockToMockito extends Recipe {

    private static final String SUPPORTED_TYPES = getSupportedTypesStr();

    @Override
    public String getDisplayName() {
        return "Rewrite JMockit " + SUPPORTED_TYPES;
    }

    @Override
    public String getDescription() {
        return "Rewrites JMockit `" + SUPPORTED_TYPES + "` blocks to Mockito statements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        @SuppressWarnings("rawtypes")
        UsesType[] usesTypes = Arrays.stream(values()).map(blockType -> new UsesType<>(blockType.getFqn(), false)).toArray(UsesType[]::new);
        return Preconditions.check(Preconditions.or(usesTypes), new RewriteJMockitBlockVisitor());
    }

    private static class RewriteJMockitBlockVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDeclaration, ctx);
            if (md.getBody() == null) {
                return md;
            }
            // rewrite the statements that are not mock expectations or verifications
            SetupStatementsRewriter ssr = new SetupStatementsRewriter(this, md.getBody());
            J.Block methodBody = ssr.rewriteMethodBody();
            List<Statement> statements = methodBody.getStatements();

            int bodyStatementIndex = 0;
            // iterate over each statement in the method body, find Expectations blocks and rewrite them
            while (bodyStatementIndex < statements.size()) {
                Statement s = statements.get(bodyStatementIndex);
                Optional<JMockitBlockType> blockType = JMockitUtils.getJMockitBlock(s);
                if (blockType.isPresent()) {
                    JMockitBlockRewriter blockRewriter = new JMockitBlockRewriter(this, ctx, methodBody,
                            ((J.NewClass) s), bodyStatementIndex, blockType.get());
                    methodBody = blockRewriter.rewriteMethodBody();
                    statements = methodBody.getStatements();
                    // if the expectations rewrite failed, skip the next statement
                    if (blockRewriter.isRewriteFailed()) {
                        bodyStatementIndex++;
                    }
                } else {
                    bodyStatementIndex++;
                }
            }
            return md.withBody(methodBody);
        }
    }
}
