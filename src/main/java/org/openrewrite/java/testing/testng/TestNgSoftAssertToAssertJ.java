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
package org.openrewrite.java.testing.testng;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class TestNgSoftAssertToAssertJ extends Recipe {

    @Getter
    final String displayName = "TestNG `SoftAssert` to AssertJ `SoftAssertions`";

    @Getter
    final String description = "Convert TestNG-style soft assertions (`org.testng.asserts.SoftAssert`) to " +
            "AssertJ soft assertions (`org.assertj.core.api.SoftAssertions`).";

    private static final String SELECT = "#{any(org.assertj.core.api.SoftAssertions)}.assertThat(#{any()})";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.testng.asserts.SoftAssert", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                // Dispatch on any instance call whose receiver is a SoftAssert: the assert* methods are inherited from
                // `Assertion`, while `assertAll` is declared on `SoftAssert`. Unrecognized methods fall through unchanged.
                Expression select = method.getSelect();
                if (select == null || !TypeUtils.isAssignableTo("org.testng.asserts.SoftAssert", select.getType())) {
                    return method;
                }

                List<Expression> args = method.getArguments();
                switch (method.getSimpleName()) {
                    case "assertTrue":
                        return unary(method, ctx, select, args, "isTrue()");
                    case "assertFalse":
                        return unary(method, ctx, select, args, "isFalse()");
                    case "assertNull":
                        return unary(method, ctx, select, args, "isNull()");
                    case "assertNotNull":
                        return unary(method, ctx, select, args, "isNotNull()");
                    case "assertSame":
                        return binary(method, ctx, select, args, "isSameAs(#{any()})");
                    case "assertNotSame":
                        return binary(method, ctx, select, args, "isNotSameAs(#{any()})");
                    case "assertEquals":
                        return equals(method, ctx, select, args, "isEqualTo(#{any()})", "isCloseTo(#{any()}, within(#{any()}))");
                    case "assertNotEquals":
                        return equals(method, ctx, select, args, "isNotEqualTo(#{any()})", "isNotCloseTo(#{any()}, within(#{any()}))");
                    case "assertEqualsNoOrder":
                        return binary(method, ctx, select, args, "containsExactlyInAnyOrder(#{any()})");
                    case "assertAll":
                        // AssertJ has no assertAll(String); drop the TestNG message. Plain assertAll() is left to ChangeType.
                        if (args.isEmpty()) {
                            return method;
                        }
                        return apply(method, ctx, "#{any(org.assertj.core.api.SoftAssertions)}.assertAll()", false, select);
                    default:
                        // e.g. fail(...): same method name in AssertJ, left to the ChangeType recipe
                        return method;
                }
            }

            /**
             * Handles single-subject predicates: {@code assertTrue/assertFalse/assertNull/assertNotNull(subject[, message])}.
             */
            private J.MethodInvocation unary(J.MethodInvocation method, ExecutionContext ctx, Expression select,
                                             List<Expression> args, String terminal) {
                Expression subject = args.get(0);
                if (args.size() == 1) {
                    return apply(method, ctx, SELECT + "." + terminal, false, select, subject);
                }
                return apply(method, ctx, SELECT + ".as(#{any(String)})." + terminal, false, select, subject, args.get(1));
            }

            /**
             * Handles two-subject assertions without a delta overload: {@code assertSame/assertNotSame(actual, expected[, message])}.
             */
            private J.MethodInvocation binary(J.MethodInvocation method, ExecutionContext ctx, Expression select,
                                              List<Expression> args, String terminal) {
                Expression actual = args.get(0);
                Expression expected = args.get(1);
                if (args.size() == 2) {
                    return apply(method, ctx, SELECT + "." + terminal, false, select, actual, expected);
                }
                return apply(method, ctx, SELECT + ".as(#{any(String)})." + terminal, false, select, actual, args.get(2), expected);
            }

            /**
             * Handles {@code assertEquals/assertNotEquals(actual, expected[, delta][, message])}, where a floating-point
             * delta maps to {@code isCloseTo(..., within(...))}. Note TestNG passes {@code actual} first, {@code expected} second.
             */
            private J.MethodInvocation equals(J.MethodInvocation method, ExecutionContext ctx, Expression select,
                                              List<Expression> args, String terminal, String closeToTerminal) {
                Expression actual = args.get(0);
                Expression expected = args.get(1);
                if (args.size() == 2) {
                    return apply(method, ctx, SELECT + "." + terminal, false, select, actual, expected);
                }
                if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                    return apply(method, ctx, SELECT + ".as(#{any(String)})." + terminal, false, select, actual, args.get(2), expected);
                }
                if (args.size() == 3) {
                    return apply(method, ctx, SELECT + "." + closeToTerminal, true, select, actual, expected, args.get(2));
                }
                // 4 args: (actual, expected, delta, message)
                return apply(method, ctx, SELECT + ".as(#{any(String)})." + closeToTerminal, true, select, actual, args.get(3), expected, args.get(2));
            }

            private J.MethodInvocation apply(J.MethodInvocation method, ExecutionContext ctx, String template,
                                             boolean usesWithin, Object... params) {
                JavaTemplate.Builder builder = JavaTemplate.builder(template)
                        .contextSensitive()
                        .imports("java.util.function.Supplier")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"));
                if (usesWithin) {
                    builder.staticImports("org.assertj.core.api.Assertions.within");
                    maybeAddImport("org.assertj.core.api.Assertions", "within", false);
                }
                return builder.build().apply(getCursor(), method.getCoordinates().replace(), params);
            }

            private boolean isFloatingPointType(Expression expression) {
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(expression.getType());
                if (fullyQualified != null) {
                    String typeName = fullyQualified.getFullyQualifiedName();
                    return "java.lang.Double".equals(typeName) || "java.lang.Float".equals(typeName);
                }

                JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
                return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
            }
        });
    }
}
