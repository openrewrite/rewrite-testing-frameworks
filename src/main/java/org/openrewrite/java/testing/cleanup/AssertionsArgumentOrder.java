/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;

public class AssertionsArgumentOrder extends Recipe {

    private static final MethodMatcher[] jupiterAssertionMatchers = new MethodMatcher[] {
            new MethodMatcher("org.junit.jupiter.api.Assertions assertArrayEquals(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertEquals(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertNotEquals(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertSame(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertNotSame(..)"),
            new MethodMatcher("org.junit.jupiter.api.Assertions assertArrayEquals(..)")
    };
    private static final MethodMatcher jupiterAssertIterableEqualsMatcher = new MethodMatcher("org.junit.jupiter.api.Assertions assertIterableEquals(..)");

    private static final MethodMatcher[] testNgMatcher = new MethodMatcher[] {
            new MethodMatcher("org.testng.Assert assertSame(..)"),
            new MethodMatcher("org.testng.Assert assertNotSame(..)"),
            new MethodMatcher("org.testng.Assert assertEquals(..)"),
            new MethodMatcher("org.testng.Assert assertNotEquals(..)")
    };

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public JavaSourceFile visitJavaSourceFile(JavaSourceFile cu, ExecutionContext ctx) {
                for (MethodMatcher jupiterAssertionMatcher : jupiterAssertionMatchers) {
                    doAfterVisit(new UsesMethod<>(jupiterAssertionMatcher));
                }
                for (MethodMatcher testNgAssertionMatcher : testNgMatcher) {
                    doAfterVisit(new UsesMethod<>(testNgAssertionMatcher));
                }
                doAfterVisit(new UsesMethod<>(jupiterAssertIterableEqualsMatcher));
                return cu;
            }
        };
    }

    @Override
    public String getDisplayName() {
        return "Assertion arguments should be passed in the correct order";
    }

    @Override
    public String getDescription() {
        return "Assertions such as `org.junit.Assert.assertEquals` expect the first argument to be the expected value and the second argument to be the actual value; for `org.testng.Assert`, it’s the other way around.  This recipe detects `J.Literal`, `J.NewArray`, and `java.util.Iterable` arguments swapping them if necessary so that the error messages will be confusing.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-3415");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AssertionsArgumentOrderVisitor();
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
            if (isJupiterAssertion(mi)) {
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
                    } else if (arg.equals(expected)) {
                        return actual;
                    }
                    return arg;
                })), ctx, getCursor().getParentOrThrow());
            }
            return mi;
        }

        private boolean isCorrectOrder(Expression expected, Expression actual, J.MethodInvocation mi) {
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
                var = ((J.Identifier)expression).getFieldType();
            } else if (expression instanceof J.FieldAccess) {
                var = ((J.FieldAccess) expression).getName().getFieldType();
            }
            if (var != null) {
                return var.hasFlags(Flag.Static, Flag.Final);
            }

            if (jupiterAssertIterableEqualsMatcher.matches(mi)) {
                for (MethodMatcher iterableMatcher : newListMatchers) {
                    if (iterableMatcher.matches(expression)){
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
            return jupiterAssertIterableEqualsMatcher.matches(mi);
        }

        private boolean isTestNgAssertion(J.MethodInvocation mi) {
            for (MethodMatcher actExpMatcher : testNgMatcher) {
                if (actExpMatcher.matches(mi)) {
                    return true;
                }
            }
            return false;
        }
    }
}
