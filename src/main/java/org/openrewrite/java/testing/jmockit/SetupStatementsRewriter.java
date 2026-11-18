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
package org.openrewrite.java.testing.jmockit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.RenameVariable;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
class SetupStatementsRewriter {

    private final JavaVisitor<ExecutionContext> visitor;
    private J.Block methodBody;

    J.Block rewriteMethodBody() {
        List<Statement> statements = methodBody.getStatements();
        // iterate over each statement in the method body, find JMockit blocks and rewrite them
        for (Statement s : statements) {
            if (!JMockitUtils.getJMockitBlock(s).isPresent()) {
                continue;
            }
            J.NewClass nc = (J.NewClass) s;
            List<J.Identifier> spies = new ArrayList<>();
            for (Expression newClassArg : nc.getArguments()) {
                if (newClassArg instanceof J.Identifier) {
                    spies.add((J.Identifier) newClassArg);
                }
            }

            assert nc.getBody() != null;

            // Check if the body has any statements - if empty, skip processing
            if (nc.getBody().getStatements().isEmpty()) {
                continue;
            }

            nc = renameSetupLocalsInScopeAtBlock(nc);
            rewriteBodyStatement(nc, nc.getCoordinates().replace());
            J.Block expectationsBlock = (J.Block) nc.getBody().getStatements().get(0);

            // collect setup statements
            List<Statement> setupStatements = new ArrayList<>();
            List<Statement> newExpectationsBlockStatements = new ArrayList<>();
            for (Statement st : expectationBlocks(nc)) {
                for (Statement expectationStatement : ((J.Block) st).getStatements()) {
                    if (!isSetupStatement(expectationStatement, spies)) {
                        newExpectationsBlockStatements.add(expectationStatement);
                        continue;
                    }
                    setupStatements.add(expectationStatement);
                }
            }

            // check if setup statement variable names conflict with method body variable names
            if (clashesInMethodBody(nc)) {
                // When there's a conflict, don't extract setup statements.
                // Leave them in the expectations block so JMockitBlockRewriter can handle them
                // and wrap everything in a block to avoid variable name conflicts.
                continue;
            }

            // move setup statements before the expectations block
            JavaCoordinates coordinates = nc.getCoordinates().before();
            if (!setupStatements.isEmpty()) {
                // move statements individually
                for (Statement setupStatement : setupStatements) {
                    rewriteBodyStatement(setupStatement, coordinates);
                    coordinates = setupStatement.getCoordinates().after();
                }
            }

            // the new expectations block has the setup statements removed
            J.Block newExpectationsBlock = expectationsBlock.withStatements(newExpectationsBlockStatements);
            nc = nc.withBody(nc.getBody().withStatements(singletonList(newExpectationsBlock)));

            rewriteBodyStatement(nc, nc.getCoordinates().replace());
        }
        return methodBody;
    }

    /**
     * Expectations and Verifications may contain several blocks.
     */
    private static List<Statement> expectationBlocks(J.NewClass jmockitBlock) {
        assert jmockitBlock.getBody() != null;
        List<Statement> blocks = jmockitBlock.getBody().getStatements();
        return TypeUtils.isAssignableTo("mockit.Expectations", jmockitBlock.getType()) ||
               TypeUtils.isAssignableTo("mockit.Verifications", jmockitBlock.getType()) ?
                blocks : singletonList(blocks.get(0));
    }

    /**
     * Where a parameter or an earlier local of its name is in scope, a setup local does not compile, hoisted or in a
     * block. Nor does one named like a setup local of an earlier block, as the blocks merge into one. It takes a free
     * name.
     */
    private J.NewClass renameSetupLocalsInScopeAtBlock(J.NewClass jmockitBlock) {
        Cursor bodyCursor = new Cursor(visitor.getCursor(), methodBody);
        Scope atBlock = Scope.at(bodyCursor).withPosition(new Cursor(bodyCursor, jmockitBlock));
        Set<String> handedOut = new HashSet<>();
        Set<String> declaredInEarlierBlocks = new HashSet<>();
        J.NewClass renamed = jmockitBlock;
        for (Statement block : expectationBlocks(jmockitBlock)) {
            Set<String> declaredInBlock = new HashSet<>();
            for (Statement statement : ((J.Block) block).getStatements()) {
                if (!(statement instanceof J.VariableDeclarations)) {
                    continue;
                }
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    declaredInBlock.add(variable.getSimpleName());
                    if (declaredInEarlierBlocks.contains(variable.getSimpleName()) ||
                        atBlock.lookup(variable.getSimpleName()).filter(Scope.Binding::isLocal).isPresent()) {
                        String newName = atBlock.freeName(variable.getSimpleName(), handedOut::contains);
                        handedOut.add(newName);
                        renamed = (J.NewClass) new RenameVariable<Integer>(variable, newName).visitNonNull(renamed, 0, bodyCursor);
                    }
                }
            }
            declaredInEarlierBlocks.addAll(declaredInBlock);
        }
        return renamed;
    }

    /**
     * A hoisted declaration is in scope for the rest of the method body. It clashes with a name declared at any
     * depth after it, and it would shadow a field or other outer variable that code after it reads.
     */
    private boolean clashesInMethodBody(J.NewClass jmockitBlock) {
        assert jmockitBlock.getBody() != null;
        Cursor bodyCursor = new Cursor(visitor.getCursor(), methodBody);
        List<Cursor> following = new ArrayList<>();
        boolean after = false;
        for (Statement bodyStatement : methodBody.getStatements()) {
            if (bodyStatement.isScope(jmockitBlock)) {
                after = true;
            } else if (after) {
                following.add(new Cursor(bodyCursor, bodyStatement));
            }
        }
        Cursor anonymousBody = new Cursor(new Cursor(bodyCursor, jmockitBlock), jmockitBlock.getBody());
        for (Statement block : expectationBlocks(jmockitBlock)) {
            if (Scope.at(new Cursor(anonymousBody, block)).collidesAcross(following)) {
                return true;
            }
        }
        return false;
    }

    private void rewriteBodyStatement(Statement statement, JavaCoordinates coordinates) {
        List<Statement> statements = new ArrayList<>(methodBody.getStatements());

        if (coordinates.getMode() == JavaCoordinates.Mode.REPLACEMENT) {
            // Replace the statement at the specified position
            for (int i = 0; i < statements.size(); i++) {
                if (statements.get(i).isScope(coordinates.getTree())) {
                    statements.set(i, statement);
                    break;
                }
            }
        } else {
            // Find the reference statement and insert before/after it
            for (int i = 0; i < statements.size(); i++) {
                if (statements.get(i).isScope(coordinates.getTree())) {
                    if (coordinates.getMode() == JavaCoordinates.Mode.BEFORE) {
                        statements.add(i, statement);
                    } else { // AFTER
                        statements.add(i + 1, statement);
                    }
                    break;
                }
            }
        }

        methodBody = methodBody.withStatements(statements);
    }

    private boolean isSetupStatement(Statement expectationStatement, List<J.Identifier> spies) {
        if (expectationStatement instanceof J.MethodInvocation) {
            // a method invocation on a mock is not a setup statement
            J.MethodInvocation methodInvocation = (J.MethodInvocation) expectationStatement;
            if (methodInvocation.getSelect() instanceof J.MethodInvocation) {
                return isSetupStatement((Statement) methodInvocation.getSelect(), spies);
            }
            if (methodInvocation.getSelect() instanceof J.Identifier) {
                return isNotMockIdentifier((J.Identifier) methodInvocation.getSelect(), spies);
            }
            if (methodInvocation.getSelect() instanceof J.FieldAccess) {
                return isNotMockIdentifier((J.Identifier) ((J.FieldAccess) methodInvocation.getSelect()).getTarget(),
                        spies);
            }
            return isNotMockIdentifier(methodInvocation.getName(), spies);
        }
        if (expectationStatement instanceof J.Assignment) {
            // an assignment to a jmockit reserved field is not a setup statement
            JavaType variableType = getVariableTypeFromAssignment((J.Assignment) expectationStatement);
            return !TypeUtils.isAssignableTo("mockit.Invocations", variableType);
        }
        return true;
    }

    private static JavaType getVariableTypeFromAssignment(J.Assignment assignment) {
        J.Identifier identifier = null;
        if (assignment.getVariable() instanceof J.Identifier) {
            identifier = (J.Identifier) assignment.getVariable();
        } else if (assignment.getVariable() instanceof J.FieldAccess) {
            J.FieldAccess fieldAccess = (J.FieldAccess) assignment.getVariable();
            if (fieldAccess.getTarget() instanceof J.Identifier) {
                identifier = (J.Identifier) fieldAccess.getTarget();
            }
        }
        if (identifier == null) {
            return null;
        }
        return identifier.getFieldType() != null ? identifier.getFieldType().getOwner() : identifier.getType();
    }

    private static boolean isNotMockIdentifier(J.Identifier identifier, List<J.Identifier> spies) {
        if (spies.stream().anyMatch(spy -> SemanticallyEqual.areEqual(spy, identifier))) {
            return false;
        }
        if (identifier.getType() instanceof JavaType.Method &&
            TypeUtils.isAssignableTo("mockit.Invocations",
                ((JavaType.Method) identifier.getType()).getDeclaringType())) {
            return false;
        }
        JavaType.Variable fieldType = identifier.getFieldType();
        if (fieldType == null) {
            return true;
        }
        for (JavaType.FullyQualified annotationType : fieldType.getAnnotations()) {
            if (TypeUtils.isAssignableTo("mockit.Mocked", annotationType) ||
                TypeUtils.isAssignableTo("mockit.Injectable", annotationType) ||
                TypeUtils.isAssignableTo("mockit.Tested", annotationType)) {
                return false;
            }
        }
        return true;
    }
}
