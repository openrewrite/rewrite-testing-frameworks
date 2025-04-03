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
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;

public class SimplifyHasSizeJAssertion extends Recipe {

    public static final String ASSERT_THAT = "org.assertj.core.api.Assertions assertThat(..)";
    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher(ASSERT_THAT);
    public static final String HAS_SIZE = "org.assertj.core.api.* hasSize(int)";
    private static final MethodMatcher HAS_SIZE_MATCHER = new MethodMatcher(HAS_SIZE);
    public static final String HAS_SAME_SIZE_AS = "hasSameSizeAs";

    private static final TypeMatcher CHAR_SEQUENCE_TYPE_MATCHER = new TypeMatcher("java.lang.CharSequence", true);
    private static final TypeMatcher ITERABLE_TYPE_MATCHER = new TypeMatcher("java.lang.Iterable", true);
    private static final TypeMatcher MAP_TYPE_MATCHER = new TypeMatcher("java.util.Map", true);

    @Override
    public String getDisplayName() {
        return "Simplify AssertJ assertions with hasSize argument";
    }

    @Override
    public String getDescription() {
        return "Simplify AssertJ assertions by replacing hasSize with hasSameSizeAs dedicated assertions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(ASSERT_THAT, true),
                        new UsesMethod<>(HAS_SIZE, true)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        if (!HAS_SIZE_MATCHER.matches(mi) || !ASSERT_THAT_MATCHER.matches(mi.getSelect())) {
                            return mi;
                        }

                        Expression expression = mi.getArguments().get(0);

                        if (expression instanceof J.MethodInvocation) {
                            Expression argument = ((J.MethodInvocation) expression).getSelect();
                            JavaType type = argument.getType();
                            System.out.println(type);

                            if (CHAR_SEQUENCE_TYPE_MATCHER.matches(type) ||
                                    ITERABLE_TYPE_MATCHER.matches(type) ||
                                    MAP_TYPE_MATCHER.matches(type)) {
                                mi = mi.withMethodType(mi.getMethodType().withName(HAS_SAME_SIZE_AS))
                                        .withName(mi.getName().withSimpleName(HAS_SAME_SIZE_AS))
                                        .withArguments(Collections.singletonList(argument));
                            }
                        }

                        if (expression instanceof J.FieldAccess) {
                            Expression target = ((J.FieldAccess) expression).getTarget();

                            if (target.getType() instanceof JavaType.Array) {
                                mi = mi.withMethodType(mi.getMethodType().withName(HAS_SAME_SIZE_AS))
                                        .withName(mi.getName().withSimpleName(HAS_SAME_SIZE_AS))
                                        .withArguments(Collections.singletonList(target));
                            }
                        }
                        return mi;
                    }
                });
    }
}
