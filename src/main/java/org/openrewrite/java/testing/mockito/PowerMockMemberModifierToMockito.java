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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.openrewrite.java.testing.mockito.PowerMockitoMockStaticToMockito.classLiteral;

public class PowerMockMemberModifierToMockito extends Recipe {

    private static final String MEMBER_MODIFIER = "org.powermock.api.support.membermodification.MemberModifier";
    private static final String MEMBER_MATCHER = "org.powermock.api.support.membermodification.MemberMatcher";
    private static final String POWER_MOCKITO = "org.powermock.api.mockito.PowerMockito";

    private static final MethodMatcher STUB = new MethodMatcher(MEMBER_MODIFIER + " stub(java.lang.reflect.Method)", true);
    private static final MethodMatcher SUPPRESS = new MethodMatcher(MEMBER_MODIFIER + " suppress(java.lang.reflect.Method)", true);
    private static final MethodMatcher METHOD = new MethodMatcher(MEMBER_MATCHER + " method(java.lang.Class, java.lang.String, ..)", true);
    private static final MethodMatcher TO_RETURN = new MethodMatcher("org.powermock.api.support.membermodification.strategy.MethodStubStrategy toReturn(..)");
    private static final MethodMatcher TO_THROW = new MethodMatcher("org.powermock.api.support.membermodification.strategy.MethodStubStrategy toThrow(..)");
    private static final MethodMatcher MOCK_STATIC = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
    private static final MethodMatcher POWER_MOCK_STATIC = new MethodMatcher(POWER_MOCKITO + " mockStatic(..)");
    private static final MethodMatcher SPY_CLASS = new MethodMatcher(POWER_MOCKITO + " spy(java.lang.Class)");
    private static final AnnotationMatcher SET_UP = new AnnotationMatcher("@*..Before*");

    @Getter
    final String displayName = "Replace `MemberModifier.stub` and `suppress` of static methods with `Mockito.mockStatic()`";

    @Getter
    final String description = "Replaces `MemberModifier.stub(method(Type.class, \"name\")).toReturn(..)`, `toThrow(..)` and " +
            "`MemberModifier.suppress(method(Type.class, \"name\"))` of a static method with a spy on the static methods " +
            "of the type, as `PowerMockito.spy(Type.class)` does, and a stubbing of that one method, which " +
            "`PowerMockitoMockStaticToMockito` then migrates to a `MockedStatic`. Instance methods are left as they are, " +
            "since Mockito cannot stub a method of every instance of a class.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>(MEMBER_MODIFIER, false),
                        Preconditions.not(new KotlinFileChecker<>())
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                        J.Block b = super.visitBlock(block, ctx);
                        Cursor parent = getCursor().getParentTreeCursor();
                        J.ClassDeclaration enclosingClass = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (enclosingClass == null || !(parent.getValue() instanceof J.MethodDeclaration)) {
                            return b;
                        }
                        J.MethodDeclaration enclosingMethod = (J.MethodDeclaration) parent.getValue();
                        if (calledFromOtherMethod(enclosingClass, enclosingMethod)) {
                            // PowerMock stubs accumulate, whereas the second `mockStatic` of a type fails while the first is open
                            return b;
                        }
                        boolean inSetUp = isSetUp(enclosingMethod);
                        Set<String> mockedInSetUp = inSetUp ? new HashSet<>() : typesMockedInSetUp(enclosingClass);
                        Set<String> mockedInBlock = new HashSet<>();
                        List<Statement> statements = new ArrayList<>();
                        boolean changed = false;
                        for (Statement statement : b.getStatements()) {
                            Stubbing stubbing = Stubbing.of(statement);
                            if (stubbing == null) {
                                recordMockedTypes(statement, mockedInBlock);
                                statements.add(statement);
                                continue;
                            }
                            String type = stubbing.type.getFullyQualifiedName();
                            boolean spy = !mockedInBlock.contains(type) && !mockedInSetUp.contains(type);
                            Cursor site = new Cursor(getCursor(), statement);
                            if (spy) {
                                statements.add(spyStatic(stubbing, site, ctx));
                                mockedInBlock.add(type);
                            }
                            statements.add(stubbing.stubStatic(site, spy ? continuation(statement.getPrefix()) : statement.getPrefix(), ctx));
                            changed = true;
                        }
                        if (!changed) {
                            return b;
                        }
                        maybeAddImport(POWER_MOCKITO);
                        maybeAddImport("org.mockito.ArgumentMatchers");
                        maybeAddImport("org.mockito.Mockito");
                        maybeRemoveImport(MEMBER_MODIFIER);
                        maybeRemoveImport(MEMBER_MATCHER);
                        maybeRemoveImport(MEMBER_MODIFIER + ".stub");
                        maybeRemoveImport(MEMBER_MODIFIER + ".suppress");
                        maybeRemoveImport(MEMBER_MODIFIER + ".method");
                        maybeRemoveImport(MEMBER_MATCHER + ".method");
                        maybeRemoveImport(POWER_MOCKITO);
                        return b.withStatements(statements);
                    }

                    private Statement spyStatic(Stubbing stubbing, Cursor site, ExecutionContext ctx) {
                        Statement statement = (Statement) site.getValue();
                        return JavaTemplate.builder("PowerMockito.spy(#{any(java.lang.Class)});")
                                .contextSensitive()
                                .imports(POWER_MOCKITO)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12",
                                        "powermock-api-mockito-1", "powermock-api-support-1", "powermock-core-1", "powermock-reflect-1"))
                                .build()
                                .<J.MethodInvocation>apply(site, statement.getCoordinates().replace(), stubbing.classLiteral)
                                .withPrefix(statement.getPrefix());
                    }

                    /// The prefix of the statement that follows the one that took over the comments and blank lines
                    private Space continuation(Space prefix) {
                        String whitespace = prefix.getComments().isEmpty() ? prefix.getWhitespace() :
                                prefix.getComments().get(prefix.getComments().size() - 1).getSuffix();
                        return Space.format("\n" + whitespace.substring(whitespace.lastIndexOf('\n') + 1));
                    }

                    private boolean calledFromOtherMethod(J.ClassDeclaration classDecl, J.MethodDeclaration method) {
                        return new JavaIsoVisitor<AtomicBoolean>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, AtomicBoolean found) {
                                if (invocation.getSimpleName().equals(method.getSimpleName()) &&
                                    (invocation.getSelect() == null || invocation.getSelect() instanceof J.Identifier &&
                                                                       "this".equals(((J.Identifier) invocation.getSelect()).getSimpleName()))) {
                                    J.MethodDeclaration caller = getCursor().firstEnclosing(J.MethodDeclaration.class);
                                    if (caller != null && !caller.getId().equals(method.getId())) {
                                        found.set(true);
                                    }
                                }
                                return super.visitMethodInvocation(invocation, found);
                            }
                        }.reduce(classDecl, new AtomicBoolean()).get();
                    }

                    private boolean isSetUp(J.MethodDeclaration method) {
                        for (J.Annotation annotation : method.getLeadingAnnotations()) {
                            if (SET_UP.matches(annotation)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private final Map<UUID, Set<String>> typesMockedInSetUp = new HashMap<>();

                    private Set<String> typesMockedInSetUp(J.ClassDeclaration classDecl) {
                        return typesMockedInSetUp.computeIfAbsent(classDecl.getId(), id -> {
                            Set<String> types = new HashSet<>();
                            for (Statement member : classDecl.getBody().getStatements()) {
                                if (member instanceof J.MethodDeclaration && isSetUp((J.MethodDeclaration) member) &&
                                    ((J.MethodDeclaration) member).getBody() != null) {
                                    for (Statement statement : ((J.MethodDeclaration) member).getBody().getStatements()) {
                                        Stubbing stubbing = Stubbing.of(statement);
                                        if (stubbing != null) {
                                            types.add(stubbing.type.getFullyQualifiedName());
                                        }
                                        recordMockedTypes(statement, types);
                                    }
                                }
                            }
                            return types;
                        });
                    }

                    private void recordMockedTypes(Statement statement, Set<String> types) {
                        if (statement instanceof J.MethodInvocation) {
                            J.MethodInvocation mi = (J.MethodInvocation) statement;
                            if (MOCK_STATIC.matches(mi) || POWER_MOCK_STATIC.matches(mi) || SPY_CLASS.matches(mi)) {
                                for (Expression argument : mi.getArguments()) {
                                    JavaType.FullyQualified type = classLiteral(argument);
                                    if (type != null) {
                                        types.add(type.getFullyQualifiedName());
                                    }
                                }
                            }
                        }
                    }
                }
        );
    }

    /// `stub(method(Type.class, "name", ParameterType.class)).toReturn(value)` and its `toThrow` and `suppress` forms,
    /// where `name` is a static method of `Type` that Mockito can stub.
    private static final class Stubbing {
        final JavaType.FullyQualified type;
        final Expression classLiteral;
        final JavaType.Method method;
        final List<Expression> parameterLiterals;
        final boolean overloaded;
        final @Nullable String stubbing;
        final @Nullable Expression value;

        private Stubbing(JavaType.FullyQualified type, Expression classLiteral, JavaType.Method method,
                         List<Expression> parameterLiterals, boolean overloaded, @Nullable String stubbing, @Nullable Expression value) {
            this.overloaded = overloaded;
            this.type = type;
            this.classLiteral = classLiteral;
            this.method = method;
            this.parameterLiterals = parameterLiterals;
            this.stubbing = stubbing;
            this.value = value;
        }

        static @Nullable Stubbing of(Statement statement) {
            if (!(statement instanceof J.MethodInvocation)) {
                return null;
            }
            J.MethodInvocation mi = (J.MethodInvocation) statement;
            if (SUPPRESS.matches(mi)) {
                return of(mi.getArguments().get(0), null, null);
            }
            if ((TO_RETURN.matches(mi) || TO_THROW.matches(mi)) && mi.getSelect() instanceof J.MethodInvocation &&
                STUB.matches((J.MethodInvocation) mi.getSelect()) && mi.getArguments().size() == 1) {
                Expression value = mi.getArguments().get(0);
                return of(((J.MethodInvocation) mi.getSelect()).getArguments().get(0),
                        TO_RETURN.matches(mi) ? "thenReturn" : "thenThrow", value);
            }
            return null;
        }

        private static @Nullable Stubbing of(Expression methodArgument, @Nullable String stubbing, @Nullable Expression value) {
            if (!(methodArgument instanceof J.MethodInvocation) || !METHOD.matches((J.MethodInvocation) methodArgument)) {
                return null;
            }
            List<Expression> arguments = ((J.MethodInvocation) methodArgument).getArguments();
            Expression literal = arguments.get(0);
            JavaType.FullyQualified type = classLiteral(literal);
            if (type == null || !(arguments.get(1) instanceof J.Literal) || !(((J.Literal) arguments.get(1)).getValue() instanceof String)) {
                return null;
            }
            String name = (String) ((J.Literal) arguments.get(1)).getValue();
            List<Expression> parameterLiterals = new ArrayList<>();
            for (Expression argument : arguments.subList(2, arguments.size())) {
                if (!(argument instanceof J.FieldAccess) || !"class".equals(((J.FieldAccess) argument).getSimpleName()) ||
                    ((J.FieldAccess) argument).getTarget().getType() == null) {
                    return null;
                }
                parameterLiterals.add(argument);
            }
            JavaType.Method method = null;
            int sameName = 0;
            for (JavaType.Method candidate : type.getMethods()) {
                if (!candidate.getName().equals(name)) {
                    continue;
                }
                sameName++;
                if (parameterLiterals.isEmpty() ? method == null : parametersMatch(candidate, parameterLiterals)) {
                    if (method != null) {
                        return null; // PowerMock refuses to pick one of several methods too
                    }
                    method = candidate;
                }
            }
            boolean overloaded = sameName > 1;
            if (method == null || !method.hasFlags(Flag.Static) || method.hasFlags(Flag.Private) || method.hasFlags(Flag.Varargs) ||
                !parameterLiterals.isEmpty() && method.getParameterTypes().size() != parameterLiterals.size()) {
                return null;
            }
            if (parameterLiterals.isEmpty() && sameName > 1) {
                return null; // PowerMock would not pick one of the overloads either
            }
            if ("thenThrow".equals(stubbing) && !canThrow(method, value)) {
                return null;
            }
            return new Stubbing(type, literal, method, parameterLiterals, overloaded, stubbing, value);
        }

        private static boolean parametersMatch(JavaType.Method candidate, List<Expression> parameterLiterals) {
            if (candidate.getParameterTypes().size() != parameterLiterals.size()) {
                return false;
            }
            for (int i = 0; i < parameterLiterals.size(); i++) {
                String expected = erasure(candidate.getParameterTypes().get(i));
                if (expected == null || !expected.equals(erasure(((J.FieldAccess) parameterLiterals.get(i)).getTarget().getType()))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean canThrow(JavaType.Method method, @Nullable Expression thrown) {
            JavaType type = thrown == null ? null : thrown.getType();
            if (TypeUtils.asFullyQualified(type) == null) {
                return false;
            }
            if (TypeUtils.isAssignableTo("java.lang.RuntimeException", type) || TypeUtils.isAssignableTo("java.lang.Error", type)) {
                return true;
            }
            for (JavaType declared : method.getThrownExceptions()) {
                if (TypeUtils.isAssignableTo(declared, type)) {
                    return true;
                }
            }
            return false; // Mockito rejects a checked exception that the method does not declare
        }

        private static @Nullable String erasure(@Nullable JavaType type) {
            if (type instanceof JavaType.Primitive) {
                return ((JavaType.Primitive) type).getKeyword();
            }
            if (type instanceof JavaType.Array) {
                String element = erasure(((JavaType.Array) type).getElemType());
                return element == null ? null : element + "[]";
            }
            if (type instanceof JavaType.FullyQualified) {
                return ((JavaType.FullyQualified) type).getFullyQualifiedName();
            }
            return null;
        }

        /// `PowerMockito.when(Type.class, "name", ArgumentMatchers.any()).thenReturn(value)`, which
        /// `PowerMockitoMockStaticToMockito` migrates to `mockedType.when(() -> Type.name(any(..)))`.
        Statement stubStatic(Cursor site, Space prefix, ExecutionContext ctx) {
            Statement statement = (Statement) site.getValue();
            List<String> imports = new ArrayList<>(Arrays.asList(POWER_MOCKITO, "org.mockito.ArgumentMatchers", "org.mockito.Mockito"));
            StringBuilder template = new StringBuilder("PowerMockito.when(#{any(java.lang.Class)}, \"").append(method.getName()).append('"');
            List<Object> parameters = new ArrayList<>();
            parameters.add(classLiteral);
            for (int i = 0; i < method.getParameterTypes().size(); i++) {
                JavaType parameter = method.getParameterTypes().get(i);
                template.append(", ").append("ArgumentMatchers.");
                if (parameter instanceof JavaType.Primitive) {
                    template.append("any").append(primitiveMatcher((JavaType.Primitive) parameter)).append("()");
                } else {
                    // `any()` rather than `any(Type.class)`, as the latter does not match null, which PowerMock does
                    if (overloaded) {
                        // Names the parameter type, so that the call does not turn ambiguous between the overloads
                        JavaType witnessed = ((J.FieldAccess) parameterLiterals.get(i)).getTarget().getType();
                        while (witnessed instanceof JavaType.Array) {
                            witnessed = ((JavaType.Array) witnessed).getElemType();
                        }
                        if (witnessed instanceof JavaType.FullyQualified &&
                            !((JavaType.FullyQualified) witnessed).getPackageName().equals("java.lang")) {
                            imports.add(((JavaType.FullyQualified) witnessed).getFullyQualifiedName().replace('$', '.'));
                        }
                        template.append('<').append(((J.FieldAccess) parameterLiterals.get(i)).getTarget().withPrefix(Space.EMPTY).printTrimmed(site)).append('>');
                    }
                    template.append("any()");
                }
            }
            template.append(')');
            if (stubbing == null) {
                template.append(".thenAnswer(Mockito.RETURNS_DEFAULTS)");
            } else {
                template.append('.').append(stubbing).append("(#{any()})");
                parameters.add(value);
            }
            return JavaTemplate.builder(template.append(';').toString())
                    .contextSensitive()
                    .imports(imports.toArray(new String[0]))
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12",
                            "powermock-api-mockito-1", "powermock-api-support-1", "powermock-core-1", "powermock-reflect-1"))
                    .build()
                    .<J.MethodInvocation>apply(site, statement.getCoordinates().replace(), parameters.toArray())
                    .withPrefix(prefix);
        }

        private static String primitiveMatcher(JavaType.Primitive type) {
            switch (type) {
                case Boolean:
                    return "Boolean";
                case Byte:
                    return "Byte";
                case Char:
                    return "Char";
                case Double:
                    return "Double";
                case Float:
                    return "Float";
                case Int:
                    return "Int";
                case Long:
                    return "Long";
                default:
                    return "Short";
            }
        }
    }
}
