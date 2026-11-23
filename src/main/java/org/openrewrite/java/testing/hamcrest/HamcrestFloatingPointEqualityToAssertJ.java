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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class HamcrestFloatingPointEqualityToAssertJ extends Recipe {
    @Getter
    final String displayName = "Keep the floating point equality of Hamcrest's `equalTo(..)` in AssertJ";

    @Getter
    final String description = "Hamcrest's `equalTo(..)` and `is(..)` compare a `float` or `double` with `equals(..)`, " +
            "which takes `NaN` to equal itself, where AssertJ's `isEqualTo(float)` and `isEqualTo(double)` compare " +
            "with `==`, which doesn't. Replace `equalTo(Float.NaN)` and `is(Double.NaN)` with AssertJ's `isNaN()`, " +
            "and their `not(..)` with `isNotNaN()`, and box any other expected value but a literal, such as " +
            "`is(Double.valueOf(expected))`, so that it is compared with AssertJ's `isEqualTo(Double)`, which uses " +
            "`equals(..)` too.";

    private static final MethodMatcher ASSERT_THAT = new MethodMatcher("org.hamcrest.MatcherAssert assertThat(..)");
    private static final MethodMatcher EQUAL_TO = new MethodMatcher("org.hamcrest.*Matchers equalTo(..)");
    private static final MethodMatcher IS = new MethodMatcher("org.hamcrest.*Matchers is(..)");
    private static final MethodMatcher NOT = new MethodMatcher("org.hamcrest.*Matchers not(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!migrates(mi)) {
                    return boxExpectedValue(mi, getCursor());
                }
                List<Expression> args = mi.getArguments();
                boolean negated = NOT.matches(args.get(args.size() - 1));
                Expression actual = args.get(args.size() - 2);
                Expression reason = args.size() == 3 ? args.get(0) : null;
                for (String type : new String[]{"org.hamcrest.Matchers", "org.hamcrest.CoreMatchers"}) {
                    for (String name : new String[]{"equalTo", "is", "not"}) {
                        maybeRemoveImport(type + "." + name);
                    }
                }
                maybeRemoveImport("org.hamcrest.MatcherAssert.assertThat");
                maybeRemoveImport("org.hamcrest.MatcherAssert");
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat");
                String code = "assertThat(#{any()})" + (reason == null ? "" : ".as(#{any(String)})") +
                              (negated ? ".isNotNaN()" : ".isNaN()");
                return JavaTemplate.builder(code)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(),
                                reason == null ? new Object[]{actual} : new Object[]{actual, reason});
            }
        });
    }

    /**
     * Boxes the expected value of {@code assertThat(aDouble, equalTo(expected))}, which Hamcrest boxes anyway, so that
     * the assertion it is migrated to calls {@code isEqualTo(Double)} rather than {@code isEqualTo(double)}.
     */
    private static J.MethodInvocation boxExpectedValue(J.MethodInvocation assertThat, Cursor cursor) {
        List<Expression> args = assertThat.getArguments();
        if (!ASSERT_THAT.matches(assertThat) || args.size() < 2 || !isFloatingPoint(args.get(args.size() - 2).getType())) {
            return assertThat;
        }
        J.MethodInvocation matcher = equalityMatcher(args.get(args.size() - 1));
        if (matcher == null) {
            return assertThat;
        }
        Expression expected = matcher.getArguments().get(0);
        JavaType type = expected.getType();
        if (expected instanceof J.Literal ||
            (type != JavaType.Primitive.Double && type != JavaType.Primitive.Float)) {
            return assertThat;
        }
        String boxed = type == JavaType.Primitive.Double ? "Double" : "Float";
        Expression box = JavaTemplate.apply(boxed + ".valueOf(#{any(" + type + ")})",
                new Cursor(cursor, expected), expected.getCoordinates().replace(), expected);
        J.MethodInvocation boxedMatcher = matcher.withArguments(ListUtils.map(matcher.getArguments(), (i, a) -> i == 0 ? box : a));
        return (J.MethodInvocation) new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                return method == matcher ? boxedMatcher : super.visitMethodInvocation(method, p);
            }
        }.visitNonNull(assertThat, 0);
    }

    /**
     * The {@code equalTo(x)} or {@code is(x)} of a value, also in {@code not(..)} or {@code is(equalTo(x))}.
     */
    private static J.@Nullable MethodInvocation equalityMatcher(Expression matcher) {
        if (NOT.matches(matcher) || IS.matches(matcher) && isMatcherCall(((J.MethodInvocation) matcher).getArguments().get(0))) {
            return equalityMatcher(((J.MethodInvocation) matcher).getArguments().get(0));
        }
        return EQUAL_TO.matches(matcher) || IS.matches(matcher) ? (J.MethodInvocation) matcher : null;
    }

    private static boolean isMatcherCall(Expression expression) {
        return EQUAL_TO.matches(expression) || IS.matches(expression) || NOT.matches(expression);
    }

    private static boolean isFloatingPoint(@Nullable JavaType type) {
        return type == JavaType.Primitive.Double || type == JavaType.Primitive.Float ||
               TypeUtils.isOfClassType(type, "java.lang.Double") || TypeUtils.isOfClassType(type, "java.lang.Float");
    }

    /**
     * Whether the Hamcrest assertion asserts that its value is or isn't {@code NaN}, which this recipe migrates.
     */
    static boolean migrates(J.MethodInvocation assertThat) {
        List<Expression> args = assertThat.getArguments();
        if (!ASSERT_THAT.matches(assertThat) || args.size() < 2) {
            return false;
        }
        Expression matcher = args.get(args.size() - 1);
        if (NOT.matches(matcher)) {
            matcher = ((J.MethodInvocation) matcher).getArguments().get(0);
        }
        return isEqualToNaN(matcher);
    }

    private static boolean isEqualToNaN(Expression matcher) {
        if (!EQUAL_TO.matches(matcher) && !IS.matches(matcher)) {
            return false;
        }
        Expression expected = ((J.MethodInvocation) matcher).getArguments().get(0);
        if (EQUAL_TO.matches(expected) || IS.matches(expected)) {
            return isEqualToNaN(expected);
        }
        return isNaN(expected);
    }

    private static boolean isNaN(Expression expression) {
        JavaType.Variable field = fieldType(expression);
        return field != null && "NaN".equals(field.getName()) &&
               (TypeUtils.isOfClassType(field.getOwner(), "java.lang.Float") ||
                TypeUtils.isOfClassType(field.getOwner(), "java.lang.Double"));
    }

    private static JavaType.@Nullable Variable fieldType(Expression expression) {
        if (expression instanceof J.Identifier) {
            return ((J.Identifier) expression).getFieldType();
        }
        if (expression instanceof J.FieldAccess) {
            return ((J.FieldAccess) expression).getName().getFieldType();
        }
        return null;
    }
}
