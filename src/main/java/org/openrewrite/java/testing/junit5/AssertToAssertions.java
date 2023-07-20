/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.testing.junit5;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AssertToAssertions extends Recipe {

    @Override
    public String getDisplayName() {
        return "JUnit 4 `Assert` To JUnit Jupiter `Assertions`";
    }

    @Override
    public String getDescription() {
        return "Change JUnit 4's `org.junit.Assert` into JUnit Jupiter's `org.junit.jupiter.api.Assertions`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.junit.Assert", false), new AssertToAssertionsVisitor());
    }

    public static class AssertToAssertionsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final JavaType ASSERTION_TYPE = JavaType.buildType("org.junit.Assert");

        private static final List<String> JUNIT_ASSERT_METHOD_NAMES = Arrays.asList(
                "assertArrayEquals", "assertEquals", "assertFalse", "assertNotEquals", "assertNotNull", "assertNotSame",
                "assertNull", "assertSame", "assertThrows", "assertTrue", "fail");

        @Override
        public @Nullable J preVisit(J tree, ExecutionContext ctx) {
            if (tree instanceof JavaSourceFile) {
                JavaSourceFile c = (JavaSourceFile) tree;
                boolean hasWildcardAssertImport = false;
                for (J.Import imp : c.getImports()) {
                    if ("org.junit.Assert.*".equals(imp.getQualid().toString())) {
                        hasWildcardAssertImport = true;
                        break;
                    }
                }
                if (hasWildcardAssertImport) {
                    maybeAddImport("org.junit.jupiter.api.Assertions", "*", false);
                    maybeRemoveImport("org.junit.Assert.*");
                }
            }
            return tree;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            if (!isJunitAssertMethod(m)) {
                return m;
            }
            doAfterVisit(new ChangeMethodTargetToStatic("org.junit.Assert " + m.getSimpleName() + "(..)",
                    "org.junit.jupiter.api.Assertions", null, null, true)
                    .getVisitor());

            if (isAssertEqualsWithArrayArguments(m)) {
                // rename so we maintain the expected behaviour
                m = m.withName(m.getName().withSimpleName("assertArrayEquals"));
            }

            List<Expression> args = m.getArguments();
            Expression firstArg = args.get(0);
            // Suppress arg-switching for Assertions.assertEquals(String, String)
            if (args.size() == 2) {
                if ("assertSame".equals(m.getSimpleName()) ||
                        "assertNotSame".equals(m.getSimpleName()) ||
                        "assertEquals".equals(m.getSimpleName()) ||
                        "assertNotEquals".equals(m.getSimpleName())) {
                    return m;
                }
            }

            if (TypeUtils.isString(firstArg.getType())) {
                // Move the first arg to be the last argument

                List<Expression> newArgs = new ArrayList<>(args.size());
                for (int i = 1; i < args.size(); i++) {
                    if (i == 1) {
                        newArgs.add(args.get(i).withPrefix(firstArg.getPrefix()));
                    } else {
                        newArgs.add(args.get(i));
                    }
                }
                newArgs.add(firstArg.withPrefix(args.get(args.size() - 1).getPrefix()));

                m = m.withArguments(newArgs);
            }

            return m;
        }

        private static boolean isAssertEqualsWithArrayArguments(J.MethodInvocation method) {
            if (!("assertEquals".equals(method.getSimpleName()))) {
                return false;
            }
            // Detection based on 2nd argument which is the same in both overloads with and without message
            Expression secondArg = method.getArguments().get(1);
            return secondArg.getType() instanceof JavaType.Array;
        }

        private static boolean isJunitAssertMethod(J.MethodInvocation method) {
            if (method.getMethodType() != null && TypeUtils.isAssignableTo(ASSERTION_TYPE, method.getMethodType().getDeclaringType())) {
                return !"assertThat".equals(method.getSimpleName());
            }
            if (method.getMethodType() == null && JUNIT_ASSERT_METHOD_NAMES.contains(method.getSimpleName())) {
                return true;
            }
            if (!(method.getSelect() instanceof J.Identifier)) {
                return false;
            }
            J.Identifier receiver = (J.Identifier) method.getSelect();
            if (!(receiver.getType() instanceof JavaType.FullyQualified)) {
                return false;
            }
            JavaType.FullyQualified receiverType = (JavaType.FullyQualified) receiver.getType();
            return "org.junit.Assert".equals(receiverType.getFullyQualifiedName());
        }
    }
}
