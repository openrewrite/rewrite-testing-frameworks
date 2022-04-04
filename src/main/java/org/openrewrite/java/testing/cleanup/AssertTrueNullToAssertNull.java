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

public class AssertTrueNullToAssertNull extends Recipe {
    private static final MethodMatcher ASSERT_TRUE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertTrue(..)");

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertTrue(a == null)` to `assertNull(a)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertNull(a)` is simpler and more clear.";
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
                        "  public static void assertNull(Object actual) {}" +
                        "}")
                .build();

        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate assertNull = JavaTemplate.builder(this::getCursor, "assertNull(#{any(java.lang.Object)})")
                    .staticImports("org.junit.jupiter.api.Assertions.assertNull")
                    .javaParser(javaParser)
                    .build();

            private final JavaTemplate assertNullNoStaticImport = JavaTemplate.builder(this::getCursor, "Assertions.assertNull(#{any(java.lang.Object)})")
                    .imports("org.junit.jupiter.api.Assertions")
                    .javaParser(javaParser)
                    .build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (ASSERT_TRUE.matches(method) && isEqualBinary(method)) {

                    J.Binary binary = (J.Binary) method.getArguments().get(0);

                    Expression nonNullExpression = getNonNullExpression(binary);

                    if (method.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertNull");
                        return method.withTemplate(assertNull, method.getCoordinates().replace(), nonNullExpression);
                    } else {
                        return method.withTemplate(assertNullNoStaticImport, method.getCoordinates().replace(), nonNullExpression);
                    }
                }
                return super.visitMethodInvocation(method, ctx);
            }


            private Expression getNonNullExpression(J.Binary binary) {

                if (binary.getRight() instanceof J.Literal){
                    boolean isNull = ((J.Literal) binary.getRight()).getValue() == null;
                    if (isNull){
                        return binary.getLeft();
                    }
                }

                return binary.getRight();
            }

            private boolean isEqualBinary(J.MethodInvocation method) {

                if (method.getArguments().isEmpty()) {
                    return false;
                }

                J.Binary binary = (J.Binary) method.getArguments().get(0);
                J.Binary.Type operator = binary.getOperator();
                return operator.equals(J.Binary.Type.Equal);


            }
        };
    }

}
