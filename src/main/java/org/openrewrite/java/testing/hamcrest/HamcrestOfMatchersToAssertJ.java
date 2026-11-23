/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.hamcrest;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

public class HamcrestOfMatchersToAssertJ extends Recipe {
    @Getter
    final String displayName = "Migrate `anyOf` and `allOf` Hamcrest matchers to AssertJ";

    @Getter
    final String description = "Migrate Hamcrest `assertThat(actual, anyOf(..))` to AssertJ's `satisfiesAnyOf(..)` and `assertThat(actual, allOf(..))` to `satisfies(..)`, keeping each nested matcher as a Hamcrest `assertThat` inside a lambda. `allOf(..)` on an `Iterable` that can be evaluated again becomes an assertion for each matcher, since a lambda would take a `List<? extends E>` that no assertion of an element compiles on. Matchers that take an `Iterable` are left unchanged.";

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher ANY_OF_MATCHER = new MethodMatcher("org.hamcrest.*Matchers anyOf(..)");
    private static final MethodMatcher ALL_OF_MATCHER = new MethodMatcher("org.hamcrest.*Matchers allOf(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesMethod<>(ANY_OF_MATCHER),
                new UsesMethod<>(ALL_OF_MATCHER)
        ), new AnyOfToAssertJVisitor());
    }

    private static class AnyOfToAssertJVisitor extends JavaIsoVisitor<ExecutionContext> {
        /**
         * {@code assertThat(list, allOf(hasItem(a), hasItem(b)))} would become a {@code satisfies(..)} whose lambdas take
         * a {@code List<? extends E>}, on which neither Hamcrest's nor AssertJ's assertion of an element compiles. So
         * {@code allOf} on an {@code Iterable} that can be evaluated again becomes an assertion for each matcher.
         */
        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            return super.visitBlock(block.withStatements(ListUtils.flatMap(block.getStatements(), this::splitAllOf)), ctx);
        }

        private Object splitAllOf(Statement statement) {
            if (!(statement instanceof J.MethodInvocation) || !ASSERT_THAT_MATCHER.matches((J.MethodInvocation) statement)) {
                return statement;
            }
            J.MethodInvocation mi = (J.MethodInvocation) statement;
            List<Expression> arguments = mi.getArguments();
            Expression ofExpression = arguments.get(arguments.size() - 1);
            Expression actual = arguments.get(arguments.size() - 2);
            if (!ALL_OF_MATCHER.matches(ofExpression) ||
                !TypeUtils.isAssignableTo("java.lang.Iterable", actual.getType()) || !evaluatesAgain(actual)) {
                return statement;
            }
            List<Expression> matchers = ((J.MethodInvocation) ofExpression).getArguments();
            if (matchers.size() < 2 || TypeUtils.isAssignableTo("java.lang.Iterable", matchers.get(0).getType())) {
                return statement;
            }
            List<Statement> assertions = new ArrayList<>(matchers.size());
            for (int i = 0; i < matchers.size(); i++) {
                Expression matcher = matchers.get(i).withPrefix(ofExpression.getPrefix());
                J.MethodInvocation assertion = mi.withArguments(ListUtils.map(arguments,
                        (j, argument) -> j == arguments.size() - 1 ? matcher : argument));
                if (i > 0) {
                    assertion = (J.MethodInvocation) new RandomizeIdVisitor<Integer>().visitNonNull(
                            assertion.withPrefix(mi.getPrefix().withComments(emptyList())), 0);
                }
                assertions.add(assertion);
            }
            maybeRemoveImport("org.hamcrest.Matchers.allOf");
            maybeRemoveImport("org.hamcrest.CoreMatchers.allOf");
            return assertions;
        }

        /**
         * Whether evaluating the expression again gives the same value, as a variable, a field and a getter do.
         */
        private boolean evaluatesAgain(@Nullable Expression expression) {
            if (expression == null || expression instanceof J.Identifier || expression instanceof J.Literal) {
                return true;
            }
            if (expression instanceof J.FieldAccess) {
                return evaluatesAgain(((J.FieldAccess) expression).getTarget());
            }
            if (expression instanceof J.MethodInvocation) {
                J.MethodInvocation m = (J.MethodInvocation) expression;
                return m.getArguments().stream().allMatch(J.Empty.class::isInstance) &&
                       m.getSimpleName().matches("(get|is|has)[A-Z].*") && evaluatesAgain(m.getSelect());
            }
            return false;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);
            List<Expression> arguments = mi.getArguments();
            Expression ofExpression = arguments.get(arguments.size() - 1);
            boolean allOfMatcherMatches = ALL_OF_MATCHER.matches(ofExpression);
            if (!ASSERT_THAT_MATCHER.matches(mi) || !(ANY_OF_MATCHER.matches(ofExpression) || allOfMatcherMatches) ||
                !HamcrestMatcherUtils.isTyped(mi)) {
                return mi;
            }

            // Skip anyOf(Iterable)
            List<Expression> anyOfArguments = ((J.MethodInvocation) ofExpression).getArguments();
            if (TypeUtils.isAssignableTo("java.lang.Iterable", anyOfArguments.get(0).getType())) {
                return mi;
            }

            StringBuilder template = new StringBuilder();
            List<Expression> parameters = new ArrayList<>();

            // assertThat(actual)
            Expression actual = arguments.get(arguments.size() - 2);
            String witness = witness(actual);
            template.append(witness == null ? "assertThat(#{any()})\n" : "Assertions.<" + witness + ">assertThat(#{any()})\n");
            parameters.add(actual);

            // .as("...")
            if (arguments.size() == 3) {
                template.append(".as(#{any(java.lang.String)})\n");
                parameters.add(arguments.get(0));
            }

            String arg = Scope.at(getCursor()).freeName("arg");
            // .satisfiesAnyOf(...) or .satisfies(...)
            template.append(allOfMatcherMatches ? ".satisfies(\n" : ".satisfiesAnyOf(\n");
            template.append(anyOfArguments.stream()
                    .map(m -> arg + " -> assertThat(" + arg + ", #{any()})")
                    .collect(joining(",\n")));
            parameters.addAll(anyOfArguments);
            template.append("\n);");

            maybeRemoveImport("org.hamcrest.Matchers.anyOf");
            maybeRemoveImport("org.hamcrest.Matchers.allOf");
            maybeRemoveImport("org.hamcrest.CoreMatchers.anyOf");
            maybeRemoveImport("org.hamcrest.CoreMatchers.allOf");
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
            if (witness != null) {
                maybeAddImport("org.assertj.core.api.Assertions");
            }
            J.MethodInvocation migrated = JavaTemplate.builder(template.toString())
                    .contextSensitive()
                    .imports("org.assertj.core.api.Assertions")
                    .staticImports("org.assertj.core.api.Assertions.assertThat")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx,
                            "assertj-core-3",
                            "hamcrest-3",
                            "junit-jupiter-api-5"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), parameters.toArray());
            if (witness != null) {
                doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(migrated));
            }
            return migrated;
        }

        /**
         * AssertJ's assertion of an {@code Iterable} or {@code Iterator} has a wildcard type, such as
         * {@code Collection<? extends E>} for a {@code Set<E>}, so its lambdas take a value that the nested matchers
         * don't, where {@code Assertions.<Set<E>>assertThat(actual)} passes the value as its own type.
         */
        private @Nullable String witness(Expression actual) {
            if (!TypeUtils.isAssignableTo("java.lang.Iterable", actual.getType()) &&
                !TypeUtils.isAssignableTo("java.util.Iterator", actual.getType())) {
                return null;
            }
            return HamcrestRemainingMatchersToAssertJ.typeName(actual.getType());
        }
    }
}
