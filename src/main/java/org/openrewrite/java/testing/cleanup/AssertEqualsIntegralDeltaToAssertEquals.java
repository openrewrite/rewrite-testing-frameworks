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
package org.openrewrite.java.testing.cleanup;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class AssertEqualsIntegralDeltaToAssertEquals extends Recipe {
    private static final MethodMatcher ASSERT_EQUALS = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertEquals(..)");

    @Getter
    final String displayName = "Remove unnecessary `assertEquals` delta argument for integral types";

    @Getter
    final String description = "Remove the delta argument from `assertEquals()` when both expected and actual are " +
            "`int` or `long` types, since the delta is meaningless for exact integer comparison. " +
            "Integer arguments get unnecessarily upcasted to `double` when a delta is provided.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(ASSERT_EQUALS), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (!ASSERT_EQUALS.matches(mi)) {
                    return mi;
                }

                List<Expression> args = mi.getArguments();
                if (args.size() == 3 && isIntegralType(args.get(0)) && isIntegralType(args.get(1)) && isNumericType(args.get(2))) {
                    // assertEquals(expected, actual, delta) -> assertEquals(expected, actual)
                    StringBuilder sb = new StringBuilder();
                    if (mi.getSelect() != null) {
                        sb.append("Assertions.");
                    }
                    sb.append("assertEquals(#{any()}, #{any()})");
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .staticImports("org.junit.jupiter.api.Assertions.assertEquals")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                .build();
                    } else {
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .imports("org.junit.jupiter.api.Assertions")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                .build();
                    }
                    return t.apply(updateCursor(mi), mi.getCoordinates().replace(), args.get(0), args.get(1));
                }

                if (args.size() == 4 && isIntegralType(args.get(0)) && isIntegralType(args.get(1)) && isNumericType(args.get(2))) {
                    // assertEquals(expected, actual, delta, message) -> assertEquals(expected, actual, message)
                    StringBuilder sb = new StringBuilder();
                    if (mi.getSelect() != null) {
                        sb.append("Assertions.");
                    }
                    sb.append("assertEquals(#{any()}, #{any()}, #{any()})");
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .staticImports("org.junit.jupiter.api.Assertions.assertEquals")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                .build();
                    } else {
                        t = JavaTemplate.builder(sb.toString())
                                .contextSensitive()
                                .imports("org.junit.jupiter.api.Assertions")
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-jupiter-api-5"))
                                .build();
                    }
                    return t.apply(updateCursor(mi), mi.getCoordinates().replace(), args.get(0), args.get(1), args.get(3));
                }

                return mi;
            }

            private boolean isIntegralType(Expression expression) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(expression.getType());
                if (fq != null) {
                    String typeName = fq.getFullyQualifiedName();
                    return "java.lang.Long".equals(typeName) || "java.lang.Integer".equals(typeName);
                }
                JavaType.Primitive p = TypeUtils.asPrimitive(expression.getType());
                return p == JavaType.Primitive.Long || p == JavaType.Primitive.Int;
            }

            private boolean isNumericType(Expression expression) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(expression.getType());
                if (fq != null) {
                    String typeName = fq.getFullyQualifiedName();
                    return "java.lang.Double".equals(typeName) || "java.lang.Float".equals(typeName) ||
                            "java.lang.Long".equals(typeName) || "java.lang.Integer".equals(typeName) ||
                            "java.lang.Short".equals(typeName) || "java.lang.Byte".equals(typeName);
                }
                JavaType.Primitive p = TypeUtils.asPrimitive(expression.getType());
                return p == JavaType.Primitive.Double || p == JavaType.Primitive.Float ||
                        p == JavaType.Primitive.Long || p == JavaType.Primitive.Int ||
                        p == JavaType.Primitive.Short || p == JavaType.Primitive.Byte;
            }
        });
    }
}
