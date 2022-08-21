/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.function.Supplier;

public class AssertTrueComparisonToAssertEquals extends Recipe {
    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    @Override
    public String getDisplayName() {
        return "Junit `assertTrue(a == b)` to `assertEquals(a,b)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertEquals(a,b)` is simpler and more clear.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(ASSERT_TRUE);
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        Supplier<JavaParser> javaParser = () -> JavaParser.fromJavaVersion()
                //language=java
                .dependsOn("" +
                        "package org.junit.jupiter.api;" +
                        "public class Assertions {" +
                        "public static void assertEquals(Object expected,Object actual) {}" +
                        "}")
                .build();

        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate assertEquals = JavaTemplate.builder(this::getCursor, "assertEquals(#{any(java.lang.Object)},#{any(java.lang.Object)})")
                    .staticImports("org.junit.jupiter.api.Assertions.assertEquals")
                    .javaParser(javaParser)
                    .build();

            private final JavaTemplate assertEqualsNoStaticImport = JavaTemplate.builder(this::getCursor, "Assertions.assertEquals(#{any(java.lang.Object)},#{any(java.lang.Object)})")
                    .imports("org.junit.jupiter.api.Assertions")
                    .javaParser(javaParser)
                    .build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (ASSERT_TRUE.matches(method) && isEqualBinary(method)) {

                    J.Binary binary = (J.Binary) method.getArguments().get(0);

                    if (method.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertEquals");
                        return method.withTemplate(assertEquals, method.getCoordinates().replace(),binary.getLeft(),binary.getRight());
                    } else {
                        return method.withTemplate(assertEqualsNoStaticImport, method.getCoordinates().replace(),binary.getLeft(),binary.getRight());
                    }

                }
                return super.visitMethodInvocation(method, ctx);
            }

            private boolean isEqualBinary(J.MethodInvocation method) {

                if (method.getArguments().isEmpty()) {
                    return false;
                }

                final Expression firstArgument = method.getArguments().get(0);
                if (!(firstArgument instanceof J.Binary)) {
                    return false;
                }

                J.Binary binary = (J.Binary) firstArgument;
                J.Binary.Type operator = binary.getOperator();
                return operator.equals(J.Binary.Type.Equal);
            }
        };
    }
}
