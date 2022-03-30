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

public class AssertEqualsNullToAssertNull extends Recipe {
    private static final MethodMatcher ASSERT_EQUALS = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertEquals(..)");

    @Override
    public String getDisplayName() {
        return "`assertEquals(a, null)` to `assertNull(a)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertNull(a)` is simpler and more clear.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(ASSERT_EQUALS);
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
                if (ASSERT_EQUALS.matches(method)) {
                    if (isNullLiteral(method.getArguments().get(0))) {
                        if(method.getSelect() == null) {
                            maybeRemoveImport("org.junit.jupiter.api.Assertions");
                            maybeAddImport("org.junit.jupiter.api.Assertions", "assertNull");
                            return method.withTemplate(assertNull, method.getCoordinates().replace(), method.getArguments().get(1));
                        } else {
                            return method.withTemplate(assertNullNoStaticImport, method.getCoordinates().replace(), method.getArguments().get(1));
                        }
                    } else if (isNullLiteral(method.getArguments().get(1))) {
                        if(method.getSelect() == null) {
                            maybeRemoveImport("org.junit.jupiter.api.Assertions");
                            maybeAddImport("org.junit.jupiter.api.Assertions", "assertNull");
                            return method.withTemplate(assertNull, method.getCoordinates().replace(), method.getArguments().get(0));
                        } else {
                            return method.withTemplate(assertNullNoStaticImport, method.getCoordinates().replace(), method.getArguments().get(0));
                        }
                    }
                }
                return super.visitMethodInvocation(method, ctx);
            }

            private boolean isNullLiteral(Expression expr) {
                return expr instanceof J.Literal && ((J.Literal) expr).getValue() == null;
            }
        };
    }
}
