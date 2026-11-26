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
package org.openrewrite.java.testing.awaitility;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class AwaitilityUntilAsserted extends Recipe {

    private static final MethodMatcher UNTIL = new MethodMatcher("org.awaitility.core.ConditionFactory until(java.util.concurrent.Callable)");
    private static final String ASSERT_THAT = "org.assertj.core.api.Assertions";

    @Getter
    final String displayName = "Use `untilAsserted` with AssertJ instead of `until` with a boolean condition";

    @Getter
    final String description = "Replace Awaitility `until(() -> condition)` with `untilAsserted(() -> assertThat(...))` " +
            "so that a timeout reports the actual and expected values rather than just that the condition was not fulfilled. " +
            "Handles `equals`, `==` and `!=` on primitives or `null`, `size() == n`, `isEmpty()` and `contains(...)`, " +
            "and only applies when AssertJ is on the classpath.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(UNTIL), new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (m.getSelect() == null || !UNTIL.matches(m) || !(m.getArguments().get(0) instanceof J.Lambda)) {
                    return m;
                }
                J.Lambda lambda = (J.Lambda) m.getArguments().get(0);
                if (!isParameterless(lambda) || !(lambda.getBody() instanceof Expression)) {
                    return m;
                }
                Assertion assertion = toAssertion(unwrap((Expression) lambda.getBody()));
                if (assertion == null || !assertJAvailable()) {
                    return m;
                }
                List<Object> params = new ArrayList<>();
                params.add(m.getSelect());
                params.addAll(assertion.arguments);
                maybeAddImport(ASSERT_THAT, "assertThat");
                return JavaTemplate.builder("#{any(org.awaitility.core.ConditionFactory)}.untilAsserted(() -> assertThat(" + assertion.template)
                        .staticImports(ASSERT_THAT + ".assertThat")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "awaitility-4", "hamcrest", "assertj-core-3"))
                        .build()
                        .apply(updateCursor(m), m.getCoordinates().replace(), params.toArray());
            }

            private boolean assertJAvailable() {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu == null) {
                    return false;
                }
                JavaSourceSet sourceSet = cu.getMarkers().findFirst(JavaSourceSet.class).orElse(null);
                return sourceSet != null && sourceSet.getClasspath().stream()
                        .anyMatch(type -> ASSERT_THAT.equals(type.getFullyQualifiedName()));
            }
        });
    }

    private static boolean isParameterless(J.Lambda lambda) {
        List<?> params = lambda.getParameters().getParameters();
        return params.isEmpty() || (params.size() == 1 && params.get(0) instanceof J.Empty);
    }

    private static Expression unwrap(Expression e) {
        while (e instanceof J.Parentheses && ((J.Parentheses<?>) e).getTree() instanceof Expression) {
            e = (Expression) ((J.Parentheses<?>) e).getTree();
        }
        return e;
    }

    private static final class Assertion {
        final String template;
        final List<Object> arguments;

        Assertion(String template, Object... arguments) {
            this.template = template;
            this.arguments = java.util.Arrays.asList(arguments);
        }
    }

    private static @Nullable Assertion assertion(@Nullable Expression actual, String call, Expression... args) {
        String placeholder = placeholder(actual);
        if (placeholder == null) {
            return null;
        }
        List<Object> arguments = new ArrayList<>();
        arguments.add(actual);
        arguments.addAll(java.util.Arrays.asList(args));
        return new Assertion(placeholder + ")." + call + ")", arguments.toArray());
    }

    private static @Nullable Assertion toAssertion(Expression e) {
        if (e instanceof J.Unary && ((J.Unary) e).getOperator() == J.Unary.Type.Not) {
            Expression operand = unwrap(((J.Unary) e).getExpression());
            if (operand instanceof J.MethodInvocation) {
                J.MethodInvocation call = (J.MethodInvocation) operand;
                if (isEmptyCall(call)) {
                    return assertion(call.getSelect(), "isNotEmpty()");
                }
                if (isContainsCall(call)) {
                    return assertion(call.getSelect(), "doesNotContain(#{any(java.lang.Object)})", call.getArguments().get(0));
                }
            }
            return null;
        }
        if (e instanceof J.MethodInvocation) {
            J.MethodInvocation call = (J.MethodInvocation) e;
            if (isEmptyCall(call)) {
                return assertion(call.getSelect(), "isEmpty()");
            }
            if (isContainsCall(call)) {
                return assertion(call.getSelect(), "contains(#{any(java.lang.Object)})", call.getArguments().get(0));
            }
            if ("equals".equals(call.getSimpleName()) && call.getArguments().size() == 1 && call.getSelect() != null) {
                return assertion(call.getSelect(), "isEqualTo(#{any(java.lang.Object)})", call.getArguments().get(0));
            }
            return null;
        }
        if (e instanceof J.Binary) {
            J.Binary b = (J.Binary) e;
            boolean eq = b.getOperator() == J.Binary.Type.Equal;
            if (!eq && b.getOperator() != J.Binary.Type.NotEqual) {
                return null;
            }
            Expression left = unwrap(b.getLeft());
            Expression right = unwrap(b.getRight());
            if (right instanceof J.Literal && ((J.Literal) right).getType() == JavaType.Primitive.Null) {
                return left.getType() instanceof JavaType.Primitive ? null : assertion(left, eq ? "isNull()" : "isNotNull()");
            }
            if (eq && left instanceof J.MethodInvocation && "size".equals(((J.MethodInvocation) left).getSimpleName()) &&
                    ((J.MethodInvocation) left).getArguments().get(0) instanceof J.Empty &&
                    ((J.MethodInvocation) left).getSelect() != null && isSized(((J.MethodInvocation) left).getSelect()) &&
                    right.getType() == JavaType.Primitive.Int) {
                return assertion(((J.MethodInvocation) left).getSelect(), "hasSize(#{any(int)})", right);
            }
            if (isPrimitive(left) && isPrimitive(right) &&
                    (left.getType() == JavaType.Primitive.Boolean) == (right.getType() == JavaType.Primitive.Boolean)) {
                String rightType = ((JavaType.Primitive) right.getType()).getKeyword();
                return assertion(left, (eq ? "isEqualTo" : "isNotEqualTo") + "(#{any(" + rightType + ")})", right);
            }
        }
        return null;
    }

    private static boolean isPrimitive(Expression e) {
        return e.getType() instanceof JavaType.Primitive && e.getType() != JavaType.Primitive.Null &&
                e.getType() != JavaType.Primitive.Void && e.getType() != JavaType.Primitive.String;
    }

    private static boolean isEmptyCall(J.MethodInvocation call) {
        return "isEmpty".equals(call.getSimpleName()) && call.getSelect() != null && call.getArguments().get(0) instanceof J.Empty &&
                (isSized(call.getSelect()) || TypeUtils.isAssignableTo("java.lang.CharSequence", call.getSelect().getType()));
    }

    private static boolean isContainsCall(J.MethodInvocation call) {
        return "contains".equals(call.getSimpleName()) && call.getSelect() != null && call.getArguments().size() == 1 &&
                TypeUtils.isAssignableTo("java.util.Collection", call.getSelect().getType());
    }

    private static boolean isSized(Expression e) {
        return TypeUtils.isAssignableTo("java.util.Collection", e.getType()) || TypeUtils.isAssignableTo("java.util.Map", e.getType());
    }

    /** A typed template placeholder for the actual value, or null when its type can't be named. */
    private static @Nullable String placeholder(@Nullable Expression e) {
        if (e == null) {
            return null;
        }
        JavaType type = e.getType();
        if (type instanceof JavaType.Primitive && type != JavaType.Primitive.Null && type != JavaType.Primitive.Void) {
            return "#{any(" + ((JavaType.Primitive) type).getKeyword() + ")}";
        }
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? null : "#{any(" + fq.getFullyQualifiedName() + ")}";
    }
}
