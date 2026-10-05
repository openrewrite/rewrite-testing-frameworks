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
package org.openrewrite.java.testing.junit5;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.junit5.SystemRules.*;

public class SystemRulesToSystemStubs extends Recipe {
    private static final String STUBS = "uk.org.webcompere.systemstubs";
    private static final String SYSTEM_PROPERTIES = STUBS + ".properties.SystemProperties";
    private static final String SYSTEM_OUT = STUBS + ".stream.SystemOut";
    private static final String SYSTEM_ERR = STUBS + ".stream.SystemErr";
    private static final String SYSTEM_IN = STUBS + ".stream.SystemIn";
    private static final String PROPERTY_SOURCE = STUBS + ".resource.PropertySource";
    private static final String NOOP_STREAM = STUBS + ".stream.output.NoopStream";
    private static final String DISALLOW_WRITE_STREAM = STUBS + ".stream.output.DisallowWriteStream";
    private static final String OUTPUT_FACTORIES = STUBS + ".stream.output.OutputFactories";
    private static final String LINES_ALT_STREAM = STUBS + ".stream.input.LinesAltStream";
    private static final String TEXT_ALT_STREAM = STUBS + ".stream.input.TextAltStream";
    private static final String SYSTEM_STUB = STUBS + ".jupiter.SystemStub";
    private static final String SYSTEM_STUBS_EXTENSION = STUBS + ".jupiter.SystemStubsExtension";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String LOG_MODE = PACKAGE + ".LogMode";

    private static final AnnotationMatcher EXTEND_WITH_SYSTEM_STUBS = new AnnotationMatcher("@" + EXTEND_WITH + "(" + SYSTEM_STUBS_EXTENSION + ".class)");
    private static final String ADD_EXTENSION = "addSystemStubsExtension";
    private static final String CANNOT_MIGRATE_COMMENT = " TODO Migrate by hand to System Stubs: this rule is used in a way that has no direct System Stubs equivalent.";

    @Getter
    final String displayName = "Migrate System Rules to System Stubs";

    @Getter
    final String description = "Replaces System Rules' `ProvideSystemProperty`, `ClearSystemProperties`, `RestoreSystemProperties`, `SystemOutRule`, `SystemErrRule`, `StandardOutputStreamLog`, `StandardErrorStreamLog`, `DisallowWriteToSystemOut`, `DisallowWriteToSystemErr` and `TextFromStandardInputStream` rules with `@SystemStub` fields of the System Stubs JUnit Jupiter extension. A rule is only migrated when every use of it has a System Stubs equivalent; other rules get a `TODO` comment.";

    @RequiredArgsConstructor
    private enum Kind {
        PROVIDE_PROPERTY("ProvideSystemProperty", SYSTEM_PROPERTIES),
        CLEAR_PROPERTIES("ClearSystemProperties", SYSTEM_PROPERTIES),
        RESTORE_PROPERTIES("RestoreSystemProperties", SYSTEM_PROPERTIES),
        OUT_RULE("SystemOutRule", SYSTEM_OUT),
        ERR_RULE("SystemErrRule", SYSTEM_ERR),
        OUT_LOG("StandardOutputStreamLog", SYSTEM_OUT),
        ERR_LOG("StandardErrorStreamLog", SYSTEM_ERR),
        OUT_DISALLOW("DisallowWriteToSystemOut", SYSTEM_OUT),
        ERR_DISALLOW("DisallowWriteToSystemErr", SYSTEM_ERR),
        STDIN("TextFromStandardInputStream", SYSTEM_IN);

        final String simpleName;
        final String stub;

        String rule() {
            return PACKAGE + "." + simpleName;
        }

        String stubSimpleName() {
            return stub.substring(stub.lastIndexOf('.') + 1);
        }

        boolean isOutputRule() {
            return this == OUT_RULE || this == ERR_RULE;
        }

        static @Nullable Kind of(@Nullable String fqn) {
            for (Kind kind : values()) {
                if (kind.rule().equals(fqn)) {
                    return kind;
                }
            }
            return null;
        }
    }

    private static class Migration {
        final Kind kind;
        final Set<String> imports = new LinkedHashSet<>();
        String initializer = "";
        List<Expression> initializerArguments = new ArrayList<>();
        boolean logEnabledByInitializer;
        boolean muted;
        boolean logEnabledByTest;

        Migration(Kind kind) {
            this.kind = kind;
            imports.add(kind.stub);
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PACKAGE + ".*", false), new JavaVisitor<ExecutionContext>() {
            private final Map<JavaType.Variable, Migration> migrations = new HashMap<>();
            private final Map<JavaType.Variable, String> todos = new HashMap<>();

            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                migrations.clear();
                todos.clear();
                Cursor root = new Cursor(null, Cursor.ROOT_VALUE);
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations field, Integer p) {
                        Kind kind = Kind.of(fieldTypeName(field));
                        JavaType.Variable variable = field.getVariables().get(0).getVariableType();
                        if (kind != null && variable != null && isRuleField(field) &&
                            getCursor().getParentTreeCursor().getParentTreeCursor().getValue() instanceof J.ClassDeclaration) {
                            if (mayBeUsedElsewhere(field, getCursor())) {
                                todos.put(variable, MAY_BE_USED_ELSEWHERE_COMMENT);
                                return field;
                            }
                            Migration migration = plan(kind, field.getVariables().get(0).getInitializer(), references(cu, root, variable));
                            if (migration == null) {
                                todos.put(variable, CANNOT_MIGRATE_COMMENT);
                            } else {
                                migrations.put(variable, migration);
                            }
                        }
                        return field;
                    }
                }.visit(cu, 0, root);
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
                if (getCursor().getMessage(ADD_EXTENSION) == null ||
                    service(AnnotationService.class).matches(updateCursor(cd), EXTEND_WITH_SYSTEM_STUBS)) {
                    return cd;
                }
                maybeAddImport(EXTEND_WITH);
                maybeAddImport(SYSTEM_STUBS_EXTENSION);
                return JavaTemplate.builder("@ExtendWith(SystemStubsExtension.class)")
                        .imports(EXTEND_WITH, SYSTEM_STUBS_EXTENSION)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-jupiter-2", "junit-jupiter-api-5"))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }

            @Override
            public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = (J.VariableDeclarations) super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.Variable variable = multiVariable.getVariables().get(0).getVariableType();
                if (variable == null || !(getCursor().getParentTreeCursor().getValue() instanceof J.Block)) {
                    return vd;
                }
                if (todos.containsKey(variable)) {
                    return withTodo(vd, todos.get(variable));
                }
                Migration migration = migrations.get(variable);
                if (migration == null) {
                    return vd;
                }

                J.VariableDeclarations.NamedVariable namedVariable = vd.getVariables().get(0);
                Expression oldInitializer = Objects.requireNonNull(namedVariable.getInitializer());
                Expression initializer = JavaTemplate.builder(migration.initializer)
                        .imports(migration.imports.toArray(new String[0]))
                        .staticImports(OUTPUT_FACTORIES + ".tapAndOutput")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-core-2"))
                        .build()
                        .apply(new Cursor(new Cursor(getCursor(), namedVariable), oldInitializer),
                                oldInitializer.getCoordinates().replace(), migration.initializerArguments.toArray());
                JavaType stubType = initializer.getType() == null ? JavaType.ShallowClass.build(migration.kind.stub) : initializer.getType();

                vd = vd.withLeadingAnnotations(ListUtils.map(vd.getLeadingAnnotations(), annotation -> {
                    if (!TypeUtils.isOfClassType(annotation.getType(), RULE) && !TypeUtils.isOfClassType(annotation.getType(), CLASS_RULE)) {
                        return annotation;
                    }
                    return JavaTemplate.builder("@SystemStub")
                            .imports(SYSTEM_STUB)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-jupiter-2"))
                            .build()
                            .apply(new Cursor(getCursor(), annotation), annotation.getCoordinates().replace());
                }));
                TypeTree typeExpression = vd.getTypeExpression();
                if (typeExpression != null) {
                    vd = vd.withTypeExpression(new J.Identifier(randomId(), typeExpression.getPrefix(), Markers.EMPTY,
                            emptyList(), migration.kind.stubSimpleName(), stubType, null));
                }
                vd = vd.withVariables(ListUtils.map(vd.getVariables(), v -> v
                        .withInitializer(initializer)
                        .withVariableType(v.getVariableType() == null ? null : v.getVariableType().withType(stubType))));

                maybeAddImport(SYSTEM_STUB);
                for (String type : migration.imports) {
                    maybeAddImport(type);
                }
                if (migration.initializer.contains("tapAndOutput")) {
                    maybeAddImport(OUTPUT_FACTORIES, "tapAndOutput");
                }
                maybeRemoveImport(migration.kind.rule());
                String supertype = declaredSupertype(multiVariable);
                if (supertype != null) {
                    maybeRemoveImport(supertype);
                }
                maybeRemoveImport(LOG_MODE);
                maybeRemoveImport(RULE);
                maybeRemoveImport(CLASS_RULE);
                getCursor().putMessageOnFirstEnclosing(J.ClassDeclaration.class, ADD_EXTENSION, true);
                return vd;
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = (J.Identifier) super.visitIdentifier(identifier, ctx);
                JavaType.Variable fieldType = id.getFieldType();
                Migration migration = fieldType == null ? null : migrations.get(fieldType);
                if (migration == null) {
                    return id;
                }
                JavaType stubType = JavaType.ShallowClass.build(migration.kind.stub);
                return id.withType(stubType).withFieldType(fieldType.withType(stubType));
            }

            @Override
            public @Nullable J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                JavaType.Variable field = referencedField(method.getSelect());
                Migration migration = field == null ? null : migrations.get(field);
                if (migration == null || m.getSelect() == null) {
                    return m;
                }

                String stub = "#{any(" + migration.kind.stub + ")}";
                List<Expression> arguments = arguments(m);
                List<Object> parameters = new ArrayList<>();
                parameters.add(m.getSelect());
                String template;
                switch (m.getSimpleName()) {
                    case "setProperty":
                    case "and":
                        if (J.Literal.isLiteralValue(arguments.get(1), null)) {
                            template = stub + ".remove(#{any(java.lang.String)})";
                            parameters.add(arguments.get(0));
                        } else {
                            template = stub + ".set(#{any(java.lang.String)}, #{any(java.lang.String)})";
                            parameters.addAll(arguments);
                        }
                        break;
                    case "clearProperty":
                        template = stub + ".remove(#{any(java.lang.String)})";
                        parameters.addAll(arguments);
                        break;
                    case "add":
                        return null;
                    case "enableLog":
                        if (migration.logEnabledByInitializer) {
                            return null;
                        }
                        template = stub + ".clear()";
                        break;
                    case "getLog":
                        template = stub + ".getText()";
                        break;
                    case "getLogWithNormalizedLineSeparator":
                        template = stub + ".getText().replace(System.lineSeparator(), \"\\n\")";
                        break;
                    case "getLogAsBytes":
                        template = stub + ".getText().getBytes()";
                        break;
                    case "clear":
                    case "clearLog":
                        template = stub + ".clear()";
                        break;
                    case "provideLines":
                        maybeAddImport(LINES_ALT_STREAM);
                        template = stub + ".setInputStream(new LinesAltStream(" + placeholders(arguments) + "))";
                        parameters.addAll(arguments);
                        break;
                    case "provideText":
                        maybeAddImport(TEXT_ALT_STREAM);
                        template = stub + ".setInputStream(new TextAltStream(#{any(java.lang.String)}))";
                        parameters.addAll(arguments);
                        break;
                    case "throwExceptionOnInputEnd":
                        String exception = TypeUtils.isAssignableTo("java.io.IOException", arguments.get(0).getType()) ?
                                "java.io.IOException" : "java.lang.RuntimeException";
                        template = stub + ".andExceptionThrownOnInputEnd(#{any(" + exception + ")})";
                        parameters.addAll(arguments);
                        break;
                    default:
                        return m;
                }
                return JavaTemplate.builder(template)
                        .imports(LINES_ALT_STREAM, TEXT_ALT_STREAM)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "system-stubs-core-2"))
                        .build()
                        .apply(getCursor(), m.getCoordinates().replace(), parameters.toArray());
            }
        });
    }

    private static String placeholders(List<Expression> arguments) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Expression argument : arguments) {
            joiner.add(argument.getType() instanceof JavaType.Array ? "#{anyArray(java.lang.String)}" : "#{any(java.lang.String)}");
        }
        return joiner.toString();
    }

    // System Rules writes through to the original stream unless muted, and only captures once logging is enabled
    private static String outputRuleInitializer(Migration migration) {
        String stream = migration.kind.stubSimpleName();
        if (!migration.muted) {
            return "new " + stream + "(tapAndOutput())";
        }
        if (migration.logEnabledByInitializer || migration.logEnabledByTest) {
            return "new " + stream + "()";
        }
        migration.imports.add(NOOP_STREAM);
        return "new " + stream + "(new NoopStream())";
    }

    private static @Nullable Migration plan(Kind kind, @Nullable Expression initializer, List<Cursor> references) {
        if (initializer == null) {
            return null;
        }
        Migration migration = new Migration(kind);
        if (!planInitializer(migration, initializer)) {
            return null;
        }
        for (Cursor reference : references) {
            J.MethodInvocation invocation = invokedOn(reference);
            if (invocation == null || !isSupported(migration, invocation, reference.getParentTreeCursor())) {
                return null;
            }
        }
        if (migration.kind.isOutputRule()) {
            migration.initializer = outputRuleInitializer(migration);
        }
        return migration;
    }

    private static boolean isSupported(Migration migration, J.MethodInvocation invocation, Cursor invocationCursor) {
        List<Expression> arguments = arguments(invocation);
        String name = invocation.getSimpleName();
        switch (migration.kind) {
            case PROVIDE_PROPERTY:
                return ("setProperty".equals(name) || "and".equals(name)) && isStatement(invocationCursor);
            case CLEAR_PROPERTIES:
                return "clearProperty".equals(name);
            case RESTORE_PROPERTIES:
                return "add".equals(name) && isStatement(invocationCursor);
            case OUT_RULE:
            case ERR_RULE:
                if ("enableLog".equals(name)) {
                    migration.logEnabledByTest = true;
                    return isStatement(invocationCursor);
                }
                return "getLog".equals(name) || "getLogWithNormalizedLineSeparator".equals(name) ||
                       "clearLog".equals(name) || "getLogAsBytes".equals(name);
            case OUT_LOG:
            case ERR_LOG:
                return "getLog".equals(name) || "clear".equals(name);
            case STDIN:
                if ("provideLines".equals(name)) {
                    return true;
                }
                if ("provideText".equals(name)) {
                    return arguments.size() == 1 && !(arguments.get(0).getType() instanceof JavaType.Array);
                }
                return "throwExceptionOnInputEnd".equals(name) && followsProvidedInput(invocation, invocationCursor);
            default:
                return false;
        }
    }

    // System Stubs drops the end-of-input exception whenever new input is set, which System Rules kept
    private static boolean followsProvidedInput(J.MethodInvocation invocation, Cursor invocationCursor) {
        if (!isStatement(invocationCursor)) {
            return false;
        }
        JavaType.Variable field = referencedField(invocation.getSelect());
        List<Statement> statements = invocationCursor.getParentTreeCursor().<J.Block>getValue().getStatements();
        boolean providedBefore = false;
        boolean seen = false;
        for (Statement statement : statements) {
            if (statement == invocation) {
                seen = true;
            } else if (statement instanceof J.MethodInvocation &&
                       Objects.equals(field, referencedField(((J.MethodInvocation) statement).getSelect())) &&
                       ((J.MethodInvocation) statement).getSimpleName().startsWith("provide")) {
                if (seen) {
                    return false;
                }
                providedBefore = true;
            }
        }
        return providedBefore;
    }

    private static boolean planInitializer(Migration migration, Expression initializer) {
        switch (migration.kind) {
            case PROVIDE_PROPERTY:
                return planProvideProperty(migration, initializer);
            case CLEAR_PROPERTIES:
                if (!(initializer instanceof J.NewClass)) {
                    return false;
                }
                StringBuilder clear = new StringBuilder("new SystemProperties()");
                for (Expression key : arguments((J.NewClass) initializer)) {
                    if (key.getType() instanceof JavaType.Array) {
                        return false;
                    }
                    clear.append(".remove(#{any(java.lang.String)})");
                    migration.initializerArguments.add(key);
                }
                migration.initializer = clear.toString();
                return true;
            case RESTORE_PROPERTIES:
                migration.initializer = "new SystemProperties()";
                return initializer instanceof J.NewClass;
            case OUT_RULE:
            case ERR_RULE:
                Expression current = initializer;
                while (current instanceof J.MethodInvocation) {
                    J.MethodInvocation configuration = (J.MethodInvocation) current;
                    if ("enableLog".equals(configuration.getSimpleName())) {
                        migration.logEnabledByInitializer = true;
                    } else if ("mute".equals(configuration.getSimpleName()) ||
                               "muteForSuccessfulTests".equals(configuration.getSimpleName())) {
                        migration.muted = true;
                    } else {
                        return false;
                    }
                    current = configuration.getSelect();
                }
                return current instanceof J.NewClass;
            case OUT_LOG:
            case ERR_LOG:
                if (!(initializer instanceof J.NewClass)) {
                    return false;
                }
                List<Expression> logMode = arguments((J.NewClass) initializer);
                String stream = migration.kind.stubSimpleName();
                if (logMode.isEmpty() || isLogMode(logMode.get(0), "LOG_AND_WRITE_TO_STREAM")) {
                    migration.initializer = "new " + stream + "(tapAndOutput())";
                    return true;
                }
                migration.initializer = "new " + stream + "()";
                return isLogMode(logMode.get(0), "LOG_ONLY");
            case OUT_DISALLOW:
            case ERR_DISALLOW:
                migration.initializer = "new " + migration.kind.stubSimpleName() + "(new DisallowWriteStream())";
                migration.imports.add(DISALLOW_WRITE_STREAM);
                return initializer instanceof J.NewClass;
            case STDIN:
                if (initializer instanceof J.MethodInvocation &&
                    "emptyStandardInputStream".equals(((J.MethodInvocation) initializer).getSimpleName())) {
                    migration.initializer = "new SystemIn()";
                    return true;
                }
                if (initializer instanceof J.NewClass && arguments((J.NewClass) initializer).size() == 1) {
                    migration.initializer = "new SystemIn(new TextAltStream(#{any(java.lang.String)}))";
                    migration.initializerArguments.add(arguments((J.NewClass) initializer).get(0));
                    migration.imports.add(TEXT_ALT_STREAM);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private static boolean planProvideProperty(Migration migration, Expression initializer) {
        if (initializer instanceof J.MethodInvocation && !"and".equals(((J.MethodInvocation) initializer).getSimpleName())) {
            J.MethodInvocation factory = (J.MethodInvocation) initializer;
            Expression source = arguments(factory).get(0);
            migration.imports.add(PROPERTY_SOURCE);
            if ("fromFile".equals(factory.getSimpleName())) {
                migration.initializer = "new SystemProperties(PropertySource.fromFile(#{any(java.lang.String)}))";
                migration.initializerArguments.add(source);
                return true;
            }
            // System Rules resolves resources against its own package, System Stubs against the class loader root
            if ("fromResource".equals(factory.getSimpleName()) && isStringLiteral(source) &&
                String.valueOf(((J.Literal) source).getValue()).startsWith("/")) {
                migration.initializer = "new SystemProperties(PropertySource.fromResource(#{any(java.lang.String)}))";
                String resource = String.valueOf(((J.Literal) source).getValue()).substring(1);
                migration.initializerArguments.add(((J.Literal) source)
                        .withValue(resource)
                        .withValueSource(Objects.requireNonNull(((J.Literal) source).getValueSource()).replaceFirst("^\"/", "\"")));
                return true;
            }
            return false;
        }

        Deque<List<Expression>> pairs = new ArrayDeque<>();
        Expression current = initializer;
        while (current instanceof J.MethodInvocation && "and".equals(((J.MethodInvocation) current).getSimpleName())) {
            pairs.push(arguments((J.MethodInvocation) current));
            current = ((J.MethodInvocation) current).getSelect();
        }
        if (!(current instanceof J.NewClass)) {
            return false;
        }
        List<Expression> constructorArguments = arguments((J.NewClass) current);
        if (constructorArguments.size() == 2) {
            pairs.push(constructorArguments);
        }

        StringBuilder template = new StringBuilder("new SystemProperties(");
        boolean first = true;
        for (List<Expression> pair : pairs) {
            if (J.Literal.isLiteralValue(pair.get(1), null)) {
                template.append(first ? ")" : "").append(".remove(#{any(java.lang.String)})");
                migration.initializerArguments.add(pair.get(0));
            } else {
                template.append(first ? "" : ".set(").append("#{any(java.lang.String)}, #{any(java.lang.String)})");
                migration.initializerArguments.addAll(pair);
            }
            first = false;
        }
        migration.initializer = first ? "new SystemProperties()" : template.toString();
        return true;
    }

    private static boolean isLogMode(Expression expression, String constant) {
        JavaType.Variable variable = referencedField(expression);
        return variable != null && constant.equals(variable.getName()) && TypeUtils.isOfClassType(variable.getOwner(), LOG_MODE);
    }
}
