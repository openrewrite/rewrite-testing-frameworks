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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReturnActual extends Recipe {
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");

    @Getter
    final String displayName = "Collapse `assertThat` followed by `return` into single statement";

    @Getter
    final String description = "Collapse an `assertThat` statement followed by a `return` of the same object into a single `return assertThat(...).assertions().actual()` statement.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = super.visitBlock(block, ctx);

                // Quick checks to avoid unnecessary processing: we need at least two statements and the last one must be a return
                int numberOfStatements = bl.getStatements().size();
                if (numberOfStatements < 2) {
                    return bl;
                }
                Statement maybeReturn = bl.getStatements().get(numberOfStatements - 1);
                if (!(maybeReturn instanceof J.Return)) {
                    return bl;
                }
                J.Return returnStatement = (J.Return) maybeReturn;
                if (returnStatement.getExpression() == null) {
                    return bl;
                }
                Statement maybeAssertion = bl.getStatements().get(numberOfStatements - 2);
                if (!(maybeAssertion instanceof J.MethodInvocation)) {
                    return bl;
                }
                J.MethodInvocation assertion = (J.MethodInvocation) maybeAssertion;
                if (!isAssertThatChain(assertion)) {
                    return bl;
                }
                Expression assertThatArg = getAssertThatArgument(assertion);
                if (assertThatArg == null ||
                        !SemanticallyEqual.areEqual(assertThatArg, returnStatement.getExpression())) {
                    return bl;
                }

                // Build the new block with the collapsed return statement
                List<Statement> withoutReturn = ListUtils.mapLast(bl.getStatements(), stmt -> null);
                return bl.withStatements(ListUtils.mapLast(withoutReturn, stmt -> {
                    // Build the collapsed return statement
                    J.MethodInvocation withActual = JavaTemplate.builder("#{any()}.actual()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                            .build()
                            .apply(new Cursor(getCursor(), assertion), assertion.getCoordinates().replace(), assertion)
                            .withPrefix(Space.SINGLE_SPACE);
                    return returnStatement
                            .withExpression(withActual)
                            .withPrefix(assertion.getPrefix());
                }));
            }

            private boolean isAssertThatChain(J.MethodInvocation mi) {
                // Walk the select chain to find assertThat at the root
                J.MethodInvocation current = mi;
                while (current.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation select = (J.MethodInvocation) current.getSelect();
                    if (ASSERT_THAT.matches(select)) {
                        // Found assertThat at root; validate argument is not a method call
                        Expression arg = select.getArguments().get(0);
                        if (arg instanceof MethodCall) {
                            return false;
                        }
                        // Validate type compatibility of the outermost assertion
                        return isTypeCompatible(select, mi);
                    }
                    current = select;
                }
                return false;
            }

            private boolean isTypeCompatible(J.MethodInvocation assertThat, J.MethodInvocation outermost) {
                JavaType assertThatType = assertThat.getType();
                JavaType assertionType = outermost.getType();

                if (assertThatType != null && assertionType != null) {
                    if (TypeUtils.isOfType(assertThatType, assertionType)) {
                        return true;
                    }

                    JavaType.Parameterized assertThatFq = TypeUtils.asParameterized(assertThatType);
                    JavaType.Parameterized assertionFq = TypeUtils.asParameterized(assertionType);

                    if (assertionFq == null && assertionType instanceof JavaType.GenericTypeVariable) {
                        JavaType.GenericTypeVariable genericType = (JavaType.GenericTypeVariable) assertionType;
                        if (!genericType.getBounds().isEmpty()) {
                            assertionFq = TypeUtils.asParameterized(genericType.getBounds().get(0));
                        }
                    }

                    if (assertThatFq != null && assertionFq != null) {
                        return TypeUtils.isOfType(assertThatFq.getType(), assertionFq.getType());
                    }
                }
                return false;
            }

            private @Nullable Expression getAssertThatArgument(J.MethodInvocation mi) {
                J.MethodInvocation current = mi;
                while (current.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation select = (J.MethodInvocation) current.getSelect();
                    if (ASSERT_THAT.matches(select)) {
                        return select.getArguments().get(0);
                    }
                    current = select;
                }
                return null;
            }
        });
    }
}
