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
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockWhiteboxInvokeMethodToJavaReflection extends Recipe {

    private static final MethodMatcher INVOKE_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Object, java.lang.String, ..)");
    // The overload for a static method, which names the class rather than an instance
    private static final MethodMatcher INVOKE_STATIC_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Class, java.lang.String, ..)");

    @Getter
    final String displayName = "Replace PowerMock `Whitebox.invokeMethod()` with Java reflection";

    @Getter
    final String description = "Replace `Whitebox.invokeMethod(Object, String, ..)` and its overload for static methods with " +
            "`java.lang.reflect.Method` lookup and `invoke()`. The method is looked up on the class declaring the " +
            "resolved target method, which also covers Mockito spies and mocks, and its parameter types are taken from " +
            "that method, falling back to the target's runtime class and each argument's compile-time class. The " +
            "arguments of a variable arity method are passed as the array it takes. A call nested in a " +
            "larger expression is replaced by `method.invoke(target, ..)`, with the `Method` declared before the " +
            "enclosing statement.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new InvokeMethodVisitor().withPrecondition();
    }

    static class InvokeMethodVisitor extends WhiteboxToReflectionVisitor {

        InvokeMethodVisitor() {
            super("java.lang.reflect.Method", INVOKE_METHOD, INVOKE_STATIC_METHOD);
        }

        private static boolean isStaticOverload(J.MethodInvocation mi) {
            return INVOKE_STATIC_METHOD.matches(mi);
        }

        /// The class a static call names, by a class literal or by the type argument of a `Class<Type>` expression.
        private JavaType.@Nullable FullyQualified staticTarget(J.MethodInvocation mi) {
            if (!isStaticOverload(mi)) {
                return null;
            }
            Expression target = mi.getArguments().get(0);
            JavaType.FullyQualified literal = classLiteralType(target);
            if (literal != null) {
                return literal;
            }
            JavaType.Parameterized parameterized = TypeUtils.asParameterized(target.getType());
            if (parameterized != null && TypeUtils.isOfClassType(parameterized, "java.lang.Class") &&
                parameterized.getTypeParameters().size() == 1) {
                return TypeUtils.asFullyQualified(parameterized.getTypeParameters().get(0));
            }
            return null;
        }

        @Override
        JavaType.@Nullable Method resolve(J.MethodInvocation mi) {
            return resolveTargetMethod(mi);
        }

        @Override
        JavaType.@Nullable FullyQualified lookupOwner(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            if (resolvedMethod != null) {
                JavaType.FullyQualified owner = resolvedMethod.getDeclaringType();
                return isAccessible(owner) ? owner : null;
            }
            JavaType.FullyQualified staticTarget = staticTarget(mi);
            return staticTarget != null && isAccessible(staticTarget) ? staticTarget : null;
        }

        @Override
        @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                       JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            String methodName = extractStringLiteral(args.get(1));
            if (methodName == null) {
                return null;
            }
            boolean isStatic = isStaticOverload(mi);
            JavaType.FullyQualified lookupOwner = lookupOwner(mi, resolvedMethod);
            if (isStatic && lookupOwner == null) {
                return null;
            }
            List<String> parameterTypes = new ArrayList<>();
            if (resolvedMethod != null) {
                for (JavaType parameterType : resolvedMethod.getParameterTypes()) {
                    String classLiteral = classLiteralFromType(parameterType);
                    if (classLiteral == null) {
                        return null;
                    }
                    parameterTypes.add(classLiteral);
                }
            } else {
                for (int i = 2; i < args.size(); i++) {
                    String classLiteral = classLiteralFromType(args.get(i).getType());
                    parameterTypes.add(classLiteral != null ? classLiteral : "#{any(java.lang.Object)}.getClass()");
                }
            }
            String varName = generateVariableName(methodName + "Method", scope, INCREMENT_NUMBER);
            StringBuilder sb = new StringBuilder(usesHierarchyLookup(lookupOwner) ?
                    hierarchyMethodLookupPrefix(varName, parameterTypes) :
                    methodLookupPrefix(varName, lookupReceiverTemplate(lookupOwner), parameterTypes));
            if (sink.varName != null) {
                if (isNonObjectCast(sink.castType)) {
                    sb.append(sink.castType).append(" ").append(sink.varName).append(" = (").append(boxedCastType(sink.castType)).append(") ");
                } else {
                    sb.append("Object ").append(sink.varName).append(" = ");
                }
            }
            return sb.append(invocation(varName, isStatic, mi, resolvedMethod)).append(";").toString();
        }

        @Override
        @Nullable Hoisted hoist(J.MethodInvocation mi, Cursor scope) {
            JavaType.Method resolvedMethod = resolve(mi);
            JavaType.FullyQualified owner = lookupOwner(mi, resolvedMethod);
            if (resolvedMethod == null || owner == null) {
                return null;
            }
            List<String> parameterTypes = new ArrayList<>();
            for (JavaType parameterType : resolvedMethod.getParameterTypes()) {
                String literal = classLiteralFromType(parameterType);
                if (literal == null) {
                    return null;
                }
                parameterTypes.add(literal);
            }
            String varName = methodVarName(mi.getArguments().get(1), scope);
            boolean isStatic = isStaticOverload(mi);
            return new Hoisted(mi, varName, methodLookupPrefix(varName, lookupReceiverTemplate(owner), parameterTypes),
                    castPrefix(mi) + invocation("#{any(java.lang.reflect.Method)}", isStatic, mi, resolvedMethod), 2, !isStatic);
        }

        private String methodLookupPrefix(String varName, String receiver, List<String> parameterTypes) {
            StringBuilder sb = new StringBuilder("Method ").append(varName).append(" = ").append(receiver)
                    .append(".getDeclaredMethod(#{any(java.lang.String)}");
            for (String parameterType : parameterTypes) {
                sb.append(", ").append(parameterType);
            }
            return sb.append(");\n").append(varName).append(".setAccessible(true);\n").toString();
        }

        /// `method.invoke(<target>, <arguments>)`, with the trailing arguments of a variable arity method gathered
        /// into the array it takes. A static method takes no target.
        private String invocation(String method, boolean isStatic, J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            StringBuilder sb = new StringBuilder(method).append(".invoke(").append(isStatic ? "null" : "#{any(java.lang.Object)}");
            int argCount = args.size() - 2;
            int fixed = packsVarargs(resolvedMethod, args) ? resolvedMethod.getParameterTypes().size() - 1 : argCount;
            for (int i = 0; i < fixed; i++) {
                sb.append(", #{any(java.lang.Object)}");
            }
            if (fixed < argCount || packsVarargs(resolvedMethod, args)) {
                JavaType.Array varargs = (JavaType.Array) resolvedMethod.getParameterTypes().get(resolvedMethod.getParameterTypes().size() - 1);
                sb.append(", (Object) new ").append(arrayElementTypeName(varargs)).append("[]{");
                for (int i = fixed; i < argCount; i++) {
                    sb.append(i == fixed ? "" : ", ").append("#{any(java.lang.Object)}");
                }
                sb.append("}");
            }
            return sb.append(")").toString();
        }

        /// A variable arity method called with the elements, rather than with the array, as the arguments
        private boolean packsVarargs(JavaType.@Nullable Method resolvedMethod, List<Expression> args) {
            if (resolvedMethod == null || !resolvedMethod.hasFlags(Flag.Varargs)) {
                return false;
            }
            int parameterCount = resolvedMethod.getParameterTypes().size();
            int argCount = args.size() - 2;
            if (argCount != parameterCount) {
                return true;
            }
            JavaType last = args.get(args.size() - 1).getType();
            return !(last instanceof JavaType.Array || last == JavaType.Primitive.Null);
        }

        private String arrayElementTypeName(JavaType.Array array) {
            String literal = classLiteralFromType(array.getElemType());
            return literal == null ? "Object" : literal.substring(0, literal.length() - ".class".length());
        }

        @Override
        boolean fallsBackToRuntimeClass(J.MethodInvocation mi) {
            return !isStaticOverload(mi) && lookupOwner(mi, resolve(mi)) == null;
        }

        // `Whitebox` searches the hierarchy, so a lookup on the target's runtime class goes through the
        // helper this visitor adds to the test class instead of `getDeclaredMethod` directly.
        private String hierarchyMethodLookupPrefix(String varName, List<String> parameterTypes) {
            recordHierarchyLookup();
            StringBuilder sb = new StringBuilder("Method ").append(varName).append(" = ")
                    .append(METHOD_LOOKUP_HELPER)
                    .append("(#{any(java.lang.Object)}.getClass(), #{any(java.lang.String)}");
            for (String parameterType : parameterTypes) {
                sb.append(", ").append(parameterType);
            }
            return sb.append(");\n").append(varName).append(".setAccessible(true);\n").toString();
        }

        @Override
        Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();
            boolean isStatic = isStaticOverload(mi);
            List<Object> result = new ArrayList<>();
            result.add(lookupReceiverArg(args.get(0), lookupOwner(mi, resolvedMethod)));
            result.add(args.get(1)); // methodName
            if (resolvedMethod == null) {
                for (int i = 2; i < args.size(); i++) {
                    if (classLiteralFromType(args.get(i).getType()) == null) {
                        result.add(args.get(i)); // arg.getClass() fallback for getDeclaredMethod
                    }
                }
            }
            if (!isStatic) {
                result.add(args.get(0)); // target for invoke
            }
            for (int i = 2; i < args.size(); i++) {
                result.add(args.get(i)); // arg for invoke
            }
            return result.toArray();
        }

        /**
         * Resolve the target method from the target's type and the method name, walking the supertype chain.
         * The arguments pick between overloads and select a variable arity method, and the call is left alone
         * where the method cannot be unambiguously resolved (not found, overloaded, or missing type information).
         */
        private JavaType.@Nullable Method resolveTargetMethod(J.MethodInvocation mi) {
            List<Expression> args = mi.getArguments();
            String methodName = extractStringLiteral(args.get(1));
            if (methodName == null) {
                return null;
            }
            JavaType targetType = isStaticOverload(mi) ? staticTarget(mi) : args.get(0).getType();
            if (!(targetType instanceof JavaType.FullyQualified)) {
                return null;
            }
            List<JavaType.Method> candidates = new ArrayList<>();
            for (JavaType.FullyQualified current = (JavaType.FullyQualified) targetType;
                 current != null; current = current.getSupertype()) {
                for (JavaType.Method method : current.getMethods()) {
                    if (method.getName().equals(methodName) && accepts(method, args)) {
                        candidates.add(method);
                    }
                }
            }
            if (candidates.size() > 1) {
                // A subclass overriding the method hides the one it overrides
                List<JavaType.Method> distinct = new ArrayList<>();
                for (JavaType.Method candidate : candidates) {
                    boolean overridden = false;
                    for (JavaType.Method other : distinct) {
                        overridden |= sameParameters(other, candidate);
                    }
                    if (!overridden) {
                        distinct.add(candidate);
                    }
                }
                candidates = distinct;
            }
            return candidates.size() == 1 ? candidates.get(0) : null;
        }

        private boolean sameParameters(JavaType.Method a, JavaType.Method b) {
            if (a.getParameterTypes().size() != b.getParameterTypes().size()) {
                return false;
            }
            for (int i = 0; i < a.getParameterTypes().size(); i++) {
                if (!TypeUtils.isOfType(a.getParameterTypes().get(i), b.getParameterTypes().get(i))) {
                    return false;
                }
            }
            return true;
        }

        /// Whether the arguments of the `invokeMethod` call, from its third on, can be passed to the method
        private boolean accepts(JavaType.Method method, List<Expression> args) {
            int argCount = args.size() - 2;
            List<JavaType> parameters = method.getParameterTypes();
            boolean varargs = method.hasFlags(Flag.Varargs);
            if (argCount != parameters.size() && !(varargs && argCount >= parameters.size() - 1)) {
                return false;
            }
            for (int i = 0; i < argCount; i++) {
                boolean spread = varargs && i >= parameters.size() - 1;
                JavaType parameter = spread ? ((JavaType.Array) parameters.get(parameters.size() - 1)).getElemType() : parameters.get(i);
                JavaType argument = args.get(i + 2).getType();
                if (!isPassable(parameter, argument) && !(spread && argCount == parameters.size() && isPassable(parameters.get(i), argument))) {
                    return false;
                }
            }
            return true;
        }

        /// Only rules out an argument whose class is certainly not one the parameter takes, so a type that is not
        /// fully resolved, or a generic one, does not stop the call from being migrated.
        private boolean isPassable(JavaType parameter, @Nullable JavaType argument) {
            JavaType.FullyQualified parameterClass = erasedClass(parameter);
            JavaType.FullyQualified argumentClass = erasedClass(argument);
            if (parameterClass == null || argumentClass == null) {
                return true;
            }
            return TypeUtils.isAssignableTo(parameterClass.getFullyQualifiedName(), argumentClass);
        }

        private JavaType.@Nullable FullyQualified erasedClass(@Nullable JavaType type) {
            if (type instanceof JavaType.Parameterized) {
                return ((JavaType.Parameterized) type).getType();
            }
            return type instanceof JavaType.FullyQualified && !(type instanceof JavaType.Unknown) ? (JavaType.FullyQualified) type : null;
        }

        /// The class literal for a parameter type, erased where it is generic.
        private @Nullable String classLiteralFromType(@Nullable JavaType type) {
            if (type instanceof JavaType.Primitive) {
                return ((JavaType.Primitive) type).getKeyword() + ".class";
            }
            if (type instanceof JavaType.Array) {
                String element = classLiteralFromType(((JavaType.Array) type).getElemType());
                return element == null ? null : element.substring(0, element.length() - ".class".length()) + "[].class";
            }
            if (type instanceof JavaType.GenericTypeVariable) {
                List<JavaType> bounds = ((JavaType.GenericTypeVariable) type).getBounds();
                return bounds.isEmpty() ? "Object.class" : classLiteralFromType(bounds.get(0));
            }
            if (type instanceof JavaType.Parameterized) {
                return classLiteralFromType(((JavaType.Parameterized) type).getType());
            }
            if (type instanceof JavaType.FullyQualified) {
                return ((JavaType.FullyQualified) type).getClassName() + ".class";
            }
            return null;
        }
    }
}
