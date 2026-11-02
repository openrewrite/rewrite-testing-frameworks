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
import org.openrewrite.java.tree.*;

import java.util.List;

public class PowerMockWhiteboxGetFieldToJavaReflection extends Recipe {

    private static final MethodMatcher GET_FIELD =
            new MethodMatcher("org.powermock.reflect.Whitebox getField(java.lang.Class, java.lang.String)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.getField()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.getField(Class, String)` with `Class.getDeclaredField(String)` " +
            "plus `setAccessible(true)`. The field is looked up on the class declaring it, found through the class " +
            "argument and its superclasses; when that class cannot be determined, the class hierarchy is searched " +
            "at runtime, as PowerMock does. A call nested in a larger expression, such as " +
            "`getField(Type.class, \"name\").set(target, value)`, is replaced by the `Field`, declared before the " +
            "enclosing statement.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new GetFieldVisitor(GET_FIELD).withPrecondition();
    }

    /**
     * Migrates a call that takes a class and a field name and returns that field, made accessible, as both
     * {@code Whitebox.getField(Class, String)} and {@code MemberMatcher.field(Class, String)}, which delegates to
     * it, do.
     */
    static class GetFieldVisitor extends WhiteboxToReflectionVisitor {

        private static final MethodMatcher GET_CLASS = new MethodMatcher("java.lang.Object getClass()");

        GetFieldVisitor(MethodMatcher matcher) {
            super("java.lang.reflect.Field", matcher);
        }

        @Override
        String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                             JavaType.@Nullable Method resolvedMethod) {
            String varName = resultLocalName(sink, mi.getArguments().get(1), scope, true);
            return declaration(varName, lookupOwner(mi, resolvedMethod));
        }

        private String declaration(String varName, JavaType.@Nullable FullyQualified owner) {
            String lookup;
            if (owner == null) {
                recordHierarchyLookup();
                lookup = FIELD_LOOKUP_HELPER + "(#{any(java.lang.Class)}, #{any(java.lang.String)})";
            } else {
                lookup = "#{any(java.lang.Class)}.getDeclaredField(#{any(java.lang.String)})";
            }
            return "Field " + varName + " = " + lookup + ";\n" + varName + ".setAccessible(true);";
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            JavaType.FullyQualified owner = lookupOwner(mi, resolvedMethod);
            // Keeps a class literal as written when it names the declaring class already
            Object lookupClass = owner == null || TypeUtils.isOfType(owner, classLiteralType(args.get(0))) ?
                    args.get(0) : classLiteral(owner);
            return new Object[]{lookupClass, args.get(1)};
        }

        @Override
        Object[] hoistedDeclarationArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            return buildArgs(mi, resolvedMethod);
        }

        /**
         * The class declaring the field, found through the class argument, {@code Type.class} or
         * {@code target.getClass()}, and its superclasses.
         */
        @Override
        JavaType.@Nullable FullyQualified lookupOwner(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            String fieldName = extractStringLiteral(mi.getArguments().get(1));
            JavaType.FullyQualified searched = searchedClass(mi.getArguments().get(0));
            if (fieldName == null || searched == null) {
                return null;
            }
            for (JavaType.FullyQualified type = searched; type != null; type = type.getSupertype()) {
                for (JavaType.Variable member : type.getMembers()) {
                    if (member.getName().equals(fieldName)) {
                        return isAccessible(type) ? type : null;
                    }
                }
            }
            return null;
        }

        private JavaType.@Nullable FullyQualified searchedClass(Expression classArgument) {
            JavaType.FullyQualified literal = classLiteralType(classArgument);
            if (literal != null) {
                return literal;
            }
            if (GET_CLASS.matches(classArgument) && ((J.MethodInvocation) classArgument).getSelect() != null) {
                return TypeUtils.asFullyQualified(((J.MethodInvocation) classArgument).getSelect().getType());
            }
            return null;
        }

        @Override
        boolean fallsBackToRuntimeClass(J.MethodInvocation mi) {
            return lookupOwner(mi, null) == null;
        }

        /**
         * Moving the lookup in front of the statement is safe once it is known to find the field. Otherwise it
         * may throw, so it is only moved when the statement evaluates the call before anything else, as in
         * {@code field(target.getClass(), "name").set(target, value)}.
         */
        @Override
        @Nullable Hoisted hoist(J.MethodInvocation mi, Cursor scope) {
            JavaType.FullyQualified owner = lookupOwner(mi, null);
            if (owner == null && !isEvaluatedFirstInStatement(mi, scope.getValue())) {
                return null;
            }
            String varName = fieldVarName(mi.getArguments().get(1), scope);
            return new Hoisted(mi, varName, declaration(varName, owner) + "\n", "#{any(java.lang.reflect.Field)}");
        }

        @Override
        Object[] hoistedExpressionArgs(J.MethodInvocation mi, J.Identifier declared) {
            return new Object[]{declared};
        }

        private static boolean isEvaluatedFirstInStatement(J.MethodInvocation mi, J.Block block) {
            for (Statement statement : block.getStatements()) {
                Expression first = null;
                if (statement instanceof J.MethodInvocation) {
                    first = (Expression) statement;
                } else if (statement instanceof J.VariableDeclarations &&
                           ((J.VariableDeclarations) statement).getVariables().size() == 1) {
                    first = ((J.VariableDeclarations) statement).getVariables().get(0).getInitializer();
                } else if (statement instanceof J.Return) {
                    first = ((J.Return) statement).getExpression();
                }
                while (first instanceof J.MethodInvocation) {
                    if (first.getId().equals(mi.getId())) {
                        return true;
                    }
                    first = ((J.MethodInvocation) first).getSelect();
                }
            }
            return false;
        }
    }
}
