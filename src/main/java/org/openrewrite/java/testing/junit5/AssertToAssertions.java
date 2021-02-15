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
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Change JUnit4's org.junit.Assert into JUnit5's org.junit.jupiter.api.Assertions
 * The most significant difference between these classes is that in JUnit4 the optional String message is the first
 * parameter, and the JUnit5 versions have the optional message as the final parameter
 */
public class AssertToAssertions extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertToAssertionsVisitor();
    }

    public static class AssertToAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            doAfterVisit(new ChangeType("org.junit.Assert", "org.junit.jupiter.api.Assertions"));
            return super.visitClassDeclaration(classDecl, ctx);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (!isJunitAssertMethod(m)) {
                return m;
            }
            List<Expression> args = m.getArguments();
            Expression firstArg = args.get(0);
            // Suppress arg-switching for Assertions.assertEquals(String, String)
            if (args.size() == 2 && isString(firstArg.getType()) && isString(args.get(1).getType())) {
                return m;
            }
            if (isString(firstArg.getType())) {
                // Move the first arg to be the last argument

                List<Expression> newArgs = Stream.concat(
                        args.stream().skip(1),
                        Stream.of(firstArg)
                ).collect(Collectors.toList());
                m = m.withArguments(newArgs);
            }
            m = maybeAutoFormat(method, m, ctx, getCursor().dropParentUntil(it -> it instanceof J));

            return m;
        }

        private boolean isJunitAssertMethod(J.MethodInvocation method) {
            if (!(method.getSelect() instanceof J.Identifier)) {
                return false;
            }
            J.Identifier receiver = (J.Identifier) method.getSelect();
            if (!(receiver.getType() instanceof JavaType.FullyQualified)) {
                return false;
            }
            JavaType.FullyQualified receiverType = (JavaType.FullyQualified) receiver.getType();
            return receiverType.getFullyQualifiedName().equals("org.junit.Assert");
        }

        private boolean isString(JavaType type) {
            return type instanceof JavaType.Primitive
                    && ((JavaType.Primitive) type).name().equals("String");
        }
    }
}