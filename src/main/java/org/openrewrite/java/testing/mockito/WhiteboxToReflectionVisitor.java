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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;
import static org.openrewrite.java.VariableNameUtils.generateVariableName;

/**
 * Shared machinery for replacing a single {@code org.powermock.reflect.Whitebox} API family with
 * plain Java reflection: extracts migratable Whitebox calls from block statements, applies the
 * family's replacement template, and maintains the surrounding method declaration
 * ({@code throws Exception}, imports, formatting). Subclasses configure the family's matchers and
 * required {@code java.lang.reflect} import via the constructor and supply the template and its
 * arguments.
 */
abstract class WhiteboxToReflectionVisitor extends JavaIsoVisitor<ExecutionContext> {

    static final String WHITEBOX_FQN = "org.powermock.reflect.Whitebox";

    private static final String WHITEBOX_REPLACED = "whiteboxReplaced";

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

    private final String reflectiveImport;
    private final List<MethodMatcher> matchers;

    WhiteboxToReflectionVisitor(String reflectiveImport, MethodMatcher... matchers) {
        this.reflectiveImport = reflectiveImport;
        this.matchers = Arrays.asList(matchers);
    }

    /**
     * Where a result-producing reflection call stores its result. {@code varName} is the declared
     * variable receiving the value, or null when the result is discarded (the call was a bare
     * statement); {@code castType} is that variable's declared type.
     */
    static final class ResultSink {
        final @Nullable String varName;
        final @Nullable String castType;

        private ResultSink(@Nullable String varName, @Nullable String castType) {
            this.varName = varName;
            this.castType = castType;
        }
    }

    /**
     * The replacement template for the matched call, or null when the call cannot be mechanically
     * migrated and must be left unchanged.
     */
    abstract @Nullable String buildTemplate(J.MethodInvocation mi, ResultSink sink, Cursor scope,
                                            JavaType.@Nullable Method resolvedMethod);

    /**
     * The template arguments matching {@link #buildTemplate}'s placeholders.
     */
    abstract Object[] buildArgs(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod);

    /**
     * The target method the call reflects on, when it can be unambiguously resolved; used to derive
     * declared parameter types for class literals.
     */
    JavaType.@Nullable Method resolve(J.MethodInvocation mi) {
        return null;
    }

    /**
     * This visitor gated so that only source files calling one of the configured Whitebox methods
     * are traversed.
     */
    TreeVisitor<?, ExecutionContext> withPrecondition() {
        TreeVisitor<?, ExecutionContext> precondition = new UsesMethod<>(matchers.get(0));
        for (int i = 1; i < matchers.size(); i++) {
            precondition = Preconditions.or(precondition, new UsesMethod<>(matchers.get(i)));
        }
        return Preconditions.check(precondition, this);
    }

    private boolean matches(J.MethodInvocation mi) {
        for (MethodMatcher matcher : matchers) {
            if (matcher.matches(mi)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        if (getCursor().getMessage(WHITEBOX_REPLACED, false)) {
            md = addThrowsExceptionIfAbsent(md);
            maybeRemoveImport(WHITEBOX_FQN);
            maybeAddImport(reflectiveImport, false);
            return maybeAutoFormat(method, md, ctx);
        }
        return md;
    }

    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        J.Block b = super.visitBlock(block, ctx);

        // Phase A: replace Whitebox calls that are a statement or single-variable declaration initializer.
        List<Statement> statements = b.getStatements();
        for (int i = statements.size() - 1; i >= 0; i--) {
            Statement stmt = statements.get(i);
            J.MethodInvocation mi = extractWhiteboxInvocation(stmt);
            if (mi == null) {
                continue;
            }
            Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
            JavaType.Method resolvedMethod = resolve(mi);
            String template = buildTemplate(mi, sinkFromStatement(stmt), blockCursor, resolvedMethod);
            if (template != null) {
                b = JavaTemplate.builder(template)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion())
                        .imports(templateImports(resolvedMethod).toArray(new String[0]))
                        .build()
                        .apply(
                                new Cursor(getCursor().getParentOrThrow(), b),
                                stmt.getCoordinates().replace(),
                                buildArgs(mi, resolvedMethod)
                        );
                recordReplacement(resolvedMethod);
                // Re-read statements list since the block has been rebuilt
                statements = b.getStatements();
            }
        }

        // Phase B: hoist Whitebox calls nested in expression position (return/arguments/conditions/...),
        // introducing a temp local before the enclosing statement and referencing it in place.
        List<UUID> originalIds = new ArrayList<>();
        for (Statement s : b.getStatements()) {
            originalIds.add(s.getId());
        }
        for (UUID id : originalIds) {
            Statement s = findStatementById(b, id);
            while (s != null) {
                J.MethodInvocation nested = findNestedWhiteboxResultCall(s, ctx);
                if (nested == null) {
                    break;
                }
                J.Block hoisted = hoistNestedCall(b, s, nested, ctx);
                if (hoisted == b) {
                    break;
                }
                b = hoisted;
                s = findStatementById(b, id);
            }
        }

        return b;
    }

    private void recordReplacement(JavaType.@Nullable Method resolvedMethod) {
        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, WHITEBOX_REPLACED, true);
        for (String paramImport : resolvedParamImports(resolvedMethod)) {
            maybeAddImport(paramImport);
        }
    }

    private List<String> templateImports(JavaType.@Nullable Method resolvedMethod) {
        List<String> imports = new ArrayList<>();
        imports.add(reflectiveImport);
        imports.addAll(resolvedParamImports(resolvedMethod));
        return imports;
    }

    // Non-java.lang fully-qualified parameter types of the resolved method that the generated class
    // literals (e.g. `List.class`) need imported.
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

    private @Nullable Statement findStatementById(J.Block b, UUID id) {
        for (Statement s : b.getStatements()) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Find the first result-producing Whitebox call nested in expression position within
     * {@code enclosing}, excluding the call Phase A targets directly, calls in short-circuit
     * or ternary positions (hoisting would change evaluation), and calls inside nested
     * blocks/lambdas/anonymous classes (handled by their own block).
     */
    private J.@Nullable MethodInvocation findNestedWhiteboxResultCall(Statement enclosing, ExecutionContext ctx) {
        J.MethodInvocation primary = extractWhiteboxInvocation(enclosing);
        UUID primaryId = primary != null ? primary.getId() : null;
        J.MethodInvocation[] holder = {null};
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext c) {
                if (holder[0] == null &&
                    !method.getId().equals(primaryId) &&
                    matches(method) &&
                    !returnsVoid(method) &&
                    !inCarveOut(getCursor())) {
                    holder[0] = method;
                    return method;
                }
                return super.visitMethodInvocation(method, c);
            }

            @Override
            public J.Block visitBlock(J.Block nestedBlock, ExecutionContext c) {
                return nestedBlock;
            }

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext c) {
                return lambda;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext c) {
                return newClass;
            }
        }.visit(enclosing, ctx);
        return holder[0];
    }

    // True when the call sits in a short-circuit (&&/||) right operand or a ternary branch,
    // where hoisting would change whether the reflective call executes.
    private boolean inCarveOut(Cursor callCursor) {
        Object child = callCursor.getValue();
        for (Cursor c = callCursor.getParent(); c != null; c = c.getParent()) {
            Object val = c.getValue();
            if (val instanceof J.Binary) {
                J.Binary bin = (J.Binary) val;
                if ((bin.getOperator() == J.Binary.Type.And || bin.getOperator() == J.Binary.Type.Or) &&
                    bin.getRight() == child) {
                    return true;
                }
            } else if (val instanceof J.Ternary) {
                J.Ternary ternary = (J.Ternary) val;
                if (ternary.getTruePart() == child || ternary.getFalsePart() == child) {
                    return true;
                }
            }
            child = val;
        }
        return false;
    }

    private J.Block hoistNestedCall(J.Block b, Statement enclosing, J.MethodInvocation nested, ExecutionContext ctx) {
        Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
        JavaType.Method resolvedMethod = resolve(nested);
        String tempName = tempVariableName(nested, blockCursor);
        ResultSink sink = new ResultSink(tempName, getCastType(nested.getType()));
        String template = buildTemplate(nested, sink, blockCursor, resolvedMethod);
        if (template == null) {
            return b;
        }
        J.Block rebuilt = JavaTemplate.builder(template)
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion())
                .imports(templateImports(resolvedMethod).toArray(new String[0]))
                .build()
                .apply(blockCursor, enclosing.getCoordinates().before(), buildArgs(nested, resolvedMethod));

        J.Identifier ref = new J.Identifier(randomId(), nested.getPrefix(), Markers.EMPTY, emptyList(),
                tempName, nested.getType(), null);
        rebuilt = (J.Block) new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext c) {
                if (method.getId().equals(nested.getId())) {
                    return ref;
                }
                return super.visitMethodInvocation(method, c);
            }
        }.visitNonNull(rebuilt, ctx);

        recordReplacement(resolvedMethod);
        return rebuilt;
    }

    private J.@Nullable MethodInvocation extractWhiteboxInvocation(Statement statement) {
        if (statement instanceof J.MethodInvocation) {
            J.MethodInvocation mi = (J.MethodInvocation) statement;
            if (matches(mi)) {
                return mi;
            }
        }
        if (statement instanceof J.VariableDeclarations) {
            J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
            if (varDecls.getVariables().size() == 1) {
                Expression init = varDecls.getVariables().get(0).getInitializer();
                if (init instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) init;
                    // A void call (setInternalState) cannot initialize a variable declaration.
                    if (matches(mi) && !returnsVoid(mi)) {
                        return mi;
                    }
                }
            }
        }
        return null;
    }

    private boolean returnsVoid(J.MethodInvocation mi) {
        return mi.getMethodType() != null && JavaType.Primitive.Void == mi.getMethodType().getReturnType();
    }

    // `Field <var> = <target>.getClass().getDeclaredField(<name>); <var>.setAccessible(true);` —
    // shared by the get/set field variants.
    String fieldLookupPrefix(String varName) {
        return "Field " + varName + " = #{any(java.lang.Object)}.getClass().getDeclaredField(#{any(java.lang.String)});\n" +
                varName + ".setAccessible(true);\n";
    }

    // `Field <var> = <whereClass>.getDeclaredField(<name>); <var>.setAccessible(true);` —
    // used by the 4-arg setInternalState(target, field, value, Class) where-overload.
    String fieldLookupPrefixWhere(String varName) {
        return "Field " + varName + " = #{any(java.lang.Class)}.getDeclaredField(#{any(java.lang.String)});\n" +
                varName + ".setAccessible(true);\n";
    }

    // True when castType denotes a meaningful type to cast to (i.e. not null and not Object).
    boolean isNonObjectCast(@Nullable String castType) {
        return castType != null && !"Object".equals(castType) && !"java.lang.Object".equals(castType);
    }

    @Nullable String extractStringLiteral(Expression expr) {
        if (expr instanceof J.Literal && ((J.Literal) expr).getValue() instanceof String) {
            return (String) ((J.Literal) expr).getValue();
        }
        return null;
    }

    @Nullable String getCastType(@Nullable JavaType type) {
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
    String boxedCastType(String castType) {
        return BOXED_TYPES.getOrDefault(castType, castType);
    }

    String tempVariableName(J.MethodInvocation mi, Cursor scope) {
        JavaType.FullyQualified returnType = TypeUtils.asFullyQualified(mi.getType());
        if (returnType != null && "java.lang.reflect.Field".equals(returnType.getFullyQualifiedName())) {
            return generateVariableName("reflectField", scope, INCREMENT_NUMBER);
        }
        if (returnType != null && "java.lang.reflect.Method".equals(returnType.getFullyQualifiedName())) {
            return generateVariableName("reflectMethod", scope, INCREMENT_NUMBER);
        }
        if ("java.lang.reflect.Constructor".equals(reflectiveImport)) {
            return generateVariableName("reflectInstance", scope, INCREMENT_NUMBER);
        }
        if ("java.lang.reflect.Method".equals(reflectiveImport)) {
            return generateVariableName("reflectResult", scope, INCREMENT_NUMBER);
        }
        return generateVariableName("reflectValue", scope, INCREMENT_NUMBER);
    }

    /**
     * For calls whose result type IS the reflective object ({@code getField}/{@code getMethod}),
     * reuse the result variable as the local; otherwise generate one.
     */
    String resultLocalName(ResultSink sink, Expression nameExpr, Cursor scope, boolean field) {
        if (sink.varName != null) {
            return sink.varName;
        }
        return field ? fieldVarName(nameExpr, scope) : methodVarName(nameExpr, scope);
    }

    /**
     * Generate the local variable name for a reflective {@code Field}. When the field name is a
     * String literal we derive a readable name (e.g. {@code nameField}); otherwise we fall back to
     * a generic {@code reflectField} base. Uniqueness within scope is guaranteed by INCREMENT_NUMBER.
     */
    String fieldVarName(Expression nameExpr, Cursor scope) {
        return reflectVarName(nameExpr, "Field", "reflectField", scope);
    }

    /**
     * Generate the local variable name for a reflective {@code Method}. See {@link #fieldVarName}.
     */
    String methodVarName(Expression nameExpr, Cursor scope) {
        return reflectVarName(nameExpr, "Method", "reflectMethod", scope);
    }

    // Derive a unique local name from a String-literal name (`name` + suffix, e.g. `nameField`),
    // falling back to a generic base when the name is not a literal.
    private String reflectVarName(Expression nameExpr, String suffix, String fallbackBase, Cursor scope) {
        String literal = extractStringLiteral(nameExpr);
        String base = literal != null ? literal + suffix : fallbackBase;
        return generateVariableName(base, scope, INCREMENT_NUMBER);
    }

    // True when any argument from {@code fromIndex} onward is an array — used to skip calls that pass an
    // explicit {@code Class[]} varargs array, which we cannot expand into individual class literals.
    boolean hasArrayArg(List<Expression> args, int fromIndex) {
        for (int i = fromIndex; i < args.size(); i++) {
            if (TypeUtils.asArray(args.get(i).getType()) != null) {
                return true;
            }
        }
        return false;
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
}
