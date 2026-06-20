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
import org.openrewrite.java.tree.TypeUtils;

public class SimplifyHasSizeFromIsEqualToAssertion extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher IS_EQUAL_TO_MATCHER = new MethodMatcher("org.assertj.core.api.* isEqualTo(..)");

    private static final MethodMatcher CHAR_SEQUENCE_LENGTH_MATCHER = new MethodMatcher("java.lang.CharSequence length()", true);
    private static final MethodMatcher ITERABLE_SIZE_MATCHER = new MethodMatcher("java.lang.Iterable size()", true);
    private static final MethodMatcher MAP_SIZE_MATCHER = new MethodMatcher("java.util.Map size()", true);

    @Getter
    final String displayName = "Simplify literal-first AssertJ size assertions to `hasSize`";

    @Getter
    final String description = "Replace `assertThat(<int literal>).isEqualTo(collection.size())` style assertions with " +
            "the dedicated `assertThat(collection).hasSize(<int literal>)`. " +
            "Only the structural size form is rewritten, where the comparison is on a primitive `int` and reversing the " +
            "assertion is behavior-preserving (unlike arbitrary `isEqualTo` object comparisons, which rely on `equals`).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(IS_EQUAL_TO_MATCHER),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (!IS_EQUAL_TO_MATCHER.matches(mi) || mi.getArguments().size() != 1 ||
                                !(mi.getSelect() instanceof J.MethodInvocation)) {
                            return mi;
                        }

                        // The actual side must be an `assertThat(<int literal>)` call
                        J.MethodInvocation assertThat = (J.MethodInvocation) mi.getSelect();
                        if (!ASSERT_THAT_MATCHER.matches(assertThat) || assertThat.getArguments().size() != 1) {
                            return mi;
                        }
                        Expression actualLiteral = assertThat.getArguments().get(0);
                        if (!(actualLiteral instanceof J.Literal) ||
                                TypeUtils.asPrimitive(actualLiteral.getType()) != JavaType.Primitive.Int) {
                            return mi;
                        }
                        Object literalValue = ((J.Literal) actualLiteral).getValue();
                        if (!(literalValue instanceof Integer) || (Integer) literalValue < 0) {
                            return mi;
                        }

                        // The expected side must be a structural size/length call on the meaningful expression
                        Expression sizeExpression = mi.getArguments().get(0);
                        Expression actual = extractSizedExpression(sizeExpression);
                        if (actual == null) {
                            return mi;
                        }

                        return JavaTemplate.builder("assertThat(#{any()}).hasSize(#{any(int)})")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace(), actual, actualLiteral);
                    }

                    private @Nullable Expression extractSizedExpression(Expression sizeExpression) {
                        if (sizeExpression instanceof J.MethodInvocation) {
                            if (CHAR_SEQUENCE_LENGTH_MATCHER.matches(sizeExpression) ||
                                    ITERABLE_SIZE_MATCHER.matches(sizeExpression) ||
                                    MAP_SIZE_MATCHER.matches(sizeExpression)) {
                                return ((J.MethodInvocation) sizeExpression).getSelect();
                            }
                        } else if (sizeExpression instanceof J.FieldAccess) {
                            Expression target = ((J.FieldAccess) sizeExpression).getTarget();
                            if (target.getType() instanceof JavaType.Array) {
                                return target;
                            }
                        }
                        return null;
                    }
                });
    }
}
