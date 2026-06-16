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

public class PowerMockWhiteboxGetInternalStateToJavaReflection extends Recipe {

    private static final MethodMatcher GET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox getInternalState(java.lang.Object, java.lang.String)");
    private static final MethodMatcher GET_INTERNAL_STATE_WHERE =
            new MethodMatcher("org.powermock.reflect.Whitebox getInternalState(java.lang.Object, java.lang.String, java.lang.Class)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.getInternalState()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.getInternalState(Object, String)` and the `Class where` overload " +
            "with `java.lang.reflect.Field` access, casting to the declared result type where needed. The field " +
            "lookup uses `getDeclaredField` on the target object's class (or the `where` class), which differs " +
            "from PowerMock's class-hierarchy traversal for fields inherited from a superclass.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new GetInternalStateVisitor().withPrecondition();
    }

    private static class GetInternalStateVisitor extends WhiteboxToReflectionVisitor {

        GetInternalStateVisitor() {
            super("java.lang.reflect.Field", GET_INTERNAL_STATE, GET_INTERNAL_STATE_WHERE);
        }

        @Override
        String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                             JavaType.@Nullable Method resolvedMethod) {
            String varName = fieldVarName(mi.getArguments().get(1), scope);
            String receiver = GET_INTERNAL_STATE_WHERE.matches(mi) ? "#{any(java.lang.Class)}" : "#{any(java.lang.Object)}.getClass()";
            return fieldLookupPrefix(varName, receiver) + fieldGetTail(varName, sink);
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            if (GET_INTERNAL_STATE_WHERE.matches(mi)) {
                // where, fieldName, target
                return new Object[]{args.get(2), args.get(1), args.get(0)};
            }
            // target, fieldName, target
            return new Object[]{args.get(0), args.get(1), args.get(0)};
        }

        /**
         * Build the trailing {@code Field.get(...)} statement, casting to the result type when the
         * result is stored in a variable.
         */
        private String fieldGetTail(String varName, ResultSink sink) {
            if (sink.varName != null) {
                if (isNonObjectCast(sink.castType)) {
                    return sink.castType + " " + sink.varName + " = (" + boxedCastType(sink.castType) + ") " + varName + ".get(#{any(java.lang.Object)});";
                }
                return "Object " + sink.varName + " = " + varName + ".get(#{any(java.lang.Object)});";
            }
            return varName + ".get(#{any(java.lang.Object)});";
        }
    }
}
