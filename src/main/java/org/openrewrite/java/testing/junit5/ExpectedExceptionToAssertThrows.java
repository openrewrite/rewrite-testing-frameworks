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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.FindFields;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;

/**
 * Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5 Assertions.assertThrows
 *
 * Currently only supports migration of this method from ExpectedException: void expect(Class<? extends Throwable> type)
 * Migrating the other methods of ExpectedException is not yet implemented.
 *
 */
public class ExpectedExceptionToAssertThrows extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ExpectedExceptionToAssertThrowsVisitor();
    }

    public static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String EXPECTED_EXCEPTION_FQN = "org.junit.rules.ExpectedException";
        private static final String EXPECTED_EXCEPTION_METHOD_MESSAGE_KEY = "expectedExceptionMethod";

        public ExpectedExceptionToAssertThrowsVisitor() {
            setCursoringOn();
        }

        @Override
        public J.ClassDecl visitClassDecl(J.ClassDecl classDecl, ExecutionContext ctx) {
            J.ClassDecl cd = super.visitClassDecl(classDecl, ctx);
            Set<J.VariableDecls> expectedExceptionFields = FindFields.find(classDecl, EXPECTED_EXCEPTION_FQN);
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
                J.MethodDecl enclosing = getCursor().firstEnclosing(J.MethodDecl.class);
                if (enclosing != null) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDecl.class, EXPECTED_EXCEPTION_METHOD_MESSAGE_KEY, method);
                }
            }
            return super.visitMethodInvocation(method, ctx);
        }

        @Override
        public J.MethodDecl visitMethod(J.MethodDecl m, ExecutionContext ctx) {

            J.MethodInvocation expectedExceptionMethod = getCursor().pollMessage(EXPECTED_EXCEPTION_METHOD_MESSAGE_KEY);
            if (expectedExceptionMethod == null) {
                return m;
            }

            List<Expression> args = expectedExceptionMethod.getArgs();
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
            statements.remove(expectedExceptionMethod);

            J.MethodInvocation assertThrows = AssertionsBuilder.assertThrows(expectedException, statements);

            maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");
            assertThrows = (J.MethodInvocation) new AutoFormatVisitor<ExecutionContext>().visit(assertThrows, ctx);
            maybeRemoveImport("org.junit.Rule");
            maybeRemoveImport("org.junit.rules.ExpectedException");

            m = m.withBody(m.getBody().withStatements(singletonList(assertThrows)));

            return m;
        }
    }
}
