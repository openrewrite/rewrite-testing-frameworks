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
package org.openrewrite.java.testing.mockito;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;

public class PowerMockWhiteboxGetMethodToJavaReflection extends Recipe {

    private static final MethodMatcher GET_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox getMethod(java.lang.Class, java.lang.String, ..)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.getMethod()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.getMethod(Class, String, Class...)` with " +
            "`Class.getDeclaredMethod(String, Class...)` plus `setAccessible(true)`. Unlike PowerMock, " +
            "`getDeclaredMethod` does not traverse the class hierarchy; calls passing an explicit `Class[]` " +
            "array are left unchanged for manual migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new GetMethodVisitor().withPrecondition();
    }

    private static class GetMethodVisitor extends WhiteboxToReflectionVisitor {

        GetMethodVisitor() {
            super("java.lang.reflect.Method", GET_METHOD);
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            if (hasArrayArg(args, 2)) {
                // Explicit Class[] varargs array is not supported; leave for manual migration (flagged downstream)
                return null;
            }
            String varName = resultLocalName(sink, args.get(1), scope, false);
            StringBuilder sb = new StringBuilder("Method ").append(varName)
                    .append(" = #{any(java.lang.Class)}.getDeclaredMethod(#{any(java.lang.String)}");
            for (int i = 2; i < args.size(); i++) {
                sb.append(", #{any(java.lang.Class)}");
            }
            sb.append(");\n").append(varName).append(".setAccessible(true);");
            return sb.toString();
        }

        @Override
        JavaType.@Nullable FullyQualified lookupOwner(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            return classLiteralType(mi.getArguments().get(0));
        }

        /// The method of a call that is only used as the target of another, or returned from a lambda, such as
        /// `then(invocation -> getMethod(Type.class, "name"))`
        @Override
        @Nullable Hoisted hoist(J.MethodInvocation mi, Cursor scope) {
            List<Expression> args = mi.getArguments();
            if (lookupOwner(mi, null) == null || hasArrayArg(args, 2) || !args.stream().skip(2).allMatch(a -> classLiteralType(a) != null)) {
                return null;
            }
            String varName = methodVarName(args.get(1), scope);
            StringBuilder declaration = new StringBuilder("Method ").append(varName)
                    .append(" = #{any(java.lang.Class)}.getDeclaredMethod(#{any(java.lang.String)}");
            for (int i = 2; i < args.size(); i++) {
                declaration.append(", #{any(java.lang.Class)}");
            }
            declaration.append(");\n").append(varName).append(".setAccessible(true);\n");
            return new Hoisted(mi, varName, declaration.toString(), "#{any(java.lang.reflect.Method)}", args.size(), false,
                    new ArrayList<>(args.subList(2, args.size())));
        }

        @Override
        boolean hoistsOutOfExpressionLambda(J.MethodInvocation mi) {
            return hoist(mi, getCursor()) != null && extractStringLiteral(mi.getArguments().get(1)) != null;
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            // declaringClass, methodName, paramType0, paramType1, ...
            return mi.getArguments().toArray();
        }
    }
}
