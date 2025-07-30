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
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;

public class AssertionsArgumentOrder extends Recipe {

    private static final MethodMatcher[] jupiterAssertionMatchers = new MethodMatcher[]{
            new MethodMatcher("org.junit.jupiter.api.Assertions assertArrayEquals(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertEquals(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertNotEquals(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertSame(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertNotSame(..)")
    };

    private static final MethodMatcher[] junitAssertMatchers = new MethodMatcher[]{
            new MethodMatcher("org.junit.Assert assertEquals(..)"),
            new MethodMatcher("org.junit.Assert assertEquals(..)"),
            new MethodMatcher("org.junit.Assert assertArrayEquals(..)"),
            new MethodMatcher("org.junit.Assert assertSame(..)"),
            new MethodMatcher("org.junit.Assert assertNotSame(..)"),
            new MethodMatcher("org.junit.Assert assert*Null(String, Object)")
    };

    private static final MethodMatcher[] junitAssertWithMessageMatchers = new MethodMatcher[]{
            new MethodMatcher("org.junit.Assert assertEquals(String, ..)"),
            new MethodMatcher("org.junit.Assert assertArrayEquals(String, ..)")
    };
    private static final MethodMatcher jupiterAssertIterableEqualsMatcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertIterableEquals(..)");

    // `assertNull("message", result())` should be `assertNull(result(), "message")`
    private static final MethodMatcher jupiterAssertNullMatcher = new MethodMatcher("org.junit.jupiter.api.Assertions assert*Null(Object, String)");

    private static final MethodMatcher[] testNgMatcher = new MethodMatcher[]{
            new MethodMatcher("org.testng.Assert assertSame(..)"),
            new MethodMatcher("org.testng.Assert assertNotSame(..)"),
            new MethodMatcher("org.testng.Assert assertEquals(..)"),
            new MethodMatcher("org.testng.Assert assertNotEquals(..)")
    };

    private static final TreeVisitor<?, ExecutionContext> precondition;

    static {
        List<MethodMatcher> matchers = new ArrayList<>(Arrays.asList(jupiterAssertionMatchers));
        matchers.addAll(Arrays.asList(junitAssertMatchers));
        matchers.addAll(Arrays.asList(junitAssertWithMessageMatchers));
        matchers.add(jupiterAssertIterableEqualsMatcher);
        matchers.add(jupiterAssertNullMatcher);
        matchers.addAll(Arrays.asList(testNgMatcher));
        //noinspection unchecked
        precondition = Preconditions.or(matchers.stream().map(UsesMethod::new).toArray(TreeVisitor[]::new));
    }

    @Override
    public String getDisplayName() {
        return "Assertion arguments should be passed in the correct order";
    }

    @Override
    public String getDescription() {
        return "Assertions such as `org.junit.Assert.assertEquals` expect the first argument to be the expected value and the second argument to be the actual value; for `org.testng.Assert`, it’s the other way around.  This recipe detects `J.Literal`, `J.NewArray`, and `java.util.Iterable` arguments swapping them if necessary so that the error messages won't be confusing.";
    }

    @Override
    public Set<String> getTags() {
        return singleton("RSPEC-S3415");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(precondition, new AssertionsArgumentOrderVisitor());
    }

    private static class AssertionsArgumentOrderVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher[] newListMatchers = new MethodMatcher[]{
                new MethodMatcher("java.util.List of(..)"),
                new MethodMatcher("java.util.Collections singleton(..)"),
                new MethodMatcher("java.util.Collections empty()"),
                new MethodMatcher("java.util.Arrays asList(..)"),
        };

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            final Expression expected;
            final Expression actual;
            if (isJunitAssertEqualsWithMessage(mi)) {
                expected = mi.getArguments().get(1);
                actual = mi.getArguments().get(2);
            } else if (isJunitAssertion(mi) || isJupiterAssertion(mi)) {
                expected = mi.getArguments().get(0);
                actual = mi.getArguments().get(1);
            } else if (isTestNgAssertion(mi)) {
                expected = mi.getArguments().get(1);
                actual = mi.getArguments().get(0);
            } else {
                return mi;
            }

            if (!isCorrectOrder(expected, actual, mi)) {
                mi = maybeAutoFormat(mi, mi.withArguments(ListUtils.map(mi.getArguments(), arg -> {
                    if (arg.equals(actual)) {
                        return expected;
                    }
                    if (arg.equals(expected)) {
                        return actual;
                    }
                    return arg;
                })), ctx, getCursor().getParentOrThrow());
            }
            return mi;
        }

        private boolean isCorrectOrder(Expression expected, Expression actual, J.MethodInvocation mi) {
            if (jupiterAssertNullMatcher.matches(mi)) {
                return isConstant(actual, mi) || !isConstant(expected, mi);
            }
            return isConstant(expected, mi) || !isConstant(actual, mi);
        }

        private boolean isConstant(Expression expression, J.MethodInvocation mi) {
            if (expression instanceof J.Literal) {
                return true;
            }

            if (expression instanceof J.NewArray) {
                return true;
            }

            // static final field
            JavaType.Variable var = null;
            if (expression instanceof J.Identifier) {
                var = ((J.Identifier) expression).getFieldType();
            } else if (expression instanceof J.FieldAccess) {
                var = ((J.FieldAccess) expression).getName().getFieldType();
            }
            if (var != null) {
                return var.hasFlags(Flag.Static, Flag.Final);
            }

            if (jupiterAssertIterableEqualsMatcher.matches(mi)) {
                for (MethodMatcher iterableMatcher : newListMatchers) {
                    if (iterableMatcher.matches(expression)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isJupiterAssertion(J.MethodInvocation mi) {
            for (MethodMatcher assertionMethodMatcher : jupiterAssertionMatchers) {
                if (assertionMethodMatcher.matches(mi)) {
                    return true;
                }
            }
            return jupiterAssertIterableEqualsMatcher.matches(mi) || jupiterAssertNullMatcher.matches(mi);
        }

        private boolean isTestNgAssertion(J.MethodInvocation mi) {
            for (MethodMatcher actExpMatcher : testNgMatcher) {
                if (actExpMatcher.matches(mi)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isJunitAssertion(J.MethodInvocation mi) {
            for (MethodMatcher assertionMethodMatcher : junitAssertMatchers) {
                if (assertionMethodMatcher.matches(mi)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isJunitAssertEqualsWithMessage(J.MethodInvocation mi) {
            for (MethodMatcher actExpMatcher : junitAssertWithMessageMatchers) {
                if (actExpMatcher.matches(mi)) {
                    return true;
                }
            }
            return false;
        }
    }
}
