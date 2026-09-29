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

public class PowerMockWhiteboxGetInternalStateToJavaReflection extends Recipe {

    private static final MethodMatcher GET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox getInternalState(java.lang.Object, java.lang.String)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.getInternalState()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.getInternalState(Object, String)` with `java.lang.reflect.Field` " +
            "access, casting to the declared result type where needed. The field is looked up on the class " +
            "declaring it, found through the target's declared type and its superclasses, which also covers Mockito " +
            "spies and mocks; when that class cannot be determined, the target's runtime class is used. A call nested " +
            "in a larger expression is replaced by `field.get(target)`, with the `Field` declared before the " +
            "enclosing statement.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new GetInternalStateVisitor().withPrecondition();
    }

    private static class GetInternalStateVisitor extends WhiteboxToReflectionVisitor {

        GetInternalStateVisitor() {
            super("java.lang.reflect.Field", GET_INTERNAL_STATE);
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            String fieldName = extractStringLiteral(mi.getArguments().get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            String prefix = fieldLookupPrefix(varName, lookupOwner(mi, resolvedMethod));
            if (sink.varName != null) {
                if (isNonObjectCast(sink.castType)) {
                    return prefix + sink.castType + " " + sink.varName + " = (" + boxedCastType(sink.castType) + ") " + varName + ".get(#{any(java.lang.Object)});";
                }
                return prefix + "Object " + sink.varName + " = " + varName + ".get(#{any(java.lang.Object)});";
            }
            return prefix + varName + ".get(#{any(java.lang.Object)});";
        }

        @Override
        JavaType.@Nullable FullyQualified lookupOwner(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            return fieldOwner(mi.getArguments().get(0), extractStringLiteral(mi.getArguments().get(1)));
        }

        @Override
        @Nullable Hoisted hoist(J.MethodInvocation mi, Cursor scope) {
            JavaType.FullyQualified owner = lookupOwner(mi, null);
            if (owner == null) {
                return null;
            }
            String varName = fieldVarName(mi.getArguments().get(1), scope);
            return new Hoisted(mi, varName, fieldLookupPrefix(varName, owner),
                    castPrefix(mi) + "#{any(java.lang.reflect.Field)}.get(#{any(java.lang.Object)})");
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            return new Object[]{
                    lookupReceiverArg(args.get(0), lookupOwner(mi, resolvedMethod)),
                    args.get(1),
                    args.get(0)
            };
        }
    }
}
