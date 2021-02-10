/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.FindFields;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5 Assertions.assertThrows
 * <p>
 * Currently only supports migration of this method from ExpectedException: void expect(Class<? extends Throwable> type)
 * Migrating the other methods of ExpectedException is not yet implemented.
 */
public class ExpectedExceptionToAssertThrows extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ExpectedExceptionToAssertThrowsVisitor();
    }

    public static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String EXPECTED_EXCEPTION_FQN = "org.junit.rules.ExpectedException";

        public ExpectedExceptionToAssertThrowsVisitor() {
            setCursoringOn();
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<J.VariableDeclarations> expectedExceptionFields = FindFields.find(classDecl, EXPECTED_EXCEPTION_FQN);
            if (expectedExceptionFields.size() > 0) {
                // Remove the ExpectedException fields
                List<Statement> statements = new ArrayList<>(classDecl.getBody().getStatements());
                statements.removeAll(expectedExceptionFields);
                cd = cd.withBody(classDecl.getBody().withStatements(statements));
            }
            return cd;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getType() != null && method.getType().getDeclaringType().getFullyQualifiedName().equals(EXPECTED_EXCEPTION_FQN)) {
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosing != null) {
                    doAfterVisit(new Scoped(enclosing, method));
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }

        private static class Scoped extends JavaIsoVisitor<ExecutionContext> {

            private final J.MethodDeclaration scope;
            private final J.MethodInvocation methodInvocation;

            private Scoped(J.MethodDeclaration scope, J.MethodInvocation methodInvocation) {
                this.scope = scope;
                this.methodInvocation = methodInvocation;
                setCursoringOn();
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration m, ExecutionContext ctx) {
                if (!(scope.isScope(m) && m.getBody() != null)) {
                    return m;
                }

                List<Expression> args = methodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                Expression expectedException = args.get(0);
                JavaType.FullyQualified argType = TypeUtils.asFullyQualified(expectedException.getType());
                if (argType == null || !argType.getFullyQualifiedName().equals("java.lang.Class")) {
                    return m;
                }

                // Remove the ExpectedException.expect() invocation, use the remaining statements as the lambda body for Assertions.assertThrows()
                List<Statement> statements = new ArrayList<>(m.getBody().getStatements());
                statements.remove(methodInvocation);
                StringBuilder printedStatements = new StringBuilder();
                for (Statement stmt : statements) {
                    printedStatements.append(stmt.print()).append(';');
                }

                m = m.withBody(
                        m.getBody().withTemplate(
                                template("{ assertThrows(#{}, () -> { #{} }); }")
                                        .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                                        .build(),
                                m.getBody().getCoordinates().replace(),
                                expectedException, printedStatements.toString())
                );

                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.junit.rules.ExpectedException");

                doAfterVisit(new AddImport<>("org.junit.jupiter.api.Assertions", "assertThrows", false));

                m = m.withBody((J.Block) new AutoFormatVisitor<ExecutionContext>().visit(m.getBody(), ctx, getCursor()));
                return m;
            }
        }


    }
}
