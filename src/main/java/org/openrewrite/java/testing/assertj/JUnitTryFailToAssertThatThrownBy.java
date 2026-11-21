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
package org.openrewrite.java.testing.assertj;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JUnitTryFailToAssertThatThrownBy extends Recipe {

    private static final MethodMatcher ASSERTJ_FAIL_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions fail(..)");
    private static final MethodMatcher JUPITER_FAIL_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions fail(..)");
    private static final MethodMatcher JUNIT4_FAIL_MATCHER = new MethodMatcher("org.junit.Assert fail(..)");
    private static final MethodMatcher JUNIT_FAIL_MATCHER = new MethodMatcher("junit.framework.Assert fail(..)");

    private static final MethodMatcher ASSERT_EQUALS_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertEquals(..)");
    private static final MethodMatcher JUNIT4_ASSERT_EQUALS_MATCHER = new MethodMatcher("org.junit.Assert assertEquals(..)");
    private static final MethodMatcher GET_MESSAGE_MATCHER = new MethodMatcher("java.lang.Throwable getMessage()", true);

    private static boolean isMessageOf(Expression expression, String exceptionName) {
        if (!GET_MESSAGE_MATCHER.matches(expression)) {
            return false;
        }
        Expression select = ((J.MethodInvocation) expression).getSelect();
        return select instanceof J.Identifier && exceptionName.equals(((J.Identifier) select).getSimpleName());
    }

    private static @Nullable String stringLiteralSource(Expression expression) {
        if (expression instanceof J.Literal) {
            J.Literal literal = (J.Literal) expression;
            if (literal.getValue() instanceof String) {
                // The source keeps quotes and escapes as written
                return literal.getValueSource();
            }
        }
        return null;
    }

    /**
     * A lambda body cannot declare a variable as its only expression, and cannot assign to a local variable
     * declared outside of it.
     */
    private static boolean canBeLambdaBody(List<Statement> statements) {
        if (statements.size() == 1 && statements.get(0) instanceof J.VariableDeclarations) {
            return false;
        }
        Set<String> declared = new HashSet<>();
        for (Statement statement : statements) {
            new JavaIsoVisitor<Set<String>>() {
                @Override
                public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Set<String> names) {
                    names.add(variable.getSimpleName());
                    return super.visitVariable(variable, names);
                }
            }.visit(statement, declared);
        }
        boolean[] assignsOuterLocal = {false};
        for (Statement statement : statements) {
            new JavaIsoVisitor<Integer>() {
                @Override
                public J.Assignment visitAssignment(J.Assignment assignment, Integer p) {
                    check(assignment.getVariable());
                    return super.visitAssignment(assignment, p);
                }

                @Override
                public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, Integer p) {
                    check(assignOp.getVariable());
                    return super.visitAssignmentOperation(assignOp, p);
                }

                @Override
                public J.Unary visitUnary(J.Unary unary, Integer p) {
                    if (unary.getOperator().isModifying()) {
                        check(unary.getExpression());
                    }
                    return super.visitUnary(unary, p);
                }

                private void check(Expression target) {
                    if (target instanceof J.Identifier) {
                        J.Identifier id = (J.Identifier) target;
                        JavaType.Variable fieldType = id.getFieldType();
                        if (fieldType != null && fieldType.getOwner() instanceof JavaType.Method &&
                            !declared.contains(id.getSimpleName())) {
                            assignsOuterLocal[0] = true;
                        }
                    }
                }
            }.visit(statement, 0);
        }
        return !assignsOuterLocal[0];
    }

    @Getter
    final String displayName = "Convert try-catch-fail blocks to AssertJ's assertThatThrownBy";

    @Getter
    final String description = "Replace try-catch blocks where the try block ends with a `fail()` statement and the catch block optionally " +
            "contains assertions, with AssertJ's `assertThatThrownBy()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(ASSERTJ_FAIL_MATCHER), new UsesMethod<>(JUPITER_FAIL_MATCHER), new UsesMethod<>(JUNIT4_FAIL_MATCHER), new UsesMethod<>(JUNIT_FAIL_MATCHER)), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitTry(J.Try tryBlock, ExecutionContext ctx) {
                J.Try try_ = (J.Try) super.visitTry(tryBlock, ctx);

                // Check if this is a simple try-catch block without resources or finally
                if (try_.getResources() != null || try_.getCatches().size() != 1 || try_.getFinally() != null) {
                    return try_;
                }

                // Check if the try block ends with a fail() call
                List<Statement> tryStatements = try_.getBody().getStatements();
                if (tryStatements.isEmpty() || !isFailMethod(tryStatements.get(tryStatements.size() - 1))) {
                    return try_;
                }

                // Get the catch block
                J.Try.Catch catchBlock = try_.getCatches().get(0);

                // Extract the exception type
                JavaType.FullyQualified exceptionFqType = TypeUtils.asFullyQualified(catchBlock.getParameter().getTree().getType());
                if (exceptionFqType == null) {
                    return try_;
                }
                String exceptionType = exceptionFqType.getClassName();

                // Extract assertions from catch block
                String exceptionName = catchBlock.getParameter().getTree().getVariables().get(0).getSimpleName();
                List<String> assertions = extractAssertions(catchBlock, exceptionName);

                // Only convert if:
                // - Catch block is empty (just a comment), or
                // - We can convert all statements in the catch block to assertions
                if (assertions.size() != catchBlock.getBody().getStatements().size()) {
                    return try_;
                }

                // Build the lambda body from try block statements (excluding the fail() call)
                List<Statement> lambdaStatements = new ArrayList<>(tryStatements.subList(0, tryStatements.size() - 1));
                if (lambdaStatements.isEmpty() || !canBeLambdaBody(lambdaStatements)) {
                    return try_;
                }

                // Generate the assertThatThrownBy code
                String template = buildTemplate(lambdaStatements, exceptionType, assertions);

                maybeRemoveImport("org.assertj.core.api.Assertions.fail");
                maybeRemoveImport("org.junit.jupiter.api.Assertions.fail");
                maybeRemoveImport("org.junit.Assert.fail");
                maybeRemoveImport("junit.framework.Assert.fail");

                // Remove assertEquals import if we converted it
                if (!assertions.isEmpty()) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertEquals");
                    maybeRemoveImport("org.junit.Assert.assertEquals");
                }

                maybeAddImport("org.assertj.core.api.Assertions", "assertThatThrownBy");

                // Add import for the exception type if needed
                if (!exceptionFqType.getFullyQualifiedName().startsWith("java.lang.")) {
                    maybeAddImport(exceptionFqType.getFullyQualifiedName());
                }

                return JavaTemplate.builder(template)
                        .contextSensitive()
                        .staticImports("org.assertj.core.api.Assertions.assertThatThrownBy")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5", "assertj-core-3"))
                        .build()
                        .<J.MethodInvocation>apply(getCursor(), try_.getCoordinates().replace(), lambdaStatements.toArray());
            }

            private boolean isFailMethod(Statement method) {
                if (method instanceof Expression) {
                    return ASSERTJ_FAIL_MATCHER.matches((Expression) method) ||
                            JUPITER_FAIL_MATCHER.matches((Expression) method) ||
                            JUNIT4_FAIL_MATCHER.matches((Expression) method) ||
                            JUNIT_FAIL_MATCHER.matches((Expression) method);
                }
                return false;
            }

            private List<String> extractAssertions(J.Try.Catch catchBlock, String exceptionName) {
                List<String> assertions = new ArrayList<>();
                for (Statement statement : catchBlock.getBody().getStatements()) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) statement;
                        if ((ASSERT_EQUALS_MATCHER.matches(mi) || JUNIT4_ASSERT_EQUALS_MATCHER.matches(mi)) &&
                            2 <= mi.getArguments().size()) {
                            // Handle assertEquals for the exception's own message
                            Expression arg1 = mi.getArguments().get(0);
                            Expression arg2 = mi.getArguments().get(1);
                            String expected = null;
                            if (isMessageOf(arg1, exceptionName)) {
                                expected = stringLiteralSource(arg2);
                            } else if (isMessageOf(arg2, exceptionName)) {
                                expected = stringLiteralSource(arg1);
                            }
                            if (expected != null) {
                                assertions.add(".hasMessage(" + expected + ")");
                            }
                        }
                    }
                }
                return assertions;
            }

            private String buildTemplate(List<Statement> lambdaStatements, String exceptionType, List<String> assertions) {
                StringBuilder template = new StringBuilder();
                template.append("assertThatThrownBy(() -> ");

                if (lambdaStatements.size() == 1) {
                    // Single statement lambda
                    template.append("#{any()}");
                } else {
                    // Multi-statement lambda
                    template.append("{\n");
                    for (int i = 0; i < lambdaStatements.size(); i++) {
                        template.append("    #{any()};");
                        if (i < lambdaStatements.size() - 1) {
                            template.append("\n");
                        }
                    }
                    template.append("\n}");
                }

                template.append(").isInstanceOf(").append(exceptionType).append(".class)");

                for (String assertion : assertions) {
                    template.append(assertion);
                }

                return template.toString();
            }
        });
    }
}
