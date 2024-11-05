/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.MethodCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SimplifyMockitoVerifyWhenGiven extends Recipe {

    private static final MethodMatcher WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final MethodMatcher GIVEN_MATCHER = new MethodMatcher("org.mockito.BDDMockito given(..)");
    private static final MethodMatcher VERIFY_MATCHER = new MethodMatcher("org.mockito.Mockito verify(..)");
    private static final MethodMatcher STUBBER_MATCHER = new MethodMatcher("org.mockito.stubbing.Stubber when(..)");
    private static final MethodMatcher EQ_MATCHER = new MethodMatcher("org.mockito.ArgumentMatchers eq(..)");
    private static final MethodMatcher MOCKITO_EQ_MATCHER = new MethodMatcher("org.mockito.Mockito eq(..)");

    @Override
    public String getDisplayName() {
        return "Call to Mockito method \"verify\", \"when\" or \"given\" should be simplified";
    }

    @Override
    public String getDescription() {
        return "Fixes Sonar issue `java:S6068`: Call to Mockito method \"verify\", \"when\" or \"given\" should be simplified.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-6068");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(EQ_MATCHER), new UsesMethod<>(MOCKITO_EQ_MATCHER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(methodInvocation, ctx);

                        if ((WHEN_MATCHER.matches(mi) || GIVEN_MATCHER.matches(mi)) && mi.getArguments().get(0) instanceof J.MethodInvocation) {
                            List<Expression> updatedArguments = new ArrayList<>(mi.getArguments());
                            updatedArguments.set(0, checkAndUpdateEq((J.MethodInvocation) mi.getArguments().get(0)));
                            mi = mi.withArguments(updatedArguments);
                        } else if (VERIFY_MATCHER.matches(mi.getSelect()) ||
                                   STUBBER_MATCHER.matches(mi.getSelect())) {
                            mi = checkAndUpdateEq(mi);
                        }

                        maybeRemoveImport("org.mockito.ArgumentMatchers.eq");
                        maybeRemoveImport("org.mockito.Mockito.eq");
                        return mi;
                    }

                    private J.MethodInvocation checkAndUpdateEq(J.MethodInvocation methodInvocation) {
                        if (methodInvocation.getArguments().stream().allMatch(arg -> EQ_MATCHER.matches(arg) ||
                                                                                     MOCKITO_EQ_MATCHER.matches(arg))) {
                            return methodInvocation.withArguments(ListUtils.map(methodInvocation.getArguments(), invocation ->
                                    ((MethodCall) invocation).getArguments().get(0).withPrefix(invocation.getPrefix())));
                        }
                        return methodInvocation;
                    }
                });
    }

}
