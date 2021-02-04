/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.testing.junitassertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

/**
 * This is a refactoring visitor that will convert JUnit-style fail() to assertJ's fail().
 *
 * This visitor only supports the migration of the following JUnit 5 fail() methods:
 *
 * <PRE>
 *  fail()                                  ->   fail("")
 *  fail(String message)                    ->   fail(String message)
 *  fail(String message, Throwable cause)   ->   fail(String message, Throwable cause)
 *  fail(Throwable cause)                   ->   fail("", Throwable cause)
 * </PRE>
 *
 * Note: There is an additional method signature in JUnit that accepts a StringSupplier as an argument. Attempts
 *  to map this signature into assertJ's model obfuscates the original assertion.
 */
public class JUnitFailToAssertJFail extends Recipe {

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JUnitFailToAssertJFailVisitor();
    }

    public static class JUnitFailToAssertJFailVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";
        private static final String ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.assertj.core.api.Assertions";

        /**
         * This matcher finds the junit methods that will be migrated by this visitor.
         */
        private static final MethodMatcher JUNIT_FAIL_MATCHER = new MethodMatcher(
                JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " fail(..)"
        );

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation original = method;
            if (!JUNIT_FAIL_MATCHER.matches(method)) {
                return original;
            }

            MethodTypeBuilder methodTypeBuilder = newMethodType()
                    .declaringClass(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME)
                    .flags(Flag.Public, Flag.Static)
                    .returnType(
                            JavaType.Class.build("java.lang.Object"),
                            new JavaType.GenericTypeVariable(
                                    "T",
                                    JavaType.Class.build("java.lang.Object")
                            )
                    )
                    .name("fail");

            List<Expression> originalArgs = original.getArgs();
            List<Expression> newArgs = new ArrayList<>();

            if(originalArgs.get(0) instanceof J.Literal) {
                newArgs.add(originalArgs.get(0));
                methodTypeBuilder.parameter(JavaType.Class.build("java.lang.String"), "failureMessage");

                if(originalArgs.size() == 2) {
                    newArgs.add(originalArgs.get(1));
                }
            } else if(originalArgs.get(0) instanceof J.Empty) {
                newArgs.add(new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, "", "\"\"", JavaType.Primitive.String));
            } else {
                methodTypeBuilder.parameter(JavaType.Class.build("java.lang.String"), "failureMessage");
                methodTypeBuilder.parameter("java.lang.Throwable", "realCause");
                newArgs.add(new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, "", "\"\"", JavaType.Primitive.String));
                newArgs.add(originalArgs.get(0));
            }

            JavaType.Method assertJFailMethodType = methodTypeBuilder.build();

            J.MethodInvocation assertJFail = new J.MethodInvocation(
                    randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    null,
                    null,
                    J.Ident.build(randomId(), "fail", JavaType.Primitive.Void),
                    JContainer.build(
                            newArgs.stream().map(JRightPadded::build).collect(Collectors.toList())
                    ),
                    assertJFailMethodType
            );

            maybeAddImport(ASSERTJ_QUALIFIED_ASSERTIONS_CLASS_NAME, "fail");
            doAfterVisit(new RemoveUnusedImports());
            return (J.MethodInvocation) new AutoFormatVisitor<ExecutionContext>().visit(assertJFail, ctx, getCursor());
        }
    }
}
