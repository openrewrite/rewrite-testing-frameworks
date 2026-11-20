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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

/**
 * Hamcrest's {@code assertThat(null, equalTo(x))} becomes AssertJ's {@code assertThat(null).isEqualTo(x)}, which
 * matches every overload of AssertJ's {@code assertThat} and doesn't compile. Cast the {@code null} to {@code Object}
 * first, so that the assertion resolves to {@code assertThat(Object)}.
 */
public class CastNullActualToObject extends Recipe {

    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");

    @Getter
    final String displayName = "Cast a `null` actual value of a Hamcrest assertion to `Object`";

    @Getter
    final String description = "Cast the `null` that Hamcrest's `assertThat(null, matcher)` asserts on to `Object`, " +
            "so that the AssertJ assertion it is migrated to resolves to one overload of `assertThat`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                List<Expression> args = mi.getArguments();
                if (!ASSERT_THAT.matches(mi) || args.size() < 2) {
                    return mi;
                }
                Expression actual = args.get(args.size() - 2);
                if (!(actual instanceof J.Literal) || ((J.Literal) actual).getType() != JavaType.Primitive.Null) {
                    return mi;
                }
                Expression cast = JavaTemplate.apply("(Object) null", new Cursor(getCursor(), actual),
                        actual.getCoordinates().replace());
                int index = args.size() - 2;
                return mi.withArguments(ListUtils.map(args, (i, arg) -> i == index ? cast : arg));
            }
        });
    }
}
