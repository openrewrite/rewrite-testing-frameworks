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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final String HIERARCHY_LOOKUP_NEEDED = "whiteboxHierarchyLookupNeeded";

    static final String FIELD_LOOKUP_HELPER = "declaredFieldInHierarchy";

    static final String METHOD_LOOKUP_HELPER = "declaredMethodInHierarchy";

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

    private @Nullable JavaSourceFile stringConstantsSource;
    private List<J.VariableDeclarations.NamedVariable> stringConstants = emptyList();
    private final Set<String> castImports = new LinkedHashSet<>();

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
        JavaType.FullyQualified declaringType = fieldDeclaringType(target, fieldName);
        return declaringType != null && isAccessible(declaringType) ? declaringType : null;
    }

    JavaType.@Nullable FullyQualified fieldDeclaringType(Expression target, @Nullable String fieldName) {
        if (fieldName == null) {
            return null;
        }
        for (JavaType.FullyQualified type = TypeUtils.asFullyQualified(target.getType());
             type != null; type = type.getSupertype()) {
            if (declaresField(type, fieldName)) {
                return type;
            }
        }
        return null;
    }

    static boolean isSuperclassOf(JavaType.FullyQualified declaringType, Expression target) {
        JavaType.FullyQualified targetType = TypeUtils.asFullyQualified(target.getType());
        return targetType != null &&
               !declaringType.getFullyQualifiedName().equals(targetType.getFullyQualifiedName());
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

    /**
     * True when the member has to be looked up on the target's runtime class, which for a member declared
     * in a superclass, or for the generated subclass of a Mockito mock or spy, does not declare it. The
     * generated code then walks the hierarchy the way {@code Whitebox} does, via a helper method.
     */
    boolean usesHierarchyLookup(JavaType.@Nullable FullyQualified owner) {
        return owner == null;
    }

    void recordHierarchyLookup() {
        getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, HIERARCHY_LOOKUP_NEEDED, true);
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

    /**
     * Whether migrating the call has to look the member up on {@code target.getClass()}, as its declaring
     * class cannot be referenced. That misses members declared in a superclass and those of Mockito spies
     * and mocks, so the migrated test may fail where the PowerMock one passed.
     */
    boolean fallsBackToRuntimeClass(J.MethodInvocation mi) {
        return false;
    }

    /**
     * The class declaring the member the call accesses, as found from the declared type of the target, or null
     * when that type neither declares nor inherits it, such as when the target is declared as an interface.
     */
    JavaType.@Nullable FullyQualified memberDeclaringType(J.MethodInvocation mi) {
        return null;
    }

    /**
     * Whether {@code type} itself declares the member the call accesses, so that
     * {@code target.getClass().getDeclared*} finds it when {@code type} is the runtime class of the target.
     */
    boolean declaresMember(JavaType.FullyQualified type, J.MethodInvocation mi) {
        return false;
    }

    static boolean declaresField(JavaType.FullyQualified type, @Nullable String fieldName) {
        for (JavaType.Variable member : type.getMembers()) {
            if (member.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    boolean matches(J.MethodInvocation mi) {
        for (MethodMatcher matcher : matchers) {
            if (matcher.matches(mi)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        // The helper has to exist before the call sites are templated, or the generated call to it
        // cannot be type-attributed.
        if (!needsHierarchyLookup(classDecl) || hasHelper(classDecl)) {
            return super.visitClassDeclaration(classDecl, ctx);
        }
        maybeAddImport(reflectiveImport, false);
        J.ClassDeclaration withHelper = JavaTemplate.builder(helperSource())
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion())
                .imports(reflectiveImport)
                .build()
                .apply(getCursor(), classDecl.getBody().getCoordinates().lastStatement());
        updateCursor(withHelper);
        return super.visitClassDeclaration(withHelper, ctx);
    }

    /** Whether any call in this class has to look the member up on the target's runtime class. */
    private boolean needsHierarchyLookup(J.ClassDeclaration classDecl) {
        Cursor classCursor = getCursor();
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean found) {
                if (!found.get() && matches(method)) {
                    Cursor outer = WhiteboxToReflectionVisitor.this.getCursor();
                    WhiteboxToReflectionVisitor.this.setCursor(classCursor);
                    try {
                        if (fallsBackToRuntimeClass(method)) {
                            found.set(true);
                        }
                    } finally {
                        WhiteboxToReflectionVisitor.this.setCursor(outer);
                    }
                }
                return super.visitMethodInvocation(method, found);
            }
        }.reduce(classDecl, new AtomicBoolean()).get();
    }

    private boolean hasHelper(J.ClassDeclaration classDecl) {
        for (Statement statement : classDecl.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration &&
                    helperName().equals(((J.MethodDeclaration) statement).getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * `Whitebox` searches the whole class hierarchy for a member, which `getDeclaredField` and
     * `getDeclaredMethod` do not. One helper per test class keeps the call sites as short as the
     * direct lookup they replace.
     */
    private String helperSource() {
        if ("java.lang.reflect.Field".equals(reflectiveImport)) {
            return "private static Field " + FIELD_LOOKUP_HELPER + "(Class<?> type, String name) throws NoSuchFieldException {\n" +
                    "    for (Class<?> c = type; c != null; c = c.getSuperclass()) {\n" +
                    "        try {\n" +
                    "            return c.getDeclaredField(name);\n" +
                    "        } catch (NoSuchFieldException e) {\n" +
                    "            // declared further up the hierarchy\n" +
                    "        }\n" +
                    "    }\n" +
                    "    throw new NoSuchFieldException(name);\n" +
                    "}";
        }
        return "private static Method " + METHOD_LOOKUP_HELPER + "(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {\n" +
                "    for (Class<?> c = type; c != null; c = c.getSuperclass()) {\n" +
                "        try {\n" +
                "            return c.getDeclaredMethod(name, parameterTypes);\n" +
                "        } catch (NoSuchMethodException e) {\n" +
                "            // declared further up the hierarchy\n" +
                "        }\n" +
                "    }\n" +
                "    throw new NoSuchMethodException(name);\n" +
                "}";
    }

    private String helperName() {
        return "java.lang.reflect.Field".equals(reflectiveImport) ? FIELD_LOOKUP_HELPER : METHOD_LOOKUP_HELPER;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        if (getCursor().getMessage(WHITEBOX_REPLACED, false)) {
            // Declaring `throws` is the readable option and is what a test method would do anyway, but it is
            // only safe where nothing depends on the signature: a caller elsewhere in the file would stop
            // compiling, and an override such as `Runnable.run()` could not legally widen at all. Those get the
            // reflection wrapped instead.
            md = canWidenSignature(md) ? addThrowsExceptionIfAbsent(md) : wrapBodyInTryCatch(md, ctx);
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
                b = reflectionTemplate(template, resolvedMethod)
                        .apply(blockCursor, stmt.getCoordinates().replace(), buildArgs(mi, resolvedMethod));
                recordReplacement(mi, resolvedMethod);
                // Re-read statements list since the block has been rebuilt
                statements = b.getStatements();
            }
        }

        return hoistNestedCalls(b, ctx);
    }

    /**
     * For a value-returning call nested in a larger expression: the declaration of the reflective
     * {@code Field} or {@code Method} named {@code varName} to insert before the enclosing statement, taking
     * the owner's class literal and the member name, and the expression that replaces the call, taking the
     * declared {@code Field} or {@code Method}, the target and the remaining arguments.
     */
    @RequiredArgsConstructor
    static final class Hoisted {
        final J.MethodInvocation call;
        final String varName;
        final String declaration;
        final String expression;
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
            castImports.add(typeImport);
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
                Hoisted hoisted = firstHoistable(statement, blockCursor);
                if (hoisted == null) {
                    break;
                }
                J.MethodInvocation nested = hoisted.call;
                List<Expression> args = nested.getArguments();
                JavaType.Method resolvedMethod = resolve(nested);
                b = reflectionTemplate(hoisted.declaration, resolvedMethod).apply(blockCursor,
                        statement.getCoordinates().before(),
                        classLiteral(requireNonNull(lookupOwner(nested, resolvedMethod))), args.get(1));
                // Passed as a parameter because the template parser does not attribute a local it has just inserted.
                List<Expression> expressionArgs = new ArrayList<>();
                expressionArgs.add(declaredVariable(b, hoisted.varName));
                expressionArgs.add(args.get(0));
                expressionArgs.addAll(args.subList(2, args.size()));
                b = (J.Block) new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (method.getId().equals(nested.getId())) {
                            return reflectionTemplate(hoisted.expression, resolvedMethod)
                                    .apply(getCursor(), method.getCoordinates().replace(), expressionArgs.toArray());
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                }.visitNonNull(b, ctx, getCursor().getParentOrThrow());
                recordReplacement(nested, resolvedMethod);
            }
        }
        return b;
    }

    private JavaTemplate reflectionTemplate(String code, JavaType.@Nullable Method resolvedMethod) {
        return JavaTemplate.builder(code)
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion())
                .imports(templateImports(resolvedMethod).toArray(new String[0]))
                .build();
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

    private @Nullable Hoisted firstHoistable(Statement statement, Cursor blockCursor) {
        return new JavaIsoVisitor<AtomicReference<@Nullable Hoisted>>() {
            @Override
            public J.Block visitBlock(J.Block block, AtomicReference<@Nullable Hoisted> found) {
                // Nested blocks were already handled when the outer visitor visited them.
                return block;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicReference<@Nullable Hoisted> found) {
                if (found.get() != null) {
                    return method;
                }
                J.MethodInvocation mi = super.visitMethodInvocation(method, found);
                if (found.get() == null && matches(mi) && isOutsideLambdaAndAnonymousClass(getCursor())) {
                    found.set(hoist(mi, blockCursor));
                }
                return mi;
            }
        }.reduce(statement, new AtomicReference<>(), blockCursor).get();
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
        // onlyIfReferenced=false: the class literals were only just templated in, so the type
        // attribution `maybeAddImport` looks for is not there yet.
        for (String paramImport : resolvedParamImports(resolvedMethod)) {
            maybeAddImport(paramImport, false);
        }
        String ownerImport = topLevelImport(lookupOwner(mi, resolvedMethod));
        if (ownerImport != null) {
            maybeAddImport(ownerImport, false);
        }
    }

    private List<String> templateImports(JavaType.@Nullable Method resolvedMethod) {
        List<String> imports = new ArrayList<>();
        imports.add(reflectiveImport);
        imports.addAll(resolvedParamImports(resolvedMethod));
        imports.addAll(castImports);
        return imports;
    }

    // Non-java.lang parameter types of the resolved method that the generated class literals
    // (e.g. `List.class`) need imported. A nested type is written `Map.Entry.class`, so it is the
    // enclosing type that has to be imported.
    private List<String> resolvedParamImports(JavaType.@Nullable Method resolvedMethod) {
        if (resolvedMethod == null) {
            return emptyList();
        }
        List<String> imports = new ArrayList<>();
        for (JavaType paramType : resolvedMethod.getParameterTypes()) {
            String paramImport = topLevelImport(TypeUtils.asFullyQualified(paramType));
            if (paramImport != null) {
                imports.add(paramImport);
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
        if (usesHierarchyLookup(owner)) {
            recordHierarchyLookup();
            return "Field " + varName + " = " + FIELD_LOOKUP_HELPER +
                    "(#{any(java.lang.Object)}.getClass(), #{any(java.lang.String)});\n" +
                    varName + ".setAccessible(true);\n";
        }
        return "Field " + varName + " = " + lookupReceiverTemplate(owner) + ".getDeclaredField(#{any(java.lang.String)});\n" +
                varName + ".setAccessible(true);\n";
    }

    // `Field <var> = <Owner>.class.getDeclaredField("<name>"); <var>.setAccessible(true);` — used where the
    // field was identified by its type, so the name is resolved here rather than passed through.
    String fieldLookupPrefixNamed(String varName, String fieldName) {
        return "Field " + varName + " = #{any(java.lang.Class)}.getDeclaredField(\"" + fieldName + "\");\n" +
                varName + ".setAccessible(true);\n";
    }

    /** The type a `Foo.class` argument denotes, or null when the argument is not a class literal. */
    JavaType.@Nullable FullyQualified classLiteralType(Expression expression) {
        if (!(expression instanceof J.FieldAccess) || !"class".equals(((J.FieldAccess) expression).getSimpleName())) {
            return null;
        }
        JavaType type = expression.getType();
        if (type instanceof JavaType.Parameterized && !((JavaType.Parameterized) type).getTypeParameters().isEmpty()) {
            return TypeUtils.asFullyQualified(((JavaType.Parameterized) type).getTypeParameters().get(0));
        }
        return null;
    }

    /**
     * The single field of {@code fieldType} declared by {@code owner} or one of its supertypes. `Whitebox`
     * picks a field by type at runtime; resolving it here keeps the generated lookup by name, and declining
     * when more than one field matches keeps the migration from guessing.
     */
    JavaType.@Nullable Variable uniqueFieldOfType(JavaType.@Nullable FullyQualified owner,
                                                  JavaType.@Nullable FullyQualified fieldType) {
        if (owner == null || fieldType == null) {
            return null;
        }
        JavaType.Variable match = null;
        for (JavaType.FullyQualified type = owner; type != null; type = type.getSupertype()) {
            for (JavaType.Variable member : type.getMembers()) {
                if (TypeUtils.isOfType(member.getType(), fieldType)) {
                    if (match != null) {
                        return null;
                    }
                    match = member;
                }
            }
        }
        return match;
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
        if (constant == null || !constant.hasFlags(Flag.Static, Flag.Final)) {
            return null;
        }
        for (J.VariableDeclarations.NamedVariable variable : stringConstants()) {
            if (constant.equals(variable.getVariableType())) {
                return (String) ((J.Literal) requireNonNull(variable.getInitializer())).getValue();
            }
        }
        return null;
    }

    // A list rather than a map keyed by JavaType.Variable, which overrides equals but not hashCode.
    private List<J.VariableDeclarations.NamedVariable> stringConstants() {
        JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
        if (sourceFile == null) {
            return emptyList();
        }
        if (sourceFile != stringConstantsSource) {
            stringConstantsSource = sourceFile;
            stringConstants = new JavaIsoVisitor<List<J.VariableDeclarations.NamedVariable>>() {
                @Override
                public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable,
                                                                          List<J.VariableDeclarations.NamedVariable> constants) {
                    if (variable.getInitializer() instanceof J.Literal &&
                        ((J.Literal) variable.getInitializer()).getValue() instanceof String) {
                        constants.add(variable);
                    }
                    return variable;
                }
            }.reduce(sourceFile, new ArrayList<>());
        }
        return stringConstants;
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


    private static final List<AnnotationMatcher> TEST_OR_FIXTURE = Arrays.asList(
            new AnnotationMatcher("@org.junit.Test"),
            new AnnotationMatcher("@org.junit.Before"),
            new AnnotationMatcher("@org.junit.After"),
            new AnnotationMatcher("@org.junit.BeforeClass"),
            new AnnotationMatcher("@org.junit.AfterClass"),
            new AnnotationMatcher("@org.junit.jupiter.api.Test"),
            new AnnotationMatcher("@org.junit.jupiter.api.BeforeEach"),
            new AnnotationMatcher("@org.junit.jupiter.api.AfterEach"),
            new AnnotationMatcher("@org.junit.jupiter.api.BeforeAll"),
            new AnnotationMatcher("@org.junit.jupiter.api.AfterAll"),
            new AnnotationMatcher("@org.junit.jupiter.params.ParameterizedTest"),
            new AnnotationMatcher("@org.testng.annotations.Test"),
            new AnnotationMatcher("@org.testng.annotations.BeforeMethod"),
            new AnnotationMatcher("@org.testng.annotations.AfterMethod"),
            new AnnotationMatcher("@org.testng.annotations.BeforeClass"),
            new AnnotationMatcher("@org.testng.annotations.AfterClass"));

    private static boolean isTestOrFixtureMethod(J.MethodDeclaration md) {
        for (J.Annotation annotation : md.getLeadingAnnotations()) {
            for (AnnotationMatcher matcher : TEST_OR_FIXTURE) {
                if (matcher.matches(annotation)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A test or fixture method is invoked reflectively by the framework, so widening it is always safe. */
    private boolean canWidenSignature(J.MethodDeclaration md) {
        return isTestOrFixtureMethod(md) || (!overridesASupertypeMethod(md) && !hasCallerInFile(md));
    }

    private static boolean overridesASupertypeMethod(J.MethodDeclaration md) {
        JavaType.Method type = md.getMethodType();
        if (type == null) {
            return true; // unknown type: assume the signature is load-bearing
        }
        for (J.Annotation annotation : md.getLeadingAnnotations()) {
            if ("Override".equals(annotation.getSimpleName())) {
                return true;
            }
        }
        JavaType.FullyQualified declaring = type.getDeclaringType();
        for (JavaType.FullyQualified supertype : supertypesOf(declaring)) {
            for (JavaType.Method candidate : supertype.getMethods()) {
                if (candidate.getName().equals(type.getName()) &&
                        candidate.getParameterTypes().size() == type.getParameterTypes().size()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<JavaType.FullyQualified> supertypesOf(JavaType.@Nullable FullyQualified type) {
        List<JavaType.FullyQualified> supertypes = new ArrayList<>();
        for (JavaType.FullyQualified current = type == null ? null : type.getSupertype();
             current != null; current = current.getSupertype()) {
            supertypes.add(current);
            supertypes.addAll(current.getInterfaces());
        }
        if (type != null) {
            supertypes.addAll(type.getInterfaces());
        }
        return supertypes;
    }

    private boolean hasCallerInFile(J.MethodDeclaration md) {
        JavaSourceFile sourceFile = getCursor().firstEnclosing(JavaSourceFile.class);
        if (sourceFile == null) {
            return true;
        }
        String name = md.getSimpleName();
        int arity = (int) md.getParameters().stream().filter(p -> !(p instanceof J.Empty)).count();
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean found) {
                if (name.equals(mi.getSimpleName()) && mi.getArguments().stream()
                        .filter(a -> !(a instanceof J.Empty)).count() == arity) {
                    found.set(true);
                }
                return super.visitMethodInvocation(mi, found);
            }
        }.reduce(sourceFile, new AtomicBoolean()).get();
    }

    /**
     * Wraps the whole body rather than the generated statements alone, so a local the reflection declares stays
     * in scope for the rest of the method. Everything `java.lang.reflect` throws is a
     * {@link ReflectiveOperationException}.
     */
    private J.MethodDeclaration wrapBodyInTryCatch(J.MethodDeclaration md, ExecutionContext ctx) {
        J.Block body = md.getBody();
        if (body == null || body.getStatements().isEmpty() || declaresThrowsException(md)) {
            return md;
        }
        J.MethodDeclaration templated = JavaTemplate
                .builder("try {\n} catch (ReflectiveOperationException e) {\n    throw new RuntimeException(e);\n}")
                .contextSensitive()
                .javaParser(JavaParser.fromJavaVersion())
                .build()
                .apply(updateCursor(md), md.getCoordinates().replaceBody());
        J.Try tryStatement = (J.Try) templated.getBody().getStatements().get(0);
        return templated.withBody(templated.getBody()
                .withStatements(singletonList(tryStatement.withBody(body))));
    }

    private static boolean declaresThrowsException(J.MethodDeclaration md) {
        return md.getThrows() != null && md.getThrows().stream()
                .anyMatch(j -> TypeUtils.isOfClassType(j.getType(), "java.lang.Exception") ||
                        TypeUtils.isOfClassType(j.getType(), "java.lang.Throwable"));
    }

    private J.MethodDeclaration addThrowsExceptionIfAbsent(J.MethodDeclaration md) {
        if (declaresThrowsException(md)) {
            return md;
        }
        JavaType.Class exceptionType = JavaType.ShallowClass.build("java.lang.Exception");
        return md.withThrows(ListUtils.concat(md.getThrows(),
                new J.Identifier(randomId(), Space.SINGLE_SPACE, Markers.EMPTY, emptyList(),
                        exceptionType.getClassName(), exceptionType, null)));
    }
}
