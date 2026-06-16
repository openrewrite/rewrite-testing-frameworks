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

import java.util.List;

public class PowerMockWhiteboxGetFieldToJavaReflection extends Recipe {

    private static final MethodMatcher GET_FIELD =
            new MethodMatcher("org.powermock.reflect.Whitebox getField(java.lang.Class, java.lang.String)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.getField()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.getField(Class, String)` with `Class.getDeclaredField(String)` " +
            "plus `setAccessible(true)`. Unlike PowerMock, `getDeclaredField` does not traverse the class " +
            "hierarchy for fields inherited from a superclass.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new GetFieldVisitor().withPrecondition();
    }

    private static class GetFieldVisitor extends WhiteboxToReflectionVisitor {

        GetFieldVisitor() {
            super("java.lang.reflect.Field", GET_FIELD);
        }

        @Override
        String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                             JavaType.@Nullable Method resolvedMethod) {
            String varName = resultLocalName(sink, mi.getArguments().get(1), scope, true);
            return "Field " + varName + " = #{any(java.lang.Class)}.getDeclaredField(#{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);";
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            // declaringClass, fieldName
            return new Object[]{args.get(0), args.get(1)};
        }
    }
}
