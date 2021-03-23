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
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.search.FindFields;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5 Assertions.assertThrows
 */
public class ExpectedExceptionToAssertThrows extends Recipe {

    @Override
    public String getDisplayName() {
        return "ExpectedException To AssertThrows";
    }

    @Override
    public String getDescription() {
        return "Replace usages of JUnit 4's @Rule ExpectedException with JUnit 5 Assertions.assertThrows";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ExpectedExceptionToAssertThrowsVisitor();
    }

    public static class ExpectedExceptionToAssertThrowsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String EXPECTED_EXCEPTION_FQN = "org.junit.rules.ExpectedException";
        private static final String EXPECT_INVOCATION_KEY = "expectedExceptionMethodInvocation";
        private static final String EXPECT_MESSAGE_INVOCATION_KEY = "expectMessageMethodInvocation";

        private final JavaParser parser = JavaParser.fromJavaVersion().dependsOn(Arrays.asList(
                Parser.Input.fromString("" +
                        "package org.junit.jupiter.api;" +
                        "import java.util.function.Supplier;" +
                        "import org.junit.jupiter.api.function.Executable;" +
                        "class AssertThrows {\n" +
                        "static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable,Supplier<String> messageSupplier){}" +
                        "static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable,String message){}" +
                        "static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable){}" +
                        "}"),
                Parser.Input.fromString(
                        "package org.junit.jupiter.api.function;" +
                                "public interface Executable {\n" +
                                "void execute() throws Throwable;\n" +
                                "}"
                )
        )).build();

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
            Set<J.VariableDeclarations> expectedExceptionFields = FindFields.find(cd, EXPECTED_EXCEPTION_FQN);
            if (expectedExceptionFields.size() > 0) {
                // Remove the ExpectedException fields
                List<Statement> statements = new ArrayList<>(cd.getBody().getStatements());
                statements.removeAll(expectedExceptionFields);
                cd = cd.withBody(cd.getBody().withStatements(statements));
            }
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {

            J.MethodDeclaration m = super.visitMethodDeclaration(methodDecl, ctx);

            J.MethodInvocation expectedExceptionMethodInvocation = getCursor().pollMessage(EXPECT_INVOCATION_KEY);
            if (expectedExceptionMethodInvocation != null) {
                List<Expression> args = expectedExceptionMethodInvocation.getArguments();
                if (args.size() != 1) {
                    return m;
                }

                Expression expectedException = args.get(0);
                JavaType.FullyQualified argType = TypeUtils.asFullyQualified(expectedException.getType());
                if (argType == null || !argType.getFullyQualifiedName().equals("java.lang.Class")) {
                    return m;
                }

                // Remove the ExpectedException invocations, use the remaining statements as the lambda body for Assertions.assertThrows()
                assert m.getBody() != null;
                List<Statement> statements = m.getBody().getStatements().stream()
                        .filter(it -> !isExpectedExceptionMethodInvocation(it))
                        .collect(Collectors.toList());

                StringBuilder printedStatements = new StringBuilder();
                for (Statement stmt : statements) {
                    printedStatements.append(stmt.print()).append(';');
                }
                J.MethodInvocation expectedMessageMethodInvocation = getCursor().pollMessage(EXPECT_MESSAGE_INVOCATION_KEY);
                if(expectedMessageMethodInvocation == null) {
                    m = m.withBody(
                            m.getBody().withTemplate(
                                    template("{ assertThrows(#{}, () -> { #{} }); }")
                                            .javaParser(parser)
                                            .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                                            .build(),
                                    m.getBody().getCoordinates().replace(),
                                    expectedException, printedStatements.toString())
                    );
                } else {
                    String expectedMessage = expectedMessageMethodInvocation.getArguments().get(0).print();
                    m = m.withBody(
                            m.getBody().withTemplate(
                                    template("{ assertThrows(#{}, () -> { #{} }, #{}); }")
                                            .javaParser(parser)
                                            .staticImports("org.junit.jupiter.api.Assertions.assertThrows")
                                            .build(),
                                    m.getBody().getCoordinates().replace(),
                                    expectedException, printedStatements.toString(), expectedMessage));
                }

                maybeRemoveImport("org.junit.Rule");
                maybeRemoveImport("org.junit.rules.ExpectedException");

                maybeAddImport("org.junit.jupiter.api.Assertions", "assertThrows");

                m = m.withBody((J.Block) new AutoFormatVisitor<ExecutionContext>().visit(m.getBody(), ctx, getCursor()));
            }
            return m;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (method.getType() != null && method.getType().getDeclaringType().getFullyQualifiedName().equals(EXPECTED_EXCEPTION_FQN)) {
                if(method.getSimpleName().equals("expect")) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, EXPECT_INVOCATION_KEY, method);
                } else if(method.getSimpleName().equals("expectMessage")) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, EXPECT_MESSAGE_INVOCATION_KEY, method);
                }
            }
            return method;
        }

        private static boolean isExpectedExceptionMethodInvocation(Statement statement) {
            if(!(statement instanceof J.MethodInvocation)) {
                return false;
            }
            J.MethodInvocation m = (J.MethodInvocation) statement;
            if(m.getType() == null) {
                return false;
            }

            return TypeUtils.isOfClassType(m.getType().getDeclaringType(), EXPECTED_EXCEPTION_FQN);
        }
    }
}
