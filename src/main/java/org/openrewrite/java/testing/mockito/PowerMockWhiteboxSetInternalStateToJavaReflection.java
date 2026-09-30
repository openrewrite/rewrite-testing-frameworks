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
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockWhiteboxSetInternalStateToJavaReflection extends Recipe {

    private static final MethodMatcher SET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object)");
    // `Whitebox` also identifies a field by its type rather than its name.
    private static final MethodMatcher SET_INTERNAL_STATE_BY_TYPE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.Class, java.lang.Object)");
    private static final MethodMatcher SET_INTERNAL_STATE_BY_TYPE_WHERE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.Class, java.lang.Object, java.lang.Class)");
    // An array value selects a distinct `Object[]` overload, which `Field.set` handles identically.
    private static final MethodMatcher SET_INTERNAL_STATE_ARRAY =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object[])");
    private static final MethodMatcher SET_INTERNAL_STATE_WHERE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object, java.lang.Class)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.setInternalState()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.setInternalState(Object, String, Object)` and " +
            "`Whitebox.setInternalState(Object, String, Object, Class)` (and their `Object[]` overloads) " +
            "with `java.lang.reflect.Field` access. " +
            "The 3-arg overload looks up the field on the class declaring it, found through the target's declared " +
            "type and its superclasses, falling back to the target's runtime class; the 4-arg where-overload uses " +
            "the supplied Class.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SetInternalStateVisitor().withPrecondition();
    }

    static class SetInternalStateVisitor extends WhiteboxToReflectionVisitor {

        SetInternalStateVisitor() {
            super("java.lang.reflect.Field", SET_INTERNAL_STATE, SET_INTERNAL_STATE_WHERE,
                    SET_INTERNAL_STATE_ARRAY,
                    SET_INTERNAL_STATE_BY_TYPE, SET_INTERNAL_STATE_BY_TYPE_WHERE);
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            if (isByType(mi)) {
                return byTypeTemplate(mi, scope);
            }
            String fieldName = extractStringLiteral(mi.getArguments().get(1));
            if (fieldName == null) {
                return null;
            }
            String varName = generateVariableName(fieldName + "Field", scope, INCREMENT_NUMBER);
            String prefix = mi.getArguments().size() == 4 ?
                    fieldLookupPrefixWhere(varName) :
                    fieldLookupPrefix(varName, lookupOwner(mi, resolvedMethod));
            return prefix +
                    varName + ".set(#{any(java.lang.Object)}, #{any(java.lang.Object)});";
        }

        /** True for the overloads that name the field by its type rather than by a string. */
        private boolean isByType(J.MethodInvocation mi) {
            return SET_INTERNAL_STATE_BY_TYPE.matches(mi) || SET_INTERNAL_STATE_BY_TYPE_WHERE.matches(mi);
        }

        /** The class whose field is being set: the `where` argument, a class literal target, or the target's type. */
        private JavaType.@Nullable FullyQualified byTypeOwner(J.MethodInvocation mi) {
            List<Expression> args = mi.getArguments();
            if (args.size() == 4) {
                return classLiteralType(args.get(3));
            }
            JavaType.FullyQualified staticTarget = classLiteralType(args.get(0));
            return staticTarget != null ? staticTarget : TypeUtils.asFullyQualified(args.get(0).getType());
        }

        private @Nullable String byTypeTemplate(J.MethodInvocation mi, Cursor scope) {
            JavaType.FullyQualified owner = byTypeOwner(mi);
            JavaType.Variable field = uniqueFieldOfType(owner, classLiteralType(mi.getArguments().get(1)));
            if (owner == null || field == null || !isAccessible(owner)) {
                return null;
            }
            String varName = generateVariableName(field.getName() + "Field", scope, INCREMENT_NUMBER);
            // A class literal target means a static field, which `Field.set` takes a null instance for.
            String instance = classLiteralType(mi.getArguments().get(0)) == null ?
                    "#{any(java.lang.Object)}" : "null";
            return fieldLookupPrefixNamed(varName, field.getName()) +
                    varName + ".set(" + instance + ", #{any(java.lang.Object)});";
        }

        @Override
        JavaType.@Nullable FullyQualified lookupOwner(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            if (isByType(mi)) {
                return byTypeOwner(mi);
            }
            return mi.getArguments().size() == 4 ? null :
                    fieldOwner(mi.getArguments().get(0), extractStringLiteral(mi.getArguments().get(1)));
        }

        @Override
        boolean fallsBackToRuntimeClass(J.MethodInvocation mi) {
            return !isByType(mi) && mi.getArguments().size() == 3 && lookupOwner(mi, null) == null;
        }

        @Override
        JavaType.@Nullable FullyQualified memberDeclaringType(J.MethodInvocation mi) {
            return fieldDeclaringType(mi.getArguments().get(0), extractStringLiteral(mi.getArguments().get(1)));
        }

        @Override
        boolean declaresMember(JavaType.FullyQualified type, J.MethodInvocation mi) {
            return declaresField(type, extractStringLiteral(mi.getArguments().get(1)));
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            if (isByType(mi)) {
                J.FieldAccess owner = classLiteral(requireNonNull(byTypeOwner(mi)));
                return classLiteralType(args.get(0)) == null ?
                        new Object[]{owner, args.get(0), args.get(2)} :
                        new Object[]{owner, args.get(2)};
            }
            if (args.size() == 4) {
                // whereClass, fieldName, target, value
                return new Object[]{args.get(3), args.get(1), args.get(0), args.get(2)};
            }
            return new Object[]{
                    lookupReceiverArg(args.get(0), lookupOwner(mi, resolvedMethod)),
                    args.get(1),
                    args.get(0),
                    args.get(2)
            };
        }
    }
}
