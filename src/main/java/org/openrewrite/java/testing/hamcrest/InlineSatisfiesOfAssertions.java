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
package org.openrewrite.java.testing.hamcrest;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns {@code assertThat(x).satisfies(arg -> assertThat(arg).isEqualTo(1))} into {@code assertThat(x).isEqualTo(1)},
 * and likewise for {@code assumeThat(x)}, which the migration of Hamcrest assumptions and of matchers wrapping
 * another matcher produces on the way. Several assertions, as the migration of {@code allOf(..)} produces, are chained
 * when each but the last returns the assertion it is called on: in
 * {@code assertThat(list).satisfies(arg -> assertThat(arg).contains(a), arg -> assertThat(arg).contains(b))} the
 * lambdas' parameter is a {@code List<? extends E>}, whose assertion takes no {@code E}, where
 * {@code assertThat(list).contains(a).contains(b)} compiles.
 */
public class InlineSatisfiesOfAssertions extends Recipe {

    private static final MethodMatcher SATISFIES = new MethodMatcher("org.assertj.core.api.AbstractAssert satisfies(..)", true);
    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher ASSUME_THAT = new MethodMatcher("org.assertj.core.api.Assumptions assumeThat(..)");
    private static final MethodMatcher AS = new MethodMatcher("org.assertj.core.api.Descriptable as(..)", true);

    @Getter
    final String displayName = "Inline `satisfies(..)` of assertions on its argument";

    @Getter
    final String description = "Replace `assertThat(x).satisfies(arg -> assertThat(arg).isEqualTo(1))` with " +
            "`assertThat(x).isEqualTo(1)`, and the same for `assumeThat(x)`. Several assertions are chained when each " +
            "but the last returns the assertion it is called on.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SATISFIES), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!SATISFIES.matches(mi) || mi.getArguments().isEmpty() || !isAssertionRoot(mi.getSelect())) {
                    return mi;
                }
                List<J.MethodInvocation> bodies = new ArrayList<>();
                for (Expression argument : mi.getArguments()) {
                    J.MethodInvocation body = assertionOnParameter(argument);
                    if (body == null) {
                        return mi;
                    }
                    bodies.add(body);
                }
                JavaType receiverType = mi.getSelect().getType();
                for (int i = 0; i < bodies.size() - 1; i++) {
                    if (!returnsItsAssertion(bodies.get(i), receiverType)) {
                        return mi;
                    }
                }
                maybeRemoveImport("org.assertj.core.api.Assertions.assertThat");
                maybeRemoveImport("org.assertj.core.api.Assertions");
                Expression chain = mi.getSelect();
                for (J.MethodInvocation body : bodies) {
                    J.MethodInvocation innermost = innermost(body);
                    Expression receiver = chain;
                    //noinspection DataFlowIssue
                    chain = new JavaIsoVisitor<Integer>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, Integer p) {
                            if (m == innermost) {
                                return (J.MethodInvocation) receiver.withPrefix(m.getPrefix());
                            }
                            return super.visitMethodInvocation(m, p);
                        }
                    }.visitMethodInvocation(body, 0);
                }
                return (J.MethodInvocation) chain.withPrefix(mi.getPrefix());
            }

            /**
             * @return the body of a lambda {@code arg -> assertThat(arg).isEqualTo(1)} that asserts on its parameter
             * and uses it nowhere else
             */
            private J.@Nullable MethodInvocation assertionOnParameter(Expression argument) {
                if (!(argument instanceof J.Lambda)) {
                    return null;
                }
                J.Lambda lambda = (J.Lambda) argument;
                if (lambda.getParameters().getParameters().size() != 1 ||
                    !(lambda.getParameters().getParameters().get(0) instanceof J.VariableDeclarations) ||
                    !(lambda.getBody() instanceof J.MethodInvocation)) {
                    return null;
                }
                String parameter = ((J.VariableDeclarations) lambda.getParameters().getParameters().get(0))
                        .getVariables().get(0).getSimpleName();
                J.MethodInvocation body = (J.MethodInvocation) lambda.getBody();
                J.MethodInvocation innermost = innermost(body);
                if (innermost == body || !ASSERT_THAT.matches(innermost) || innermost.getArguments().size() != 1 ||
                    !(innermost.getArguments().get(0) instanceof J.Identifier) ||
                    !parameter.equals(((J.Identifier) innermost.getArguments().get(0)).getSimpleName()) ||
                    references(body, parameter) != 1) {
                    return null;
                }
                return body;
            }

            /**
             * Whether every method of the chain returns the assertion it is called on, as AssertJ's methods returning
             * {@code SELF} do, so that the next assertion can be chained to it. The methods are looked up on the type
             * of the assertion the chain will be called on, since inside the lambda they may not be attributed.
             */
            private boolean returnsItsAssertion(J.MethodInvocation chain, @Nullable JavaType receiverType) {
                JavaType.FullyQualified assertion = TypeUtils.asFullyQualified(receiverType);
                if (assertion == null) {
                    return false;
                }
                J.MethodInvocation innermost = innermost(chain);
                for (J.MethodInvocation m = chain; m != innermost; m = (J.MethodInvocation) m.getSelect()) {
                    if (!returnsSelf(assertion, m.getSimpleName(), new HashSet<>())) {
                        return false;
                    }
                }
                return true;
            }

            private boolean returnsSelf(JavaType.FullyQualified type, String name, Set<String> visited) {
                boolean found = false;
                Deque<JavaType.FullyQualified> types = new ArrayDeque<>();
                types.add(type);
                while (!types.isEmpty()) {
                    JavaType.FullyQualified t = types.poll();
                    if (!visited.add(t.getFullyQualifiedName())) {
                        continue;
                    }
                    for (JavaType.Method m : t.getMethods()) {
                        if (m.getName().equals(name)) {
                            JavaType returnType = m.getReturnType();
                            if (!(returnType instanceof JavaType.GenericTypeVariable &&
                                  "SELF".equals(((JavaType.GenericTypeVariable) returnType).getName()))) {
                                return false;
                            }
                            found = true;
                        }
                    }
                    if (t.getSupertype() != null) {
                        types.add(t.getSupertype());
                    }
                    types.addAll(t.getInterfaces());
                }
                return found;
            }

            private boolean isAssertionRoot(@Nullable Expression select) {
                while (select instanceof J.MethodInvocation && AS.matches((J.MethodInvocation) select)) {
                    select = ((J.MethodInvocation) select).getSelect();
                }
                return select instanceof J.MethodInvocation &&
                       (ASSERT_THAT.matches((J.MethodInvocation) select) || ASSUME_THAT.matches((J.MethodInvocation) select));
            }

            private J.MethodInvocation innermost(J.MethodInvocation chain) {
                J.MethodInvocation current = chain;
                while (current.getSelect() instanceof J.MethodInvocation) {
                    current = (J.MethodInvocation) current.getSelect();
                }
                return current;
            }

            private int references(J tree, String name) {
                return new JavaIsoVisitor<AtomicInteger>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, AtomicInteger c) {
                        // the method's own name is no reference to the parameter
                        visit(m.getSelect(), c);
                        for (Expression argument : m.getArguments()) {
                            visit(argument, c);
                        }
                        return m;
                    }

                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, AtomicInteger c) {
                        visit(fieldAccess.getTarget(), c);
                        return fieldAccess;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicInteger c) {
                        if (identifier.getSimpleName().equals(name)) {
                            c.incrementAndGet();
                        }
                        return identifier;
                    }
                }.reduce(tree, new AtomicInteger()).get();
            }
        });
    }
}
