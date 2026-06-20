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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

public class SimplifyArrayLengthAssertion extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher ASSERTION_MATCHER = new MethodMatcher("org.assertj.core.api.* *(..)");

    @Getter
    final String displayName = "Simplify AssertJ assertions on an array's `length`";

    @Getter
    final String description = "Replace `assertThat(array.length)` size assertions with the dedicated array assertions, " +
            "such as `assertThat(array).hasSize(n)`, `assertThat(array).isEmpty()` and `assertThat(array).hasSameSizeAs(other)`. " +
            "Works for both object and primitive arrays.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!ASSERTION_MATCHER.matches(mi) || !(mi.getSelect() instanceof J.MethodInvocation)) {
                            return mi;
                        }

                        // The actual side must be an `assertThat(array.length)` call
                        J.MethodInvocation assertThat = (J.MethodInvocation) mi.getSelect();
                        if (!ASSERT_THAT_MATCHER.matches(assertThat) || assertThat.getArguments().size() != 1) {
                            return mi;
                        }
                        Expression array = arrayOfLength(assertThat.getArguments().get(0));
                        if (array == null) {
                            return mi;
                        }

                        Expression argument = mi.getArguments().isEmpty() ? null : mi.getArguments().get(0);
                        String template;
                        Expression secondArgument;
                        switch (mi.getSimpleName()) {
                            case "isZero":
                                template = "assertThat(#{any()}).isEmpty()";
                                secondArgument = null;
                                break;
                            case "isEqualTo":
                                Expression otherArray = arrayOfLength(argument);
                                if (isZeroLiteral(argument)) {
                                    template = "assertThat(#{any()}).isEmpty()";
                                    secondArgument = null;
                                } else if (otherArray != null) {
                                    template = "assertThat(#{any()}).hasSameSizeAs(#{any()})";
                                    secondArgument = otherArray;
                                } else {
                                    template = "assertThat(#{any()}).hasSize(#{any(int)})";
                                    secondArgument = argument;
                                }
                                break;
                            case "isLessThan":
                                template = "assertThat(#{any()}).hasSizeLessThan(#{any(int)})";
                                secondArgument = argument;
                                break;
                            case "isLessThanOrEqualTo":
                                template = "assertThat(#{any()}).hasSizeLessThanOrEqualTo(#{any(int)})";
                                secondArgument = argument;
                                break;
                            case "isGreaterThan":
                                template = "assertThat(#{any()}).hasSizeGreaterThan(#{any(int)})";
                                secondArgument = argument;
                                break;
                            case "isGreaterThanOrEqualTo":
                                template = "assertThat(#{any()}).hasSizeGreaterThanOrEqualTo(#{any(int)})";
                                secondArgument = argument;
                                break;
                            default:
                                return mi;
                        }

                        Object[] parameters = secondArgument == null ?
                                new Object[]{array} : new Object[]{array, secondArgument};
                        return JavaTemplate.builder(template)
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), parameters);
                    }

                    private boolean isZeroLiteral(@Nullable Expression expression) {
                        return expression instanceof J.Literal && Integer.valueOf(0).equals(((J.Literal) expression).getValue());
                    }

                    private @Nullable Expression arrayOfLength(@Nullable Expression expression) {
                        if (expression instanceof J.FieldAccess && "length".equals(((J.FieldAccess) expression).getSimpleName())) {
                            Expression target = ((J.FieldAccess) expression).getTarget();
                            if (target.getType() instanceof JavaType.Array) {
                                return target;
                            }
                        }
                        return null;
                    }
                });
    }
}
