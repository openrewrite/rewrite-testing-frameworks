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

public class PowerMockWhiteboxGetInnerClassTypeToJavaReflection extends Recipe {

    private static final MethodMatcher GET_INNER_CLASS_TYPE =
            new MethodMatcher("org.powermock.reflect.Whitebox getInnerClassType(java.lang.Class, java.lang.String)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.getInnerClassType()` with `Class.forName()`";

    @Getter
    final String description = "Replace `Whitebox.getInnerClassType(Class, String)` with `Class.forName()` of the binary " +
            "name of the inner class, which is its declaring class' name and its own, joined by a `$`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new GetInnerClassTypeVisitor().withPrecondition();
    }

    private static class GetInnerClassTypeVisitor extends WhiteboxToReflectionVisitor {

        GetInnerClassTypeVisitor() {
            super("java.lang.Class", GET_INNER_CLASS_TYPE);
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            String forName = "Class.forName(#{any(java.lang.Class)}.getName() + \"$\" + #{any(java.lang.String)});";
            if (sink.varName == null) {
                return forName;
            }
            return (sink.castType == null ? "Class<?>" : sink.castType) + " " + sink.varName + " = " + forName;
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            return new Object[]{args.get(0), args.get(1)};
        }
    }
}
