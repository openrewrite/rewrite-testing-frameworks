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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CollapseAssertThatAndReturnActual extends Recipe {
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");

    @Getter
    final String displayName = "Collapse `assertThat` and `return` into single statement";

    @Getter
    final String description = "Collapse an `assertThat` statement followed by a `return` of the same object into a single `return assertThat(...).assertions().actual()` statement.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block bl = super.visitBlock(block, ctx);

                AtomicBoolean skip = new AtomicBoolean(false);
                List<Statement> statements = ListUtils.map(bl.getStatements(), (i, stmt) -> {
                    if (skip.getAndSet(false)) {
                        return null;
                    }
                    List<Statement> all = bl.getStatements();
                    if (i + 1 < all.size() &&
                        stmt instanceof J.MethodInvocation &&
                        all.get(i + 1) instanceof J.Return) {

                        J.MethodInvocation assertion = (J.MethodInvocation) stmt;
                        J.Return returnStatement = (J.Return) all.get(i + 1);

                        if (returnStatement.getExpression() != null && isAssertThatChain(assertion)) {
                            Expression assertThatArg = getAssertThatArgument(assertion);
                            if (assertThatArg != null &&
                                SemanticallyEqual.areEqual(assertThatArg, returnStatement.getExpression())) {
                                // Build the collapsed return statement
                                J.MethodInvocation withActual = appendActual(assertion.withPrefix(Space.EMPTY), assertThatArg.getType());
                                J.Return newReturn = returnStatement
                                        .withExpression(withActual.withPrefix(Space.SINGLE_SPACE))
                                        .withPrefix(assertion.getPrefix());
                                skip.set(true);
                                return newReturn;
                            }
                        }
                    }
                    return stmt;
                });

                return bl.withStatements(statements);
            }

            private J.MethodInvocation appendActual(J.MethodInvocation assertion, JavaType returnType) {
                JavaType.Method methodType = new JavaType.Method(
                        null,
                        Flag.Public.getBitMask(),
                        JavaType.ShallowClass.build("org.assertj.core.api.AbstractAssert"),
                        "actual",
                        returnType,
                        Collections.emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null
                );
                return new J.MethodInvocation(
                        Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        JRightPadded.build(assertion),
                        null,
                        new J.Identifier(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                Collections.emptyList(),
                                "actual",
                                methodType,
                                null
                        ),
                        JContainer.empty(),
                        methodType
                );
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
                // Check if mi directly selects on assertThat
                if (ASSERT_THAT.matches(current.getSelect())) {
                    J.MethodInvocation assertThat = (J.MethodInvocation) current.getSelect();
                    if (assertThat != null) {
                        Expression arg = assertThat.getArguments().get(0);
                        if (arg instanceof MethodCall) {
                            return false;
                        }
                        return isTypeCompatible(assertThat, mi);
                    }
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

            private Expression getAssertThatArgument(J.MethodInvocation mi) {
                J.MethodInvocation current = mi;
                while (current.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation select = (J.MethodInvocation) current.getSelect();
                    if (ASSERT_THAT.matches(select)) {
                        return select.getArguments().get(0);
                    }
                    current = select;
                }
                if (ASSERT_THAT.matches(current.getSelect()) && current.getSelect() instanceof J.MethodInvocation) {
                    return ((J.MethodInvocation) current.getSelect()).getArguments().get(0);
                }
                return null;
            }
        });
    }
}
