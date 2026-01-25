/*
 * Copyright 2025 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.Tree.randomId;

/**
 * Wraps `MockedConstruction` variable declarations with explicit `.close()` calls
 * into try-with-resources blocks, removing the explicit close call.
 */
public class MockConstructionToTryWithResources extends Recipe {

    private static final MethodMatcher MOCK_CONSTRUCTION_MATCHER = new MethodMatcher("org.mockito.Mockito mockConstruction(..)");
    private static final MethodMatcher MOCKED_CONSTRUCTION_CLOSE_MATCHER = new MethodMatcher("org.mockito.ScopedMock close()");

    @Getter
    final String displayName = "Wrap `MockedConstruction` in try-with-resources";

    @Getter
    final String description = "Wraps `MockedConstruction` variable declarations that have explicit `.close()` calls " +
            "into try-with-resources blocks, removing the explicit close call. " +
            "This ensures proper resource management and makes the code cleaner.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitTry(J.Try tryStatement, ExecutionContext ctx) {
                if (tryStatement.getResources() != null && !tryStatement.getResources().isEmpty()) {
                    // Track variable names from try-with-resources BEFORE visiting children
                    Set<String> tryWithResourceVars = tryStatement.getResources().stream()
                            .filter(r -> r.getVariableDeclarations() instanceof J.VariableDeclarations)
                            .map(r -> (J.VariableDeclarations) r.getVariableDeclarations())
                            .flatMap(vd -> vd.getVariables().stream())
                            .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                            .collect(toSet());
                    getCursor().putMessage("tryWithResourceVars", tryWithResourceVars);
                }
                return super.visitTry(tryStatement, ctx);
            }

            @Override
            public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (mi == null) {
                    return null;
                }
                // Remove redundant .close() calls inside try-with-resources
                if (MOCKED_CONSTRUCTION_CLOSE_MATCHER.matches(mi)) {
                    if (mi.getSelect() instanceof J.Identifier) {
                        String varName = ((J.Identifier) mi.getSelect()).getSimpleName();
                        Set<String> tryWithResourceVars = getCursor().getNearestMessage("tryWithResourceVars");
                        if (tryWithResourceVars != null && tryWithResourceVars.contains(varName)) {
                            return null;
                        }
                    }
                }
                return mi;
            }

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                // First, check if there's a MockedConstruction variable with a close() call
                List<Statement> statements = block.getStatements();

                int varDeclIndex = -1;
                int closeIndex = -1;
                J.VariableDeclarations varDecl = null;

                // Find the first MockedConstruction variable declaration with a matching close()
                for (int i = 0; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) statement;
                        if (!vd.getVariables().isEmpty()) {
                            J.VariableDeclarations.NamedVariable namedVar = vd.getVariables().get(0);
                            if (MOCK_CONSTRUCTION_MATCHER.matches(namedVar.getInitializer())) {
                                String name = namedVar.getSimpleName();
                                int foundCloseIndex = findCloseCallIndex(statements, name, i + 1);
                                if (foundCloseIndex != -1) {
                                    varDeclIndex = i;
                                    closeIndex = foundCloseIndex;
                                    varName = name;
                                    varDecl = vd;
                                    break;
                                }
                            }
                        }
                    }
                }

                // If no match found, just call super
                if (varDeclIndex == -1) {
                    return super.visitBlock(block, ctx);
                }

                // Build the try-with-resources
                // Collect statements that go inside the try body (between var decl and close)
                List<Statement> bodyStatements = new ArrayList<>();
                for (int j = varDeclIndex + 1; j < closeIndex; j++) {
                    bodyStatements.add(statements.get(j));
                }

                // Create new statement list
                List<Statement> newStatements = new ArrayList<>();

                // Add statements before the var declaration
                for (int j = 0; j < varDeclIndex; j++) {
                    newStatements.add(statements.get(j));
                }

                // Create try-with-resources
                J.Try tryWithResources = JavaTemplate.builder("try (#{any()}) {}")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-5"))
                        .build()
                        .apply(new Cursor(getCursor(), varDecl), varDecl.getCoordinates().replace(), varDecl);

                // Add body statements to the try block
                J.Block tryBody = new J.Block(randomId(), Space.EMPTY, Markers.EMPTY,
                        new org.openrewrite.java.tree.JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
                        emptyList(), Space.format(" ")).withStatements(bodyStatements);
                tryWithResources = tryWithResources.withBody(tryBody);
                tryWithResources = tryWithResources.withPrefix(varDecl.getPrefix());

                newStatements.add(tryWithResources);

                // Add statements after the close call
                for (int j = closeIndex + 1; j < statements.size(); j++) {
                    newStatements.add(statements.get(j));
                }

                J.Block newBlock = block.withStatements(newStatements);

                // Now call super on the new block to handle nested cases and other transformations
                newBlock = (J.Block) super.visitBlock(newBlock, ctx);

                return maybeAutoFormat(block, newBlock, ctx);
            }

            private int findCloseCallIndex(List<Statement> statements, String varName, int startIndex) {
                for (int i = startIndex; i < statements.size(); i++) {
                    Statement statement = statements.get(i);
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) statement;
                        if (MOCKED_CONSTRUCTION_CLOSE_MATCHER.matches(mi) &&
                            mi.getSelect() instanceof J.Identifier &&
                            ((J.Identifier) mi.getSelect()).getSimpleName().equals(varName)) {
                            return i;
                        }
                    }
                }
                return -1;
            }
        };
    }
}
