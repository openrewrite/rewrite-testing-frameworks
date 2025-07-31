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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class JUnitTryFailToAssertThatThrownBy extends Recipe {

    private static final MethodMatcher FAIL_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions fail(..)");
    private static final MethodMatcher JUNIT4_FAIL_MATCHER = new MethodMatcher("org.junit.Assert fail(..)");
    private static final MethodMatcher JUNIT_FAIL_MATCHER = new MethodMatcher("junit.framework.Assert fail(..)");
    private static final MethodMatcher ASSERT_EQUALS_MATCHER = new MethodMatcher("org.junit.jupiter.api.Assertions assertEquals(..)");
    private static final MethodMatcher JUNIT4_ASSERT_EQUALS_MATCHER = new MethodMatcher("org.junit.Assert assertEquals(..)");
    private static final MethodMatcher GET_MESSAGE_MATCHER = new MethodMatcher("java.lang.Throwable getMessage()", true);

    @Override
    public String getDisplayName() {
        return "Convert try-catch-fail blocks to AssertJ's assertThatThrownBy";
    }

    @Override
    public String getDescription() {
        return "Replace try-catch blocks where the try block ends with a `fail()` statement and the catch block optionally " +
               "contains assertions, with AssertJ's `assertThatThrownBy()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitTry(J.Try tryBlock, ExecutionContext ctx) {
                J.Try try_ = (J.Try) super.visitTry(tryBlock, ctx);

                // Check if this is a simple try-catch block without resources or finally
                if (try_.getResources() != null || try_.getCatches().size() != 1 || try_.getFinally() != null) {
                    return try_;
                }

                // Check if the try block ends with a fail() call
                List<Statement> tryStatements = try_.getBody().getStatements();
                if (tryStatements.isEmpty()) {
                    return try_;
                }

                Statement lastStatement = tryStatements.get(tryStatements.size() - 1);
                if (!(lastStatement instanceof J.MethodInvocation)) {
                    return try_;
                }

                J.MethodInvocation lastMethodCall = (J.MethodInvocation) lastStatement;
                if (!isFailMethod(lastMethodCall)) {
                    return try_;
                }

                // Get the catch block
                J.Try.Catch catchBlock = try_.getCatches().get(0);

                JavaType catchType = catchBlock.getParameter().getTree().getType();
                if (catchType == null) {
                    return try_;
                }

                // Extract the exception type
                JavaType.FullyQualified exceptionFqType = TypeUtils.asFullyQualified(catchType);
                if (exceptionFqType == null) {
                    return try_;
                }
                String exceptionType = exceptionFqType.getClassName();

                // Extract assertions from catch block
                List<String> assertions = extractAssertions(catchBlock, ctx);

                // Only convert if:
                // - Catch block is empty (just a comment), or
                // - Catch block has exactly one statement that we can convert to an assertion
                int catchStatements = catchBlock.getBody().getStatements().size();
                if (catchStatements > 1) {
                    return try_;
                }

                // If there's one statement but we couldn't extract an assertion from it, don't convert
                if (catchStatements == 1 && assertions.isEmpty()) {
                    // Check if the single statement is something we don't recognize
                    Statement stmt = catchBlock.getBody().getStatements().get(0);
                    if (!(stmt instanceof J.MethodInvocation &&
                          (ASSERT_EQUALS_MATCHER.matches((J.MethodInvocation)stmt) ||
                           JUNIT4_ASSERT_EQUALS_MATCHER.matches((J.MethodInvocation)stmt)))) {
                        return try_;
                    }
                }

                // Build the lambda body from try block statements (excluding the fail() call)
                List<Statement> lambdaStatements = new ArrayList<>(tryStatements.subList(0, tryStatements.size() - 1));

                // Generate the assertThatThrownBy code
                String template = buildTemplate(lambdaStatements, exceptionType, assertions);

                maybeAddImport("org.assertj.core.api.Assertions", "assertThatThrownBy");
                maybeRemoveImport("org.junit.jupiter.api.Assertions.fail");
                maybeRemoveImport("org.junit.Assert.fail");
                maybeRemoveImport("junit.framework.Assert.fail");

                // Remove assertEquals import if we converted it
                if (!assertions.isEmpty()) {
                    maybeRemoveImport("org.junit.jupiter.api.Assertions.assertEquals");
                    maybeRemoveImport("org.junit.Assert.assertEquals");
                }

                // Add import for the exception type if needed
                if (!exceptionFqType.getFullyQualifiedName().startsWith("java.lang.")) {
                    maybeAddImport(exceptionFqType.getFullyQualifiedName());
                }

                // Convert statements to an array for template arguments
                Object[] templateArgs = lambdaStatements.toArray();

                J.MethodInvocation assertThatThrownBy = JavaTemplate.builder(template)
                        .contextSensitive()
                        .staticImports("org.assertj.core.api.Assertions.assertThatThrownBy")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), try_.getCoordinates().replace(), templateArgs);

                return assertThatThrownBy;
            }

            private boolean isFailMethod(J.MethodInvocation method) {
                return FAIL_MATCHER.matches(method) ||
                       JUNIT4_FAIL_MATCHER.matches(method) ||
                       JUNIT_FAIL_MATCHER.matches(method);
            }


            private List<String> extractAssertions(J.Try.Catch catchBlock, ExecutionContext ctx) {
                List<String> assertions = new ArrayList<>();
                for (Statement statement : catchBlock.getBody().getStatements()) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) statement;
                        if (ASSERT_EQUALS_MATCHER.matches(mi) || JUNIT4_ASSERT_EQUALS_MATCHER.matches(mi)) {
                            // Handle assertEquals for exception message
                            if (mi.getArguments().size() >= 2) {
                                Expression arg1 = mi.getArguments().get(0);
                                Expression arg2 = mi.getArguments().get(1);

                                // Check if one of the arguments is e.getMessage()
                                if (GET_MESSAGE_MATCHER.matches(arg1)) {
                                    if (arg2 instanceof J.Literal) {
                                        J.Literal literal = (J.Literal) arg2;
                                        if (literal.getValue() instanceof String) {
                                            assertions.add(".hasMessage(\"" + literal.getValue() + "\")");
                                        }
                                    }
                                } else if (GET_MESSAGE_MATCHER.matches(arg2)) {
                                    if (arg1 instanceof J.Literal) {
                                        J.Literal literal = (J.Literal) arg1;
                                        if (literal.getValue() instanceof String) {
                                            assertions.add(".hasMessage(\"" + literal.getValue() + "\")");
                                        }
                                    }
                                }
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
        };
    }
}
