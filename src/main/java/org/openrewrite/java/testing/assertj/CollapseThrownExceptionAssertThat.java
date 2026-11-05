/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.java.JavaFileChecker;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CollapseThrownExceptionAssertThat extends Recipe {

    private static final String ASSERTJ_ASSERTIONS = "org.assertj.core.api.Assertions";
    private static final String JUNIT_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher(ASSERTJ_ASSERTIONS + " assertThat(..)");
    private static final MethodMatcher ASSERT_THROWS = new MethodMatcher(JUNIT_ASSERTIONS + " assertThrows(java.lang.Class, org.junit.jupiter.api.function.Executable)");
    private static final MethodMatcher ASSERT_THAT_EXCEPTION_OF_TYPE = new MethodMatcher(ASSERTJ_ASSERTIONS + " assertThatExceptionOfType(java.lang.Class)");
    private static final MethodMatcher IS_THROWN_BY = new MethodMatcher("org.assertj.core.api.ThrowableTypeAssert isThrownBy(..)");
    private static final MethodMatcher ACTUAL = new MethodMatcher("org.assertj.core.api.* actual()", true);

    @Getter
    final String displayName = "Collapse a captured exception and its `assertThat` into `assertThatThrownBy`";

    @Getter
    final String description = "Replace an exception captured with JUnit's `assertThrows` or AssertJ's `assertThatExceptionOfType(..).isThrownBy(..).actual()`, and only used in the `assertThat` statement that follows, with a single `assertThatThrownBy(..).isInstanceOf(..)` chain.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new JavaFileChecker<>(),
                        new UsesMethod<>(ASSERT_THAT),
                        Preconditions.or(new UsesMethod<>(ASSERT_THROWS), new UsesMethod<>(IS_THROWN_BY))),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        List<Statement> statements = b.getStatements();
                        AtomicBoolean collapsedIntoPrevious = new AtomicBoolean();
                        return b.withStatements(ListUtils.map(statements, (i, statement) -> {
                            if (collapsedIntoPrevious.getAndSet(false)) {
                                return null;
                            }
                            J.MethodInvocation collapsed = i + 1 < statements.size() ? collapse(b, statement, statements.get(i + 1), ctx) : null;
                            if (collapsed == null) {
                                return statement;
                            }
                            collapsedIntoPrevious.set(true);
                            return collapsed;
                        }));
                    }

                    private J.@Nullable MethodInvocation collapse(J.Block block, Statement statement, Statement next, ExecutionContext ctx) {
                        if (!(statement instanceof J.VariableDeclarations) || !(next instanceof J.MethodInvocation) ||
                                !next.getPrefix().getComments().isEmpty()) {
                            return null;
                        }
                        J.VariableDeclarations declarations = (J.VariableDeclarations) statement;
                        if (declarations.getVariables().size() != 1) {
                            return null;
                        }
                        J.VariableDeclarations.NamedVariable variable = declarations.getVariables().get(0);
                        Expression initializer = variable.getInitializer();
                        if (!(initializer instanceof J.MethodInvocation) || variable.getVariableType() == null) {
                            return null;
                        }

                        J.MethodInvocation capture = (J.MethodInvocation) initializer;
                        Expression exceptionType;
                        Expression callable;
                        if (ASSERT_THROWS.matches(capture)) {
                            exceptionType = capture.getArguments().get(0);
                            callable = capture.getArguments().get(1);
                            if (!(callable instanceof J.Lambda) && !(callable instanceof J.MemberReference)) {
                                return null;
                            }
                        } else if (ACTUAL.matches(capture) &&
                                capture.getSelect() instanceof J.MethodInvocation &&
                                IS_THROWN_BY.matches(capture.getSelect()) &&
                                ((J.MethodInvocation) capture.getSelect()).getSelect() instanceof J.MethodInvocation &&
                                ASSERT_THAT_EXCEPTION_OF_TYPE.matches(((J.MethodInvocation) capture.getSelect()).getSelect())) {
                            J.MethodInvocation isThrownBy = (J.MethodInvocation) capture.getSelect();
                            exceptionType = ((J.MethodInvocation) isThrownBy.getSelect()).getArguments().get(0);
                            callable = isThrownBy.getArguments().get(0);
                        } else {
                            return null;
                        }

                        J.MethodInvocation assertThat = innermostAssertThat((J.MethodInvocation) next);
                        if (assertThat == null ||
                                !TypeUtils.isAssignableTo("org.assertj.core.api.AbstractThrowableAssert", assertThat.getType()) ||
                                !(assertThat.getArguments().get(0) instanceof J.Identifier) ||
                                !variable.getVariableType().equals(((J.Identifier) assertThat.getArguments().get(0)).getFieldType()) ||
                                countReferences(block, variable) != 1) {
                            return null;
                        }

                        maybeRemoveImport(JUNIT_ASSERTIONS + ".assertThrows");
                        maybeRemoveImport(JUNIT_ASSERTIONS);
                        maybeRemoveImport(ASSERTJ_ASSERTIONS + ".assertThatExceptionOfType");
                        maybeRemoveImport(ASSERTJ_ASSERTIONS + ".assertThat");
                        maybeAddImport(ASSERTJ_ASSERTIONS, "assertThatThrownBy");

                        J.MethodInvocation thrownBy = JavaTemplate.builder("assertThatThrownBy(#{any(org.assertj.core.api.ThrowableAssert.ThrowingCallable)}).isInstanceOf(#{any(java.lang.Class)})")
                                .staticImports(ASSERTJ_ASSERTIONS + ".assertThatThrownBy")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(new Cursor(getCursor(), assertThat), assertThat.getCoordinates().replace(), callable, exceptionType)
                                .withPrefix(assertThat.getPrefix());
                        return replaceSelect((J.MethodInvocation) next, assertThat, thrownBy, chainWrap((J.MethodInvocation) next)).withPrefix(statement.getPrefix());
                    }
                });
    }

    private static J.@Nullable MethodInvocation innermostAssertThat(J.MethodInvocation chain) {
        J.MethodInvocation current = chain;
        while (current.getSelect() instanceof J.MethodInvocation) {
            if (!current.getArguments().stream().allMatch(CollapseThrownExceptionAssertThat::isIndependentOfActualType)) {
                return null;
            }
            J.MethodInvocation select = (J.MethodInvocation) current.getSelect();
            if (ASSERT_THAT.matches(select)) {
                return select;
            }
            current = select;
        }
        return null;
    }

    private static boolean isIndependentOfActualType(Expression argument) {
        if (argument instanceof J.Lambda || argument instanceof J.MemberReference) {
            return false;
        }
        JavaType.Parameterized parameterized = TypeUtils.asParameterized(argument.getType());
        return parameterized == null || TypeUtils.isOfClassType(parameterized, "java.lang.Class");
    }

    private static int countReferences(J.Block block, J.VariableDeclarations.NamedVariable variable) {
        return new JavaIsoVisitor<AtomicInteger>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger count) {
                if (identifier != variable.getName() && variable.getVariableType().equals(identifier.getFieldType())) {
                    count.incrementAndGet();
                }
                return identifier;
            }
        }.reduce(block, new AtomicInteger()).get();
    }

    private static @Nullable Space chainWrap(J.MethodInvocation chain) {
        for (J.MethodInvocation m = chain; m.getSelect() instanceof J.MethodInvocation; m = (J.MethodInvocation) m.getSelect()) {
            String whitespace = m.getPadding().getSelect().getAfter().getWhitespace();
            if (whitespace.contains("\n")) {
                return Space.format(whitespace);
            }
        }
        return null;
    }

    private static J.MethodInvocation replaceSelect(J.MethodInvocation chain, J.MethodInvocation target, J.MethodInvocation replacement, @Nullable Space wrap) {
        JRightPadded<Expression> select = chain.getPadding().getSelect();
        if (select.getElement() == target) {
            return chain.getPadding().withSelect(select.withElement(replacement).withAfter(wrap == null ? select.getAfter() : wrap));
        }
        return chain.withSelect(replaceSelect((J.MethodInvocation) select.getElement(), target, replacement, wrap));
    }
}
