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

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockWhiteboxSetInternalStateToJavaReflection extends Recipe {

    private static final MethodMatcher SET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.setInternalState()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.setInternalState(Object, String, Object)` with `java.lang.reflect.Field` " +
            "access. The field lookup uses `getDeclaredField` on the target object's class, which differs from " +
            "PowerMock's class-hierarchy traversal for fields inherited from a superclass.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SetInternalStateVisitor().withPrecondition();
    }

    private static class SetInternalStateVisitor extends WhiteboxToReflectionVisitor {

        SetInternalStateVisitor() {
            super("java.lang.reflect.Field", SET_INTERNAL_STATE);
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            String fieldName = extractStringLiteral(mi.getArguments().get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            return fieldLookupPrefix(varName) +
                    varName + ".set(#{any(java.lang.Object)}, #{any(java.lang.Object)});";
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            // target, fieldName, target, value
            return new Object[]{args.get(0), args.get(1), args.get(0), args.get(2)};
        }
    }
}
