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
    private static final MethodMatcher SET_INTERNAL_STATE_WHERE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object, java.lang.Class)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.setInternalState()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.setInternalState(Object, String, Object)` and " +
            "`Whitebox.setInternalState(Object, String, Object, Class)` with `java.lang.reflect.Field` access. " +
            "The 3-arg overload looks up the field on the target's class; the 4-arg where-overload uses the " +
            "supplied Class to resolve fields declared on a superclass.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SetInternalStateVisitor().withPrecondition();
    }

    private static class SetInternalStateVisitor extends WhiteboxToReflectionVisitor {

        SetInternalStateVisitor() {
            super("java.lang.reflect.Field", SET_INTERNAL_STATE, SET_INTERNAL_STATE_WHERE);
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            String fieldName = extractStringLiteral(mi.getArguments().get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            String prefix = mi.getArguments().size() == 4 ?
                    fieldLookupPrefixWhere(varName) :
                    fieldLookupPrefix(varName);
            return prefix +
                    varName + ".set(#{any(java.lang.Object)}, #{any(java.lang.Object)});";
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            if (args.size() == 4) {
                // whereClass, fieldName, target, value
                return new Object[]{args.get(3), args.get(1), args.get(0), args.get(2)};
            }
            // target, fieldName, target, value
            return new Object[]{args.get(0), args.get(1), args.get(0), args.get(2)};
        }
    }
}
