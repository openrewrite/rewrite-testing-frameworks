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

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
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
     * The class declaring the field or method the call reflects on, when it can be statically resolved
     * and referenced from the test. Reflecting on this class rather than on {@code target.getClass()}
     * also finds members declared in a superclass, and members of Mockito spies and mocks, whose runtime
     * class is a generated subclass.
     */
    JavaType.@Nullable FullyQualified lookupOwner(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
        return null;
    }

    JavaType.@Nullable FullyQualified fieldOwner(Expression target, @Nullable String fieldName) {
        if (fieldName == null) {
            return null;
        }
        for (JavaType.FullyQualified type = TypeUtils.asFullyQualified(target.getType());
             type != null; type = type.getSupertype()) {
            for (JavaType.Variable member : type.getMembers()) {
                if (member.getName().equals(fieldName)) {
                    return isAccessible(type) ? type : null;
                }
            }
        }
        return null;
    }

    boolean isAccessible(JavaType.FullyQualified type) {
        if (type instanceof JavaType.Parameterized) {
            type = ((JavaType.Parameterized) type).getType();
        }
        if (type.getFlags().contains(Flag.Private) || type.getClassName().contains("$")) {
            return false;
        }
        JavaType.FullyQualified owningClass = type.getOwningClass();
        if (owningClass != null && !isAccessible(owningClass)) {
            return false;
        }
        if (type.getFlags().contains(Flag.Public)) {
            return true;
        }
        JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
        if (sourceFile == null) {
            return false;
        }
        J.Package pkg = sourceFile.getPackageDeclaration();
        return type.getPackageName().equals(pkg == null ? "" : pkg.getPackageName());
    }

    String lookupReceiverTemplate(JavaType.@Nullable FullyQualified owner) {
        return owner == null ? "#{any(java.lang.Object)}.getClass()" : "#{any(java.lang.Class)}";
    }

    Object lookupReceiverArg(Expression target, JavaType.@Nullable FullyQualified owner) {
        return owner == null ? target : classLiteral(owner);
    }

    /**
     * A type-attributed {@code Owner.class} literal, passed to templates as a parameter because the template
     * parser does not see types declared in other source files.
     */
    static J.FieldAccess classLiteral(JavaType.FullyQualified owner) {
        JavaType.FullyQualified raw = owner instanceof JavaType.Parameterized ? ((JavaType.Parameterized) owner).getType() : owner;
        JavaType.Parameterized classType = new JavaType.Parameterized(null, JavaType.ShallowClass.build("java.lang.Class"), singletonList(raw));
        return new J.FieldAccess(randomId(), Space.EMPTY, Markers.EMPTY, typeReference(raw),
                JLeftPadded.build(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "class", classType, null)),
                classType);
    }

    private static Expression typeReference(JavaType.FullyQualified type) {
        String simpleName = type.getClassName().substring(type.getClassName().lastIndexOf('.') + 1);
        J.Identifier name = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), simpleName, type, null);
        JavaType.FullyQualified owningClass = type.getOwningClass();
        return owningClass == null ? name :
                new J.FieldAccess(randomId(), Space.EMPTY, Markers.EMPTY, typeReference(owningClass), JLeftPadded.build(name), type);
    }

    private static @Nullable String topLevelImport(JavaType.@Nullable FullyQualified owner) {
        if (owner == null) {
            return null;
        }
        while (owner.getOwningClass() != null) {
            owner = owner.getOwningClass();
        }
        return "java.lang".equals(owner.getPackageName()) || owner.getPackageName().isEmpty() ?
                null : owner.getFullyQualifiedName();
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

        // Replace Whitebox calls that are a statement or single-variable declaration initializer, in
        // reverse so coordinates stay valid: each JavaTemplate.apply rebuilds the block, letting
        // generateVariableName see prior locals (ListUtils.flatMap would need manual name dedup).
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
                recordReplacement(mi, resolvedMethod);
                // Re-read statements list since the block has been rebuilt
                statements = b.getStatements();
            }
        }

        return hoistNestedCalls(b, ctx);
    }

    /**
     * For a value-returning call nested in a larger expression: the declaration of the reflective
     * {@code Field} or {@code Method} named {@code varName} to insert before the enclosing statement, and
     * the expression that replaces the call. The first parameter of the expression template is the
     * declared {@code Field} or {@code Method}.
     */
    static final class Hoisted {
        final String varName;
        final String declaration;
        final Object[] declarationArgs;
        final String expression;
        final Object[] expressionArgs;

        Hoisted(String varName, String declaration, Object[] declarationArgs, String expression, Object[] expressionArgs) {
            this.varName = varName;
            this.declaration = declaration;
            this.declarationArgs = declarationArgs;
            this.expression = expression;
            this.expressionArgs = expressionArgs;
        }
    }

    /**
     * How to migrate the call when it is nested in a larger expression, or null when the family does
     * not support that or the declaring class of the member cannot be determined. The declaration then
     * only refers to class literals, so it can be moved in front of the enclosing statement.
     */
    @Nullable Hoisted hoist(J.MethodInvocation mi, Cursor scope) {
        return null;
    }

    String castPrefix(J.MethodInvocation mi) {
        JavaType returnType = mi.getMethodType() == null ? null : mi.getMethodType().getReturnType();
        if (returnType instanceof JavaType.Parameterized) {
            returnType = ((JavaType.Parameterized) returnType).getType();
        }
        String castType = returnType instanceof JavaType.GenericTypeVariable || returnType instanceof JavaType.Unknown ?
                null : getCastType(returnType);
        if (!isNonObjectCast(castType)) {
            return "";
        }
        String typeImport = topLevelImport(TypeUtils.asFullyQualified(returnType));
        if (typeImport != null) {
            maybeAddImport(typeImport);
        }
        return "(" + boxedCastType(castType) + ") ";
    }

    private J.Block hoistNestedCalls(J.Block block, ExecutionContext ctx) {
        J.Block b = block;
        for (int i = b.getStatements().size() - 1; i >= 0; i--) {
            UUID statementId = b.getStatements().get(i).getId();
            while (true) {
                Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), b);
                Statement statement = findStatement(b, statementId);
                J.MethodInvocation nested = firstHoistable(statement, blockCursor);
                if (nested == null) {
                    break;
                }
                Hoisted hoisted = requireNonNull(hoist(nested, blockCursor));
                JavaType.Method resolvedMethod = resolve(nested);
                b = JavaTemplate.builder(hoisted.declaration)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion())
                        .imports(templateImports(resolvedMethod).toArray(new String[0]))
                        .build()
                        .apply(blockCursor, statement.getCoordinates().before(), hoisted.declarationArgs);
                UUID nestedId = nested.getId();
                Object[] expressionArgs = ListUtils.concat(declaredVariable(b, hoisted.varName),
                        Arrays.asList(hoisted.expressionArgs)).toArray();
                b = (J.Block) new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (method.getId().equals(nestedId)) {
                            return JavaTemplate.builder(hoisted.expression)
                                    .contextSensitive()
                                    .javaParser(JavaParser.fromJavaVersion())
                                    .build()
                                    .apply(getCursor(), method.getCoordinates().replace(), expressionArgs);
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                }.visitNonNull(b, ctx, getCursor().getParentOrThrow());
                recordReplacement(nested, resolvedMethod);
            }
        }
        return b;
    }

    private static Statement findStatement(J.Block block, UUID id) {
        for (Statement statement : block.getStatements()) {
            if (statement.getId().equals(id)) {
                return statement;
            }
        }
        throw new IllegalStateException("Statement not found");
    }

    private static J.Identifier declaredVariable(J.Block block, String name) {
        for (Statement statement : block.getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    if (variable.getSimpleName().equals(name)) {
                        return variable.getName().withId(randomId()).withPrefix(Space.EMPTY);
                    }
                }
            }
        }
        throw new IllegalStateException("Variable " + name + " not found");
    }

    private J.@Nullable MethodInvocation firstHoistable(Statement statement, Cursor blockCursor) {
        J.MethodInvocation[] found = new J.MethodInvocation[1];
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, p);
                if (found[0] == null && mi != statement && matches(mi) && !isVariableInitializer(statement, getCursor()) &&
                    isOutsideLambdaAndAnonymousClass(getCursor()) && hoist(mi, blockCursor) != null) {
                    found[0] = mi;
                }
                return mi;
            }
        }.visit(statement, 0, blockCursor);
        return found[0];
    }

    private static boolean isVariableInitializer(Statement statement, Cursor cursor) {
        return statement instanceof J.VariableDeclarations &&
               ((J.VariableDeclarations) statement).getVariables().size() == 1 &&
               cursor.getParentTreeCursor().getValue() instanceof J.VariableDeclarations.NamedVariable;
    }

    // Checked reflection exceptions cannot propagate out of a lambda or anonymous class body.
    private static boolean isOutsideLambdaAndAnonymousClass(Cursor cursor) {
        for (Cursor c = cursor.getParentTreeCursor(); !(c.getValue() instanceof J.MethodDeclaration); c = c.getParentTreeCursor()) {
            Object value = c.getValue();
            if (value instanceof J.Lambda || value instanceof J.NewClass || value instanceof J.ClassDeclaration ||
                value instanceof JavaSourceFile) {
                return false;
            }
        }
        return true;
    }

    private void recordReplacement(J.MethodInvocation mi, JavaType.@Nullable Method resolvedMethod) {
        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, WHITEBOX_REPLACED, true);
        for (String paramImport : resolvedParamImports(resolvedMethod)) {
            maybeAddImport(paramImport);
        }
        String ownerImport = topLevelImport(lookupOwner(mi, resolvedMethod));
        if (ownerImport != null) {
            maybeAddImport(ownerImport);
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

    // `Field <var> = <Owner>.class.getDeclaredField(<name>); <var>.setAccessible(true);`, falling back to
    // `<target>.getClass()` as the owner when it is unknown — shared by the get/set field variants.
    String fieldLookupPrefix(String varName, JavaType.@Nullable FullyQualified owner) {
        return "Field " + varName + " = " + lookupReceiverTemplate(owner) + ".getDeclaredField(#{any(java.lang.String)});\n" +
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

    /**
     * The value of a String literal, or of a {@code static final} String constant initialized with a
     * literal in the same source file, as tests commonly name the fields and methods they reflect on.
     */
    @Nullable String extractStringLiteral(Expression expr) {
        if (expr instanceof J.Literal) {
            Object value = ((J.Literal) expr).getValue();
            return value instanceof String ? (String) value : null;
        }
        JavaType.Variable constant = expr instanceof J.Identifier ? ((J.Identifier) expr).getFieldType() :
                expr instanceof J.FieldAccess ? ((J.FieldAccess) expr).getName().getFieldType() : null;
        JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
        if (constant == null || !constant.hasFlags(Flag.Static, Flag.Final) || sourceFile == null) {
            return null;
        }
        String[] value = new String[1];
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
                if (constant.equals(variable.getVariableType()) && variable.getInitializer() instanceof J.Literal &&
                    ((J.Literal) variable.getInitializer()).getValue() instanceof String) {
                    value[0] = (String) ((J.Literal) variable.getInitializer()).getValue();
                }
                return variable;
            }
        }.visit(sourceFile, 0);
        return value[0];
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
