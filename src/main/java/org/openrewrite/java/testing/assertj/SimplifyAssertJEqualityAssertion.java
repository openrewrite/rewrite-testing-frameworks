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
import org.openrewrite.java.tree.JavaType.Primitive;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

/**
 * Routing a primitive comparison to `isEqualTo` rather than `isSameAs` is what keeps the assertion meaningful:
 * `assertThat(long)` boxes its actual, so `isNotSameAs` would hold for any two equal values outside the `Long`
 * cache, and the assertion would stop verifying anything.
 */
public class SimplifyAssertJEqualityAssertion extends Recipe {

    private static final MethodMatcher ASSERT_THAT_MATCHER = new MethodMatcher("org.assertj.core.api.Assertions assertThat(..)");
    private static final MethodMatcher IS_TRUE_MATCHER = new MethodMatcher("org.assertj.core.api.* isTrue()");
    private static final MethodMatcher IS_FALSE_MATCHER = new MethodMatcher("org.assertj.core.api.* isFalse()");
    private static final MethodMatcher IS_EQUAL_TO_MATCHER = new MethodMatcher("org.assertj.core.api.* isEqualTo(..)");

    private static final Set<Primitive> VALUE_TYPES = EnumSet.of(Primitive.Boolean, Primitive.Char,
            Primitive.Byte, Primitive.Short, Primitive.Int, Primitive.Long);

    /** In widening order; `char` is absent because nothing widens into it, and it widens only into `int` and `long`. */
    private static final List<Primitive> WIDENING_ORDER =
            asList(Primitive.Byte, Primitive.Short, Primitive.Int, Primitive.Long);

    @Getter
    final String displayName = "Simplify AssertJ assertions on `==` and `!=` comparisons";

    @Getter
    final Set<String> tags = singleton("RSPEC-S5838");

    @Getter
    final String description = "Replace `assertThat(x == y).isTrue()` and its variants with the dedicated assertion " +
            "for whatever `==` actually compares: `assertThat(x).isNull()` against the `null` literal, " +
            "`assertThat(x).isEqualTo(y)` when either operand is a primitive and the comparison is therefore by " +
            "value, and `assertThat(x).isSameAs(y)` when both operands are reference types. Floating point operands " +
            "are left alone, as `==` and `isEqualTo` disagree on `NaN` and `-0.0`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_THAT_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!ASSERT_THAT_MATCHER.matches(mi.getSelect())) {
                    return mi;
                }
                // Match isTrue()/isFalse() and isEqualTo(true|false), so we fire before the Refaster boolean rules
                // in the same cycle, rather than after they have already produced an `isSameAs`.
                Boolean assertsTrue = booleanAssertion(mi);
                if (assertsTrue == null) {
                    return mi;
                }

                J.MethodInvocation assertThat = (J.MethodInvocation) mi.getSelect();
                Expression argument = assertThat.getArguments().get(0).unwrap();
                boolean expectTrue = assertsTrue;
                while (argument instanceof J.Unary && ((J.Unary) argument).getOperator() == J.Unary.Type.Not) {
                    expectTrue = !expectTrue;
                    argument = ((J.Unary) argument).getExpression().unwrap();
                }
                if (!(argument instanceof J.Binary)) {
                    return mi;
                }
                J.Binary binary = (J.Binary) argument;
                J.Binary.Type operator = binary.getOperator();
                if (operator != J.Binary.Type.Equal && operator != J.Binary.Type.NotEqual) {
                    return mi;
                }

                boolean expectEqual = (operator == J.Binary.Type.Equal) == expectTrue;
                Expression left = binary.getLeft();
                Expression right = binary.getRight();
                boolean leftIsNull = J.Literal.isLiteralValue(left, null);
                if (leftIsNull || J.Literal.isLiteralValue(right, null)) {
                    return dedicated(mi, assertThat, expectEqual ? "isNull" : "isNotNull", leftIsNull ? right : left, null, ctx);
                }
                if (comparesValues(left, right)) {
                    return dedicated(mi, assertThat, expectEqual ? "isEqualTo" : "isNotEqualTo", left, right, ctx);
                }
                if (isReferenceOperand(left) && isReferenceOperand(right)) {
                    return dedicated(mi, assertThat, expectEqual ? "isSameAs" : "isNotSameAs", left, right, ctx);
                }
                return mi;
            }

            private J.MethodInvocation dedicated(J.MethodInvocation mi, J.MethodInvocation assertThat,
                                                 String assertion, Expression actual, @Nullable Expression expected,
                                                 ExecutionContext ctx) {
                // Reuse the original `assertThat` select, so a static import or qualified call is preserved as it was
                J.MethodInvocation newAssertThat = assertThat.withArguments(singletonList(actual.withPrefix(Space.EMPTY)));
                JavaTemplate template = JavaTemplate
                        .builder("#{any()}." + assertion + (expected == null ? "()" : "(#{any()})"))
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build();
                return expected == null ?
                        template.apply(getCursor(), mi.getCoordinates().replace(), newAssertThat) :
                        template.apply(getCursor(), mi.getCoordinates().replace(), newAssertThat, expected.withPrefix(Space.EMPTY));
            }

            private @Nullable Boolean booleanAssertion(J.MethodInvocation mi) {
                if (IS_TRUE_MATCHER.matches(mi)) {
                    return true;
                }
                if (IS_FALSE_MATCHER.matches(mi)) {
                    return false;
                }
                if (IS_EQUAL_TO_MATCHER.matches(mi) && mi.getArguments().size() == 1 &&
                        mi.getArguments().get(0) instanceof J.Literal) {
                    Object value = ((J.Literal) mi.getArguments().get(0)).getValue();
                    return value instanceof Boolean ? (Boolean) value : null;
                }
                return null;
            }
        });
    }

    /** Whether `left == right` compares values, and `assertThat(left).isEqualTo(right)` compares them the same way. */
    private static boolean comparesValues(Expression left, Expression right) {
        Primitive actualType = valueType(left.getType());
        Primitive expectedType = valueType(right.getType());
        if (actualType == null || expectedType == null) {
            return false;
        }
        boolean leftIsPrimitive = left.getType() instanceof Primitive;
        boolean rightIsPrimitive = right.getType() instanceof Primitive;
        if (leftIsPrimitive && rightIsPrimitive) {
            return widensTo(expectedType, actualType);
        }
        // One boxed operand only reaches `isEqualTo(Object)`, which compares wrappers of the very same type;
        // two boxed operands really do compare by reference, so `isSameAs` applies instead
        return (leftIsPrimitive || rightIsPrimitive) && actualType == expectedType;
    }

    /**
     * Whether `==` on this operand compares references, such that `isSameAs` is a faithful replacement. Unresolved
     * types are rejected because we would be guessing, and `Float`/`Double` because `==` disagrees with `isSameAs`
     * on `NaN` and `-0.0` once unboxed.
     */
    private static boolean isReferenceOperand(Expression expression) {
        JavaType type = expression.getType();
        if (type == null || type instanceof JavaType.Unknown) {
            return false;
        }
        if (type instanceof Primitive) {
            // Of the primitives only string literals compare by reference; the rest compare by value
            return type == Primitive.String;
        }
        Primitive unboxed = unboxed(type);
        return unboxed != Primitive.Float && unboxed != Primitive.Double;
    }

    /**
     * The primitive an operand contributes to a value comparison, unboxing where needed, or `null` when the
     * operand is neither a value primitive nor its wrapper, and so compares by reference.
     */
    private static @Nullable Primitive valueType(@Nullable JavaType type) {
        Primitive primitive = type instanceof Primitive ? (Primitive) type : unboxed(type);
        return VALUE_TYPES.contains(primitive) ? primitive : null;
    }

    private static @Nullable Primitive unboxed(@Nullable JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? null : Primitive.fromClassName(fullyQualified.getFullyQualifiedName());
    }

    /**
     * Whether an argument of type `from` reaches an `isEqualTo(to)` overload through widening alone. AssertJ only
     * declares the overload matching the asserted type, next to `isEqualTo(Object)`; anything wider silently falls
     * through to the `Object` overload, which then compares mismatched wrappers and is never equal.
     */
    private static boolean widensTo(Primitive from, Primitive to) {
        if (from == to) {
            return true;
        }
        if (from == Primitive.Char) {
            return to == Primitive.Int || to == Primitive.Long;
        }
        int fromRank = WIDENING_ORDER.indexOf(from);
        return fromRank >= 0 && fromRank < WIDENING_ORDER.indexOf(to);
    }
}
