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

import org.openrewrite.java.AddImport;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.JavaIsoRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5 Assertions.assertThrows
 *
 * Currently only supports migration of this method from ExpectedException: void expect(Class<? extends Throwable> type)
 * Migrating the other methods of ExpectedException is not yet implemented.
 *
 */
public class ExpectedExceptionToAssertThrows extends JavaIsoRefactorVisitor {

    private static final String ExpectedExceptionFqn = "org.junit.rules.ExpectedException";

    public ExpectedExceptionToAssertThrows() {
        setCursoringOn();
    }

    @Override
    public J.ClassDecl visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl cd = super.visitClassDecl(classDecl);
        List<J.VariableDecls> expectedExceptionFields = classDecl.findFields(ExpectedExceptionFqn);
        if(expectedExceptionFields.size() > 0) {
            // Remove the ExpectedException fields
            List<J> statements = new ArrayList<>(classDecl.getBody().getStatements());
            statements.removeAll(expectedExceptionFields);
            cd = cd.withBody(classDecl.getBody().withStatements(statements));
        }
        return cd;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method) {
        if(method.getType() != null && method.getType().getDeclaringType().getFullyQualifiedName().equals(ExpectedExceptionFqn)) {
            J.MethodDecl enclosing = getCursor().firstEnclosing(J.MethodDecl.class);
            if(enclosing != null) {
                andThen(new Scoped(enclosing, method));
            }
        }
        return super.visitMethodInvocation(method);
    }

    private static class Scoped extends JavaIsoRefactorVisitor {
        private final J.MethodDecl enclosingMethodDecl;
        private final J.MethodInvocation expectedExceptionMethod;

        private Scoped(J.MethodDecl enclosingMethodDecl, J.MethodInvocation expectedExceptionMethod) {
            this.enclosingMethodDecl = enclosingMethodDecl;
            this.expectedExceptionMethod = expectedExceptionMethod;
        }

        @Override
        public J.MethodDecl visitMethod(J.MethodDecl m) {
            if(!(enclosingMethodDecl.isScope(m) && m.getBody() != null)) {
                return m;
            }

            List<Expression> args = expectedExceptionMethod.getArgs().getArgs();
            if(args.size() != 1) {
                return m;
            }

            Expression expectedException = args.get(0);
            JavaType.FullyQualified argType = TypeUtils.asFullyQualified(expectedException.getType());
            if(argType == null || !argType.getFullyQualifiedName().equals("java.lang.Class")) {
                return m;
            }

            // Remove the ExpectedException.expect() invocation, use the remaining statements as the lambda body for Assertions.assertThrows()
            List<Statement> statements = new ArrayList<>(m.getBody().getStatements());
            statements.remove(expectedExceptionMethod);

            J.MethodInvocation assertThrows = AssertionsBuilder.assertThrows(expectedException, statements);

            AddImport addAssertThrows = new AddImport();
            addAssertThrows.setType("org.junit.jupiter.api.Assertions");
            addAssertThrows.setStaticMethod("assertThrows");
            addAssertThrows.setOnlyIfReferenced(false);
            andThen(addAssertThrows);

            andThen(new AutoFormat(assertThrows));
            maybeRemoveImport("org.junit.Rule");
            maybeRemoveImport("org.junit.rules.ExpectedException");

            m = m.withBody(m.getBody().withStatements(singletonList(assertThrows)));

            return m;
        }
    }
}
