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
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class TestNgAssertionToAssertJ extends Recipe {

    @Getter
    final String displayName = "TestNG `Assertion` to AssertJ";

    @Getter
    final String description = "Convert TestNG-style hard assertions on `org.testng.asserts.Assertion` instances to " +
            "static AssertJ `assertThat(...)`, removing the now-unused local `Assertion` instance.";

    private static final String ASSERTION = "org.testng.asserts.Assertion";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ASSERTION, false), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                Expression select = method.getSelect();
                if (select == null || !TypeUtils.isOfClassType(select.getType(), ASSERTION)) {
                    return method;
                }
                List<Expression> args = method.getArguments();
                switch (method.getSimpleName()) {
                    case "assertTrue":
                        return unary(method, ctx, args, "isTrue()");
                    case "assertFalse":
                        return unary(method, ctx, args, "isFalse()");
                    case "assertNull":
                        return unary(method, ctx, args, "isNull()");
                    case "assertNotNull":
                        return unary(method, ctx, args, "isNotNull()");
                    case "assertSame":
                        return binary(method, ctx, args, "isSameAs(#{any()})");
                    case "assertNotSame":
                        return binary(method, ctx, args, "isNotSameAs(#{any()})");
                    case "assertEqualsNoOrder":
                        return binary(method, ctx, args, "containsExactlyInAnyOrder(#{any()})");
                    case "assertEquals":
                        return equals(method, ctx, args, "isEqualTo(#{any()})", "isCloseTo(#{any()}, within(#{any()}))");
                    case "assertNotEquals":
                        return equals(method, ctx, args, "isNotEqualTo(#{any()})", "isNotCloseTo(#{any()}, within(#{any()}))");
                    case "fail":
                        return fail(method, ctx, args);
                    default:
                        return method;
                }
            }

            private J.MethodInvocation equals(J.MethodInvocation method, ExecutionContext ctx, List<Expression> args,
                                              String terminal, String closeTo) {
                Expression actual = args.get(0);
                Expression expected = args.get(1);
                if (args.size() == 2) {
                    return apply(method, ctx, "assertThat(#{any()})." + terminal, false, actual, expected);
                }
                if (args.size() == 3 && !TestNgAsserts.isFloatingPointType(args.get(2))) {
                    return apply(method, ctx, "assertThat(#{any()}).as(#{any(String)})." + terminal, false, actual, args.get(2), expected);
                }
                if (args.size() == 3) {
                    return apply(method, ctx, "assertThat(#{any()})." + closeTo, true, actual, expected, args.get(2));
                }
                // 4 args: (actual, expected, delta, message)
                return apply(method, ctx, "assertThat(#{any()}).as(#{any(String)})." + closeTo, true, actual, args.get(3), expected, args.get(2));
            }

            private J.MethodInvocation apply(J.MethodInvocation method, ExecutionContext ctx, String template,
                                             boolean usesWithin, Object... params) {
                maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);
                JavaTemplate.Builder builder = JavaTemplate.builder(template)
                        .contextSensitive()
                        .imports("java.util.function.Supplier")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"));
                if (usesWithin) {
                    maybeAddImport("org.assertj.core.api.Assertions", "within", false);
                    builder = builder.staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within");
                } else {
                    builder = builder.staticImports("org.assertj.core.api.Assertions.assertThat");
                }
                return builder.build().apply(getCursor(), method.getCoordinates().replace(), params);
            }

            private J.MethodInvocation unary(J.MethodInvocation method, ExecutionContext ctx, List<Expression> args, String terminal) {
                if (args.size() == 1) {
                    return apply(method, ctx, "assertThat(#{any()})." + terminal, false, args.get(0));
                }
                return apply(method, ctx, "assertThat(#{any()}).as(#{any(String)})." + terminal, false, args.get(0), args.get(1));
            }

            private J.MethodInvocation binary(J.MethodInvocation method, ExecutionContext ctx, List<Expression> args, String terminal) {
                Expression actual = args.get(0);
                Expression expected = args.get(1);
                if (args.size() == 2) {
                    return apply(method, ctx, "assertThat(#{any()})." + terminal, false, actual, expected);
                }
                return apply(method, ctx, "assertThat(#{any()}).as(#{any(String)})." + terminal, false, actual, args.get(2), expected);
            }

            private J.MethodInvocation fail(J.MethodInvocation method, ExecutionContext ctx, List<Expression> args) {
                maybeAddImport("org.assertj.core.api.Assertions", "fail", false);
                // A no-arg `fail()` call carries a single J.Empty argument (not an empty list). AssertJ's no-arg
                // static fail() does not resolve through the template classpath, so emit an empty message, matching
                // JUnitFailToAssertJFail's convention for the message-less case.
                String template;
                Object[] params;
                if (args.get(0) instanceof J.Empty) {
                    template = "fail(\"\")";
                    params = new Object[0];
                } else if (args.size() == 1) {
                    template = "fail(#{any(String)})";
                    params = args.toArray();
                } else {
                    template = "fail(#{any(String)}, #{any(java.lang.Throwable)})";
                    params = args.toArray();
                }
                return JavaTemplate.builder(template)
                        .contextSensitive()
                        .staticImports("org.assertj.core.api.Assertions.fail")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "assertj-core-3"))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), params);
            }
        });
    }
}
