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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

public class PowerMockWhiteboxToJavaReflection extends Recipe {

    private static final String WHITEBOX_FQN = "org.powermock.reflect.Whitebox";
    private static final MethodMatcher SET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object)");
    private static final MethodMatcher GET_INTERNAL_STATE =
            new MethodMatcher("org.powermock.reflect.Whitebox getInternalState(java.lang.Object, java.lang.String)");
    private static final MethodMatcher INVOKE_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Object, java.lang.String, ..)");
    private static final MethodMatcher INVOKE_METHOD_STATIC =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeMethod(java.lang.Class, java.lang.String, ..)");
    private static final MethodMatcher SET_INTERNAL_STATE_WHERE =
            new MethodMatcher("org.powermock.reflect.Whitebox setInternalState(java.lang.Object, java.lang.String, java.lang.Object, java.lang.Class)");
    private static final MethodMatcher GET_INTERNAL_STATE_WHERE =
            new MethodMatcher("org.powermock.reflect.Whitebox getInternalState(java.lang.Object, java.lang.String, java.lang.Class)");
    private static final MethodMatcher GET_FIELD =
            new MethodMatcher("org.powermock.reflect.Whitebox getField(java.lang.Class, java.lang.String)");
    private static final MethodMatcher GET_METHOD =
            new MethodMatcher("org.powermock.reflect.Whitebox getMethod(java.lang.Class, java.lang.String, ..)");
    private static final MethodMatcher INVOKE_CONSTRUCTOR_ARGS =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeConstructor(java.lang.Class, java.lang.Object[])");
    private static final MethodMatcher INVOKE_CONSTRUCTOR_EXPLICIT =
            new MethodMatcher("org.powermock.reflect.Whitebox invokeConstructor(java.lang.Class, java.lang.Class[], java.lang.Object[])");

    private static final List<String> BASE_IMPORTS = Arrays.asList(
            "java.lang.reflect.Field", "java.lang.reflect.Method", "java.lang.reflect.Constructor");

    private static final Map<String, String> BOXED_TYPES = new HashMap<>();

    static {
        BOXED_TYPES.put("int", "Integer");
        BOXED_TYPES.put("long", "Long");
        BOXED_TYPES.put("double", "Double");
        BOXED_TYPES.put("float", "Float");
        BOXED_TYPES.put("boolean", "Boolean");
        BOXED_TYPES.put("byte", "Byte");
        BOXED_TYPES.put("short", "Short");
        BOXED_TYPES.put("char", "Character");
    }

    @Getter
    final String displayName = "Replace PowerMock `Whitebox` with Java reflection";

    @Getter
    final String description = "Replace `org.powermock.reflect.Whitebox` calls (`setInternalState`, " +
            "`getInternalState`, `invokeMethod`, static `invokeMethod`, `invokeConstructor`, `getField` and " +
            "`getMethod`, including the `Class where` overloads) with plain Java reflection using " +
            "`java.lang.reflect.Field`, `Method` and `Constructor`. Field/constructor lookups " +
            "use `getDeclaredField`/`getDeclaredConstructor` on the named class, which differs from PowerMock " +
            "for members inherited from a superclass.";

    /**
     * Where a result-producing reflection call stores its result. {@code varName} is the declared
     * variable receiving the value, or null when the result is discarded (the call was a bare
     * statement); {@code castType} is that variable's declared type.
     */
    private static final class ResultSink {
        private final @Nullable String varName;
        private final @Nullable String castType;

        private ResultSink(@Nullable String varName, @Nullable String castType) {
            this.varName = varName;
            this.castType = castType;
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(WHITEBOX_FQN, false),
                new JavaIsoVisitor<ExecutionContext>() {

        private static final String WHITEBOX_REPLACED = "whiteboxReplaced";
        private static final String NEEDS_FIELD_IMPORT = "needsFieldImport";
        private static final String NEEDS_METHOD_IMPORT = "needsMethodImport";
        private static final String NEEDS_CONSTRUCTOR_IMPORT = "needsConstructorImport";

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (getCursor().getMessage(WHITEBOX_REPLACED, false)) {
                md = addThrowsExceptionIfAbsent(md);
                maybeRemoveImport(WHITEBOX_FQN);
                if (getCursor().getMessage(NEEDS_FIELD_IMPORT, false)) {
                    maybeAddImport("java.lang.reflect.Field", false);
                }
                if (getCursor().getMessage(NEEDS_METHOD_IMPORT, false)) {
                    maybeAddImport("java.lang.reflect.Method", false);
                }
                if (getCursor().getMessage(NEEDS_CONSTRUCTOR_IMPORT, false)) {
                    maybeAddImport("java.lang.reflect.Constructor", false);
                }
                return maybeAutoFormat(method, md, ctx);
            }
            return md;
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);

            // Replace Whitebox calls that are themselves a statement or a single-variable declaration
            // initializer. Process in reverse so coordinates remain valid after each replacement.
            List<Statement> statements = b.getStatements();
            for (int i = statements.size() - 1; i >= 0; i--) {
                Statement stmt = statements.get(i);
                J.MethodInvocation mi = extractWhiteboxInvocation(stmt);
                if (mi == null) {
                    continue;
                }
                Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                JavaType.Method resolvedMethod = resolveFor(mi);
                String template = buildReplacementTemplate(mi, sinkFromStatement(stmt), blockCursor, resolvedMethod);
                if (template != null) {
                    b = JavaTemplate.builder(template)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion())
                            .imports(templateImports(resolvedMethod).toArray(new String[0]))
                            .build()
                            .apply(
                                    new Cursor(getCursor().getParentOrThrow(), b),
                                    stmt.getCoordinates().replace(),
                                    buildTemplateArgs(mi, resolvedMethod)
                            );
                    recordReplacement(mi, resolvedMethod);
                    // Re-read statements list since the block has been rebuilt
                    statements = b.getStatements();
                }
            }

            return b;
        }

        private JavaType.@Nullable Method resolveFor(J.MethodInvocation mi) {
            if (INVOKE_METHOD.matches(mi)) {
                return resolveTargetMethod(mi.getArguments());
            }
            if (INVOKE_METHOD_STATIC.matches(mi)) {
                return resolveStaticTargetMethod(mi.getArguments());
            }
            if (INVOKE_CONSTRUCTOR_ARGS.matches(mi)) {
                return resolveTargetConstructor(mi.getArguments());
            }
            return null;
        }

        private void recordReplacement(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, WHITEBOX_REPLACED, true);
            if (needsFieldImport(mi)) {
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_FIELD_IMPORT, true);
            }
            if (needsMethodImport(mi)) {
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_METHOD_IMPORT, true);
            }
            if (needsConstructorImport(mi)) {
                getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, NEEDS_CONSTRUCTOR_IMPORT, true);
            }
            for (String paramImport : resolvedParamImports(resolvedMethod)) {
                maybeAddImport(paramImport);
            }
        }

        private List<String> templateImports(JavaType.@Nullable Method resolvedMethod) {
            List<String> imports = new ArrayList<>(BASE_IMPORTS);
            imports.addAll(resolvedParamImports(resolvedMethod));
            return imports;
        }

        // Non-java.lang fully-qualified parameter types of the resolved method/constructor that the
        // generated class literals (e.g. `List.class`) need imported.
        private List<String> resolvedParamImports(JavaType.@Nullable Method resolvedMethod) {
            if (resolvedMethod == null) {
                return emptyList();
            }
            List<String> imports = new ArrayList<>();
            for (JavaType paramType : resolvedMethod.getParameterTypes()) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(paramType);
                if (fq != null && !"java.lang".equals(fq.getPackageName())) {
                    imports.add(fq.getFullyQualifiedName());
                }
            }
            return imports;
        }

        private ResultSink sinkFromStatement(Statement statement) {
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) statement;
                return new ResultSink(vd.getVariables().get(0).getSimpleName(), getCastType(vd.getType()));
            }
            return new ResultSink(null, null);
        }

        private @Nullable String buildReplacementTemplate(J.MethodInvocation mi, ResultSink sink,
                                                          Cursor scope, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();

            if (SET_INTERNAL_STATE.matches(mi) && args.size() == 3) {
                return buildSetInternalStateTemplate(args, scope);
            }
            if (SET_INTERNAL_STATE_WHERE.matches(mi) && args.size() == 4) {
                return buildSetInternalStateWhereTemplate(args, scope);
            }
            if (GET_INTERNAL_STATE.matches(mi) && args.size() == 2) {
                return buildGetInternalStateTemplate(args, sink, scope);
            }
            if (GET_INTERNAL_STATE_WHERE.matches(mi) && args.size() == 3) {
                return buildGetInternalStateWhereTemplate(args, sink, scope);
            }
            if (GET_FIELD.matches(mi) && args.size() == 2) {
                return buildGetFieldTemplate(args, sink, scope);
            }
            if (GET_METHOD.matches(mi) && args.size() >= 2) {
                return buildGetMethodTemplate(args, sink, scope);
            }
            if (INVOKE_METHOD.matches(mi) && args.size() >= 2) {
                return buildInvokeMethodTemplate(args, sink, scope, resolvedMethod, false);
            }
            if (INVOKE_METHOD_STATIC.matches(mi) && args.size() >= 2) {
                return buildInvokeMethodTemplate(args, sink, scope, resolvedMethod, true);
            }
            if (INVOKE_CONSTRUCTOR_EXPLICIT.matches(mi) && args.size() == 3) {
                return buildInvokeConstructorTemplate(args, sink, scope, null, true);
            }
            if (INVOKE_CONSTRUCTOR_ARGS.matches(mi)) {
                return buildInvokeConstructorTemplate(args, sink, scope, resolvedMethod, false);
            }
            return null;
        }

        private Object[] buildTemplateArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
            List<Expression> args = mi.getArguments();

            if (SET_INTERNAL_STATE.matches(mi) && args.size() == 3) {
                // target, fieldName, target, value
                return new Object[]{args.get(0), args.get(1), args.get(0), args.get(2)};
            }
            if (SET_INTERNAL_STATE_WHERE.matches(mi) && args.size() == 4) {
                // where, fieldName, target, value
                return new Object[]{args.get(3), args.get(1), args.get(0), args.get(2)};
            }
            if (GET_INTERNAL_STATE.matches(mi) && args.size() == 2) {
                // target, fieldName, target
                return new Object[]{args.get(0), args.get(1), args.get(0)};
            }
            if (GET_INTERNAL_STATE_WHERE.matches(mi) && args.size() == 3) {
                // where, fieldName, target
                return new Object[]{args.get(2), args.get(1), args.get(0)};
            }
            if (GET_FIELD.matches(mi) && args.size() == 2) {
                // declaringClass, fieldName
                return new Object[]{args.get(0), args.get(1)};
            }
            if (GET_METHOD.matches(mi) && args.size() >= 2) {
                // declaringClass, methodName, paramType0, paramType1, ...
                return args.toArray();
            }
            if (INVOKE_METHOD.matches(mi) && args.size() >= 2) {
                return buildInvokeMethodArgs(args, resolvedMethod, false);
            }
            if (INVOKE_METHOD_STATIC.matches(mi) && args.size() >= 2) {
                return buildInvokeMethodArgs(args, resolvedMethod, true);
            }
            if (INVOKE_CONSTRUCTOR_EXPLICIT.matches(mi) && args.size() == 3) {
                return buildInvokeConstructorArgs(args, null, true);
            }
            if (INVOKE_CONSTRUCTOR_ARGS.matches(mi)) {
                return buildInvokeConstructorArgs(args, resolvedMethod, false);
            }
            return new Object[0];
        }

        private String buildSetInternalStateTemplate(List<Expression> args, Cursor scope) {
            String varName = fieldVarName(args.get(1), scope);
            return fieldLookupPrefix(varName, "#{any(java.lang.Object)}.getClass()") +
                    varName + ".set(#{any(java.lang.Object)}, #{any(java.lang.Object)});";
        }

        private String buildGetInternalStateTemplate(List<Expression> args, ResultSink sink, Cursor scope) {
            String varName = fieldVarName(args.get(1), scope);
            return fieldLookupPrefix(varName, "#{any(java.lang.Object)}.getClass()") + fieldGetTail(varName, sink);
        }

        private String buildGetInternalStateWhereTemplate(List<Expression> args, ResultSink sink, Cursor scope) {
            String varName = fieldVarName(args.get(1), scope);
            return fieldLookupPrefix(varName, "#{any(java.lang.Class)}") + fieldGetTail(varName, sink);
        }

        private String buildSetInternalStateWhereTemplate(List<Expression> args, Cursor scope) {
            String varName = fieldVarName(args.get(1), scope);
            return fieldLookupPrefix(varName, "#{any(java.lang.Class)}") +
                    varName + ".set(#{any(java.lang.Object)}, #{any(java.lang.Object)});";
        }

        // `Field <var> = <receiver>.getDeclaredField(<name>); <var>.setAccessible(true);` — shared by the
        // instance (receiver `obj.getClass()`) and `Class where` (receiver the where-class) get/set variants.
        private String fieldLookupPrefix(String varName, String fieldReceiver) {
            return "Field " + varName + " = " + fieldReceiver + ".getDeclaredField(#{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);\n";
        }

        private String buildGetFieldTemplate(List<Expression> args, ResultSink sink, Cursor scope) {
            String varName = resultLocalName(sink, args.get(1), scope, true);
            return "Field " + varName + " = #{any(java.lang.Class)}.getDeclaredField(#{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);";
        }

        private @Nullable String buildGetMethodTemplate(List<Expression> args, ResultSink sink, Cursor scope) {
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

        /**
         * Build the trailing {@code Field.get(...)} statement for a {@code getInternalState} replacement,
         * casting to the result type when the result is stored in a variable.
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

        // True when castType denotes a meaningful type to cast to (i.e. not null and not Object).
        private boolean isNonObjectCast(@Nullable String castType) {
            return castType != null && !"Object".equals(castType) && !"java.lang.Object".equals(castType);
        }

        /**
         * For {@code getField}/{@code getMethod}, whose result type IS the reflective object, reuse the
         * result variable as the local; otherwise generate one.
         */
        private String resultLocalName(ResultSink sink, Expression nameExpr, Cursor scope, boolean field) {
            if (sink.varName != null) {
                return sink.varName;
            }
            return field ? fieldVarName(nameExpr, scope) : methodVarName(nameExpr, scope);
        }

        private boolean hasArrayArg(List<Expression> args, int fromIndex) {
            for (int i = fromIndex; i < args.size(); i++) {
                if (TypeUtils.asArray(args.get(i).getType()) != null) {
                    return true;
                }
            }
            return false;
        }

        private @Nullable String buildInvokeMethodTemplate(List<Expression> args, ResultSink sink,
                                                           Cursor scope, JavaType.@Nullable Method resolvedMethod, boolean isStatic) {
            if (hasArrayArg(args, 2)) {
                // Explicit `Class<?>[]` parameter-type overload (or an array passed as varargs) cannot be
                // mechanically expanded; leave for manual migration (flagged downstream).
                return null;
            }
            String varName = methodVarName(args.get(1), scope);
            // Static calls take a Class target; instance calls take an Object whose class we look up.
            String declaredMethodReceiver = isStatic ? "#{any(java.lang.Class)}" : "#{any(java.lang.Object)}.getClass()";
            String invokeTarget = isStatic ? "null" : "#{any(java.lang.Object)}";

            // getDeclaredMethod line
            StringBuilder sb = new StringBuilder();
            sb.append("Method ").append(varName).append(" = ").append(declaredMethodReceiver)
                    .append(".getDeclaredMethod(#{any(java.lang.String)}");
            for (int i = 2; i < args.size(); i++) {
                String classLiteral = getParamClassLiteral(args, i, resolvedMethod);
                if (classLiteral != null) {
                    sb.append(", ").append(classLiteral);
                } else {
                    sb.append(", #{any(java.lang.Object)}.getClass()");
                }
            }
            sb.append(");\n");

            // setAccessible line
            sb.append(varName).append(".setAccessible(true);\n");

            // invoke line
            if (sink.varName != null) {
                if (isNonObjectCast(sink.castType)) {
                    sb.append(sink.castType).append(" ").append(sink.varName).append(" = (").append(boxedCastType(sink.castType)).append(") ");
                } else {
                    sb.append("Object ").append(sink.varName).append(" = ");
                }
            }
            sb.append(varName).append(".invoke(").append(invokeTarget);
            for (int i = 2; i < args.size(); i++) {
                sb.append(", #{any(java.lang.Object)}");
            }
            sb.append(");");

            return sb.toString();
        }

        private Object[] buildInvokeMethodArgs(List<Expression> args, JavaType.@Nullable Method resolvedMethod, boolean isStatic) {
            List<Object> result = new ArrayList<>();
            result.add(args.get(0)); // receiver for getDeclaredMethod (Class for static, Object for instance)
            result.add(args.get(1)); // methodName
            for (int i = 2; i < args.size(); i++) {
                if (getParamClassLiteral(args, i, resolvedMethod) == null) {
                    result.add(args.get(i)); // arg.getClass() fallback for getDeclaredMethod
                }
            }
            if (!isStatic) {
                result.add(args.get(0)); // target for invoke (static uses the null literal, no placeholder)
            }
            for (int i = 2; i < args.size(); i++) {
                result.add(args.get(i)); // arg for invoke
            }
            return result.toArray();
        }

        private @Nullable String buildInvokeConstructorTemplate(List<Expression> args, ResultSink sink, Cursor scope,
                                                                JavaType.@Nullable Method resolvedCtor, boolean explicit) {
            if (!explicit && hasArrayArg(args, 1)) {
                // An array passed to the `Object...` varargs overload is ambiguous (spread vs single arg);
                // leave for manual migration (flagged downstream).
                return null;
            }
            JavaType.FullyQualified elem = classLiteralElementType(args.get(0));
            String genericType = elem != null ? elem.getClassName() : "?";
            String varName = constructorVarName(elem, scope);

            StringBuilder sb = new StringBuilder("Constructor<").append(genericType).append("> ").append(varName)
                    .append(" = #{any(java.lang.Class)}.getDeclaredConstructor(");

            int newInstanceArgCount;
            if (explicit) {
                List<Expression> paramTypeExprs = arrayElements(args.get(1));
                List<Expression> ctorArgExprs = arrayElements(args.get(2));
                if (paramTypeExprs == null || ctorArgExprs == null) {
                    // Cannot unwrap the explicit Class[]/Object[] arrays; leave for manual migration (flagged downstream)
                    return null;
                }
                for (int i = 0; i < paramTypeExprs.size(); i++) {
                    sb.append(i > 0 ? ", " : "").append("#{any(java.lang.Class)}");
                }
                newInstanceArgCount = ctorArgExprs.size();
            } else {
                for (int i = 1; i < args.size(); i++) {
                    sb.append(i > 1 ? ", " : "");
                    String classLiteral = getParamClassLiteral(args, i, resolvedCtor, 1);
                    sb.append(classLiteral != null ? classLiteral : "#{any(java.lang.Object)}.getClass()");
                }
                newInstanceArgCount = args.size() - 1;
            }
            sb.append(");\n");
            sb.append(varName).append(".setAccessible(true);\n");
            sb.append(constructorNewInstanceTail(varName, sink, elem != null, newInstanceArgCount));
            return sb.toString();
        }

        private Object[] buildInvokeConstructorArgs(List<Expression> args, JavaType.@Nullable Method resolvedCtor, boolean explicit) {
            List<Object> result = new ArrayList<>();
            result.add(args.get(0)); // getDeclaredConstructor receiver (Class)
            if (explicit) {
                List<Expression> paramTypeExprs = arrayElements(args.get(1));
                List<Expression> ctorArgExprs = arrayElements(args.get(2));
                if (paramTypeExprs != null) {
                    result.addAll(paramTypeExprs); // one per #{any(java.lang.Class)}
                }
                if (ctorArgExprs != null) {
                    result.addAll(ctorArgExprs); // newInstance args
                }
            } else {
                for (int i = 1; i < args.size(); i++) {
                    if (getParamClassLiteral(args, i, resolvedCtor, 1) == null) {
                        result.add(args.get(i)); // arg.getClass() fallback receiver
                    }
                }
                for (int i = 1; i < args.size(); i++) {
                    result.add(args.get(i)); // newInstance args
                }
            }
            return result.toArray();
        }

        private String constructorNewInstanceTail(String varName, ResultSink sink, boolean elemKnown, int argCount) {
            String invokeArgs = repeatObjectPlaceholders(argCount);
            if (sink.varName != null) {
                String castType = sink.castType != null ? sink.castType : "Object";
                // When the Class element type is known, newInstance() returns it directly (no cast needed);
                // otherwise we have a raw Constructor<?> returning Object that must be cast.
                if (!elemKnown && isNonObjectCast(castType)) {
                    return castType + " " + sink.varName + " = (" + castType + ") " + varName + ".newInstance(" + invokeArgs + ");";
                }
                return castType + " " + sink.varName + " = " + varName + ".newInstance(" + invokeArgs + ");";
            }
            return varName + ".newInstance(" + invokeArgs + ");";
        }

        private String repeatObjectPlaceholders(int count) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append(i > 0 ? ", " : "").append("#{any(java.lang.Object)}");
            }
            return sb.toString();
        }

        private @Nullable List<Expression> arrayElements(Expression expr) {
            if (expr instanceof J.NewArray) {
                return ((J.NewArray) expr).getInitializer();
            }
            return null;
        }

        private String constructorVarName(JavaType.@Nullable FullyQualified elem, Cursor scope) {
            String base;
            if (elem != null) {
                String simple = elem.getClassName();
                int dot = simple.lastIndexOf('.');
                if (dot >= 0) {
                    simple = simple.substring(dot + 1);
                }
                base = Character.toLowerCase(simple.charAt(0)) + simple.substring(1) + "Constructor";
            } else {
                base = "reflectConstructor";
            }
            return generateVariableName(base, scope, INCREMENT_NUMBER);
        }

        /**
         * Get the class literal for a parameter at the given argument index.
         * Prefers the resolved method's declared parameter type, falls back to the argument's
         * compile-time type, and returns null if neither is available.
         */
        private @Nullable String getParamClassLiteral(List<Expression> args, int argIndex,
                                                      JavaType.@Nullable Method resolvedMethod) {
            return getParamClassLiteral(args, argIndex, resolvedMethod, 2);
        }

        /**
         * Get the class literal for a parameter, where {@code firstParamArgIndex} is the argument index of
         * the first declared parameter (2 for {@code invokeMethod}: target, name, params...; 1 for
         * {@code invokeConstructor}: class, params...).
         */
        private @Nullable String getParamClassLiteral(List<Expression> args, int argIndex,
                                                      JavaType.@Nullable Method resolvedMethod, int firstParamArgIndex) {
            if (resolvedMethod != null) {
                int paramIdx = argIndex - firstParamArgIndex;
                List<JavaType> paramTypes = resolvedMethod.getParameterTypes();
                if (paramIdx >= 0 && paramIdx < paramTypes.size()) {
                    String literal = classLiteralFromType(paramTypes.get(paramIdx));
                    if (literal != null) {
                        return literal;
                    }
                }
            }
            return getClassLiteral(args.get(argIndex));
        }

        /**
         * Resolve the target method from the first argument's type and the method name.
         * Returns null if the method cannot be unambiguously resolved (not found, overloaded,
         * or missing type information).
         */
        private JavaType.@Nullable Method resolveTargetMethod(List<Expression> args) {
            if (args.size() <= 2) {
                return null;
            }
            JavaType targetType = args.get(0).getType();
            JavaType.FullyQualified fq = targetType instanceof JavaType.FullyQualified ? (JavaType.FullyQualified) targetType : null;
            return findUniqueMethod(fq, extractStringLiteral(args.get(1)), args.size() - 2);
        }

        /**
         * Resolve the static target method from the {@code Class} literal's element type and the method name.
         */
        private JavaType.@Nullable Method resolveStaticTargetMethod(List<Expression> args) {
            if (args.size() <= 2) {
                return null;
            }
            return findUniqueMethod(classLiteralElementType(args.get(0)), extractStringLiteral(args.get(1)), args.size() - 2);
        }

        private JavaType.@Nullable Method findUniqueMethod(JavaType.@Nullable FullyQualified targetType,
                                                           @Nullable String methodName, int expectedParamCount) {
            if (targetType == null || methodName == null) {
                return null;
            }
            JavaType.Method match = null;
            for (Iterator<JavaType.Method> it = targetType.getVisibleMethods(); it.hasNext(); ) {
                JavaType.Method method = it.next();
                if (method.getName().equals(methodName) &&
                        method.getParameterTypes().size() == expectedParamCount) {
                    if (match != null) {
                        return null; // ambiguous overload
                    }
                    match = method;
                }
            }
            return match;
        }

        /**
         * Resolve the target constructor from the {@code Class} literal's element type, by parameter count.
         * Returns null if not unambiguously resolvable (so parameter types fall back to {@code arg.getClass()}).
         */
        private JavaType.@Nullable Method resolveTargetConstructor(List<Expression> args) {
            if (args.size() <= 1) {
                return null;
            }
            JavaType.FullyQualified type = classLiteralElementType(args.get(0));
            if (type == null) {
                return null;
            }
            int expectedParamCount = args.size() - 1;
            JavaType.Method match = null;
            for (JavaType.Method method : type.getMethods()) {
                if (method.isConstructor() && method.getParameterTypes().size() == expectedParamCount) {
                    if (match != null) {
                        return null; // ambiguous overload
                    }
                    match = method;
                }
            }
            return match;
        }

        /**
         * Extract the element type {@code X} from a {@code Class<X>}-typed expression (e.g. {@code MyService.class}).
         */
        private JavaType.@Nullable FullyQualified classLiteralElementType(Expression classExpr) {
            JavaType.Parameterized parameterized = TypeUtils.asParameterized(classExpr.getType());
            if (parameterized != null && !parameterized.getTypeParameters().isEmpty()) {
                return TypeUtils.asFullyQualified(parameterized.getTypeParameters().get(0));
            }
            return null;
        }

        private J.@Nullable MethodInvocation extractWhiteboxInvocation(Statement statement) {
            if (statement instanceof J.MethodInvocation) {
                J.MethodInvocation mi = (J.MethodInvocation) statement;
                if (isMigratableWhitebox(mi)) {
                    return mi;
                }
            }
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                if (varDecls.getVariables().size() == 1) {
                    Expression init = varDecls.getVariables().get(0).getInitializer();
                    if (init instanceof J.MethodInvocation) {
                        J.MethodInvocation mi = (J.MethodInvocation) init;
                        if (isMigratableWhiteboxResult(mi)) {
                            return mi;
                        }
                    }
                }
            }
            return null;
        }

        private boolean isMigratableWhitebox(J.MethodInvocation mi) {
            return SET_INTERNAL_STATE.matches(mi) || SET_INTERNAL_STATE_WHERE.matches(mi) ||
                    GET_INTERNAL_STATE.matches(mi) || GET_INTERNAL_STATE_WHERE.matches(mi) ||
                    GET_FIELD.matches(mi) || GET_METHOD.matches(mi) ||
                    INVOKE_METHOD.matches(mi) || INVOKE_METHOD_STATIC.matches(mi) ||
                    INVOKE_CONSTRUCTOR_ARGS.matches(mi) || INVOKE_CONSTRUCTOR_EXPLICIT.matches(mi);
        }

        // Whitebox calls whose result can initialize a variable declaration (i.e. not the void setters).
        private boolean isMigratableWhiteboxResult(J.MethodInvocation mi) {
            return isMigratableWhitebox(mi) &&
                    !SET_INTERNAL_STATE.matches(mi) && !SET_INTERNAL_STATE_WHERE.matches(mi);
        }

        private boolean needsFieldImport(J.MethodInvocation mi) {
            return SET_INTERNAL_STATE.matches(mi) || SET_INTERNAL_STATE_WHERE.matches(mi) ||
                    GET_INTERNAL_STATE.matches(mi) || GET_INTERNAL_STATE_WHERE.matches(mi) ||
                    GET_FIELD.matches(mi);
        }

        private boolean needsMethodImport(J.MethodInvocation mi) {
            return INVOKE_METHOD.matches(mi) || INVOKE_METHOD_STATIC.matches(mi) || GET_METHOD.matches(mi);
        }

        private boolean needsConstructorImport(J.MethodInvocation mi) {
            return INVOKE_CONSTRUCTOR_ARGS.matches(mi) || INVOKE_CONSTRUCTOR_EXPLICIT.matches(mi);
        }

        /**
         * Generate the local variable name for a reflective {@code Field}. When the field name is a
         * String literal we derive a readable name (e.g. {@code nameField}); otherwise we fall back to
         * a generic {@code reflectField} base. Uniqueness within scope is guaranteed by INCREMENT_NUMBER.
         */
        private String fieldVarName(Expression nameExpr, Cursor scope) {
            return reflectVarName(nameExpr, "Field", "reflectField", scope);
        }

        /**
         * Generate the local variable name for a reflective {@code Method}. See {@link #fieldVarName}.
         */
        private String methodVarName(Expression nameExpr, Cursor scope) {
            return reflectVarName(nameExpr, "Method", "reflectMethod", scope);
        }

        // Derive a unique local name from a String-literal name (`name` + suffix, e.g. `nameField`),
        // falling back to a generic base when the name is not a literal.
        private String reflectVarName(Expression nameExpr, String suffix, String fallbackBase, Cursor scope) {
            String literal = extractStringLiteral(nameExpr);
            String base = literal != null ? literal + suffix : fallbackBase;
            return generateVariableName(base, scope, INCREMENT_NUMBER);
        }

        private @Nullable String extractStringLiteral(Expression expr) {
            if (expr instanceof J.Literal && ((J.Literal) expr).getValue() instanceof String) {
                return (String) ((J.Literal) expr).getValue();
            }
            return null;
        }

        private @Nullable String getClassLiteral(Expression expr) {
            return classLiteralFromType(expr.getType());
        }

        private @Nullable String classLiteralFromType(@Nullable JavaType type) {
            if (type instanceof JavaType.Primitive) {
                return ((JavaType.Primitive) type).getKeyword() + ".class";
            }
            if (type instanceof JavaType.FullyQualified) {
                return ((JavaType.FullyQualified) type).getClassName() + ".class";
            }
            return null;
        }

        private @Nullable String getCastType(@Nullable JavaType type) {
            if (type instanceof JavaType.FullyQualified) {
                return ((JavaType.FullyQualified) type).getClassName();
            }
            if (type instanceof JavaType.Primitive) {
                return ((JavaType.Primitive) type).getKeyword();
            }
            return null;
        }

        /**
         * {@code Field.get}/{@code Method.invoke} return {@code Object} (boxing primitives), so a primitive
         * declared type must be cast to its wrapper (a direct {@code (int) object} cast does not compile);
         * the surrounding assignment then auto-unboxes.
         */
        private String boxedCastType(String castType) {
            return BOXED_TYPES.getOrDefault(castType, castType);
        }

        private J.MethodDeclaration addThrowsExceptionIfAbsent(J.MethodDeclaration md) {
            if (md.getThrows() != null && md.getThrows().stream()
                    .anyMatch(j -> TypeUtils.isOfClassType(j.getType(), "java.lang.Exception") ||
                            TypeUtils.isOfClassType(j.getType(), "java.lang.Throwable"))) {
                return md;
            }
            JavaType.Class exceptionType = JavaType.ShallowClass.build("java.lang.Exception");
            return md.withThrows(ListUtils.concat(md.getThrows(),
                    new J.Identifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, emptyList(),
                            exceptionType.getClassName(), exceptionType, null)));
        }
        });
    }
}
