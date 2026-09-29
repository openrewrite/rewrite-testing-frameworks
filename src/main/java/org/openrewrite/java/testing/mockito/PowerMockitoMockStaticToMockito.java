/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.testing.mockito.ScopedMocks.MOCKED_STATIC;

public class PowerMockitoMockStaticToMockito extends Recipe {

    private static final String POWER_MOCKITO = "org.powermock.api.mockito.PowerMockito";

    private static final MethodMatcher MOCK_STATIC = new MethodMatcher(POWER_MOCKITO + " mockStatic(..)");
    private static final MethodMatcher MOCKITO_MOCK_STATIC = new MethodMatcher("org.mockito.Mockito mockStatic(..)");
    private static final MethodMatcher SPY_CLASS = new MethodMatcher(POWER_MOCKITO + " spy(java.lang.Class)");
    private static final MethodMatcher VERIFY_STATIC = new MethodMatcher(POWER_MOCKITO + " verifyStatic(..)");
    private static final MethodMatcher WHEN = new MethodMatcher(POWER_MOCKITO + " when(..)");
    private static final MethodMatcher MOCKITO_WHEN = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final MethodMatcher MOCKITO_VERIFY = new MethodMatcher("org.mockito.Mockito verify(..)");
    private static final MethodMatcher DYNAMIC_WHEN = new MethodMatcher(POWER_MOCKITO + " when(java.lang.Class, String, ..)");
    private static final MethodMatcher MOCKITO_DYNAMIC_WHEN = new MethodMatcher("org.mockito.Mockito when(java.lang.Class, String, ..)");
    private static final MethodMatcher MOCKITO_METHOD = new MethodMatcher("org.mockito..* *(..)");
    private static final AnnotationMatcher PREPARE_FOR_TEST =
            new AnnotationMatcher("@org.powermock.core.classloader.annotations.PrepareForTest");

    @Getter
    final String displayName = "Replace `PowerMock.mockStatic()` with `Mockito.mockStatic()`";

    @Getter
    final String description = "Replaces `PowerMockito.mockStatic()` by `Mockito.mockStatic()`, assigning the resulting " +
            "`MockedStatic` to a field that is closed after each test, so the static mock stays active for exactly " +
            "the same part of the test as before. Also migrates `PowerMockito.verifyStatic()` and static stubbing, " +
            "and removes the `@PrepareForTest` annotation.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new UsesType<>("org.powermock..*", false),
                        Preconditions.not(new KotlinFileChecker<>())
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                        cd = removePrepareForTest(cd);

                        TestFramework framework = TestFramework.detect(getCursor().firstEnclosingOrThrow(JavaSourceFile.class));
                        ScopedMocks mocks = collectStaticMocks(cd, framework);
                        if (mocks.isEmpty()) {
                            return cd;
                        }

                        cd = mocks.declareFields(this, cd, ctx);
                        StaticMockUsageVisitor usages = new StaticMockUsageVisitor(cd, mocks);
                        cd = (J.ClassDeclaration) usages.visitNonNull(cd, ctx, getCursor().getParentOrThrow());
                        cd = mocks.closeAfterEachTest(this, cd, "tearDownStaticMocks", ctx);
                        if (usages.spiedStatically) {
                            maybeAddImport("org.mockito.Mockito");
                        }
                        maybeRemoveImport(POWER_MOCKITO);

                        doAfterVisit(new ChangeMethodTargetToStatic(POWER_MOCKITO + " mockStatic(..)",
                                "org.mockito.Mockito", MOCKED_STATIC, null, false).getVisitor());
                        return cd;
                    }

                    private J.ClassDeclaration removePrepareForTest(J.ClassDeclaration cd) {
                        for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                            if (PREPARE_FOR_TEST.matches(annotation)) {
                                List<JavaType.FullyQualified> prepared = classLiterals(annotation.getArguments());
                                if (!prepared.isEmpty()) {
                                    doAfterVisit(new RemoveAnnotationVisitor(PREPARE_FOR_TEST));
                                    prepared.forEach(this::maybeRemoveImport);
                                }
                            }
                        }
                        return cd;
                    }
                }
        );
    }

    private static ScopedMocks collectStaticMocks(J.ClassDeclaration cd, TestFramework framework) {
        ScopedMocks mocks = new ScopedMocks(cd, framework);
        List<JavaType.FullyQualified> usedStatically = new ArrayList<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Integer p) {
                return classDecl == cd ? super.visitClassDeclaration(classDecl, p) : classDecl;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, p);
                if (isStaticMockStatement(mi, getCursor())) {
                    for (Expression argument : mi.getArguments()) {
                        JavaType.FullyQualified type = classLiteral(argument);
                        if (type != null) {
                            mocks.register(MOCKED_STATIC, type, "mocked", getCursor());
                        }
                    }
                } else if (ScopedMocks.isStaticContext(getCursor())) {
                    if (mi.getMethodType() != null && mi.getMethodType().hasFlags(Flag.Static)) {
                        usedStatically.add(mi.getMethodType().getDeclaringType());
                    }
                    for (Expression argument : mi.getArguments()) {
                        JavaType.FullyQualified type = classLiteral(argument);
                        if (type != null) {
                            usedStatically.add(type);
                        }
                    }
                }
                return mi;
            }
        }.visit(cd, 0);
        usedStatically.forEach(type -> mocks.requireStatic(MOCKED_STATIC, type));
        return mocks;
    }

    private static List<JavaType.FullyQualified> classLiterals(@Nullable List<Expression> arguments) {
        List<JavaType.FullyQualified> types = new ArrayList<>();
        if (arguments == null) {
            return types;
        }
        for (Expression argument : arguments) {
            if (argument instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) argument;
                if (!(assignment.getVariable() instanceof J.Identifier) ||
                    !"value".equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                    continue;
                }
                argument = assignment.getAssignment();
            }
            if (argument instanceof J.NewArray) {
                types.addAll(classLiterals(((J.NewArray) argument).getInitializer()));
            } else {
                JavaType.FullyQualified type = classLiteral(argument);
                if (type != null) {
                    types.add(type);
                }
            }
        }
        return types;
    }

    static JavaType.@Nullable FullyQualified classLiteral(Expression expression) {
        if (expression instanceof J.FieldAccess && "class".equals(((J.FieldAccess) expression).getSimpleName())) {
            return TypeUtils.asFullyQualified(((J.FieldAccess) expression).getTarget().getType());
        }
        return null;
    }

    private static boolean isStaticMockStatement(J.MethodInvocation mi, Cursor cursor) {
        return (MOCK_STATIC.matches(mi) || MOCKITO_MOCK_STATIC.matches(mi) || SPY_CLASS.matches(mi)) &&
               cursor.getParentTreeCursor().getValue() instanceof J.Block;
    }

    private static class StaticMockUsageVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.ClassDeclaration classDecl;
        private final ScopedMocks mocks;
        boolean spiedStatically;

        StaticMockUsageVisitor(J.ClassDeclaration classDecl, ScopedMocks mocks) {
            this.classDecl = classDecl;
            this.mocks = mocks;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
            return cd == classDecl ? super.visitClassDeclaration(cd, ctx) : cd;
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            List<Statement> statements = b.getStatements();
            List<Statement> rewritten = new ArrayList<>(statements.size());
            for (int i = 0; i < statements.size(); i++) {
                Statement statement = statements.get(i);
                Statement next = i + 1 < statements.size() ? statements.get(i + 1) : null;
                if (statement instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) statement;
                    if (MOCK_STATIC.matches(mi) || MOCKITO_MOCK_STATIC.matches(mi) || SPY_CLASS.matches(mi)) {
                        rewritten.addAll(assignStaticMocks(mi, ctx));
                        continue;
                    }
                    if (VERIFY_STATIC.matches(mi) && next instanceof J.MethodInvocation) {
                        Statement verification = verifyStatic(mi, (J.MethodInvocation) next, b, ctx);
                        if (verification != null) {
                            rewritten.add(verification);
                            i++;
                            continue;
                        }
                    }
                }
                rewritten.add(statement);
            }
            return b.withStatements(rewritten);
        }

        private List<Statement> assignStaticMocks(J.MethodInvocation mi, ExecutionContext ctx) {
            List<Expression> extraArguments = new ArrayList<>();
            List<ScopedMocks.ScopedMock> assigned = new ArrayList<>();
            List<Expression> classArguments = new ArrayList<>();
            for (Expression argument : mi.getArguments()) {
                ScopedMocks.ScopedMock mock = mocks.get(MOCKED_STATIC, classLiteral(argument));
                if (mock != null) {
                    assigned.add(mock);
                    classArguments.add(argument);
                } else {
                    extraArguments.add(argument);
                }
            }
            if (assigned.isEmpty() || (!extraArguments.isEmpty() && assigned.size() > 1)) {
                return singletonList(mi);
            }

            boolean spy = SPY_CLASS.matches(mi);
            spiedStatically |= spy;
            Cursor site = new Cursor(getCursor(), mi);
            Space prefix = mi.getPrefix().withComments(emptyList());
            List<Statement> assignments = new ArrayList<>(assigned.size());
            for (int i = 0; i < assigned.size(); i++) {
                ScopedMocks.ScopedMock mock = assigned.get(i);
                J.MethodInvocation mockStatic = mi.withId(randomId())
                        .withArguments(ListUtils.concatAll(
                                singletonList(classArguments.get(i).withPrefix(Space.EMPTY)),
                                ListUtils.mapFirst(extraArguments, a -> a.withPrefix(Space.SINGLE_SPACE))));
                if (spy) {
                    mockStatic = JavaTemplate.builder("Mockito.mockStatic(#{any(java.lang.Class)}, Mockito.CALLS_REAL_METHODS)")
                            .imports("org.mockito.Mockito")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                            .build()
                            .apply(site, mi.getCoordinates().replace(), classArguments.get(i));
                }
                Statement close = mocks.closeIfOpen(mock, site, mi, ctx);
                if (close != null) {
                    assignments.add(close.withPrefix(prefix));
                }
                assignments.add(mocks.assign(mock, mockStatic, prefix));
            }
            assignments.set(0, assignments.get(0).withPrefix(mi.getPrefix()));
            return assignments;
        }

        private @Nullable Statement verifyStatic(J.MethodInvocation verifyStatic, J.MethodInvocation call,
                                                 J.Block block, ExecutionContext ctx) {
            JavaType.Method callType = call.getMethodType();
            if (callType == null || !callType.hasFlags(Flag.Static)) {
                return null;
            }
            Expression mode = null;
            JavaType.FullyQualified verified = callType.getDeclaringType();
            for (Expression argument : verifyStatic.getArguments()) {
                JavaType.FullyQualified literal = classLiteral(argument);
                if (literal != null) {
                    verified = literal;
                } else if (!(argument instanceof J.Empty)) {
                    mode = argument;
                }
            }
            ScopedMocks.ScopedMock mock = mocks.get(MOCKED_STATIC, verified);
            if (mock == null || !TypeUtils.isOfType(verified, callType.getDeclaringType())) {
                return null;
            }
            Cursor blockCursor = new Cursor(getCursor().getParentOrThrow(), block);
            String verification = "#{any(org.mockito.MockedStatic)}.verify(" + staticCallAsVerification(call, blockCursor) +
                                  (mode == null ? "" : ", #{any(org.mockito.verification.VerificationMode)}") + ");";
            J.MethodInvocation replaced = JavaTemplate.builder(verification)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .build()
                    .apply(new Cursor(blockCursor, verifyStatic), verifyStatic.getCoordinates().replace(),
                            mode == null ? new Object[]{mock.field()} : new Object[]{mock.field(), mode});
            return replaced.withPrefix(verifyStatic.getPrefix());
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (DYNAMIC_WHEN.matches(mi) || MOCKITO_DYNAMIC_WHEN.matches(mi)) {
                return dynamicWhen(mi, ctx);
            }
            if (WHEN.matches(mi) || MOCKITO_WHEN.matches(mi) || MOCKITO_VERIFY.matches(mi)) {
                return stubStaticCall(mi, ctx);
            }
            return mi;
        }

        private J.MethodInvocation stubStaticCall(J.MethodInvocation mi, ExecutionContext ctx) {
            if (mi.getArguments().isEmpty() || !(mi.getArguments().get(0) instanceof J.MethodInvocation)) {
                return mi;
            }
            J.MethodInvocation call = (J.MethodInvocation) mi.getArguments().get(0);
            if (MOCKITO_METHOD.matches(call) || call.getMethodType() == null || !call.getMethodType().hasFlags(Flag.Static) ||
                MockitoUtils.throwsCheckedException(call.getMethodType())) {
                return mi;
            }
            ScopedMocks.ScopedMock mock = mocks.get(MOCKED_STATIC, call.getMethodType().getDeclaringType());
            if (mock == null) {
                return mi;
            }
            List<Expression> rest = mi.getArguments().subList(1, mi.getArguments().size());
            String template = "#{any(org.mockito.MockedStatic)}." + mi.getSimpleName() + "(" +
                              staticCallAsVerification(call, getCursor()) +
                              rest.stream().map(r -> ", #{any()}").collect(joining()) + ")";
            return JavaTemplate.builder(template)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), ListUtils.concat(mock.field(), rest).toArray());
        }

        private J.MethodInvocation dynamicWhen(J.MethodInvocation mi, ExecutionContext ctx) {
            List<Expression> arguments = mi.getArguments();
            ScopedMocks.ScopedMock mock = mocks.get(MOCKED_STATIC, classLiteral(arguments.get(0)));
            if (mock == null || !(arguments.get(1) instanceof J.Literal)) {
                return mi;
            }
            String methodName = String.valueOf(((J.Literal) arguments.get(1)).getValue());
            List<Expression> methodArguments = arguments.subList(2, arguments.size());
            for (JavaType.Method method : mock.mockedType.getMethods()) {
                if (method.getName().equals(methodName) && method.hasFlags(Flag.Private)) {
                    return mi;
                }
            }
            String template = "#{any(org.mockito.MockedStatic)}.when(() -> " + mock.className + "." + methodName + "(" +
                              methodArguments.stream().map(r -> "#{any()}").collect(joining(", ")) + "))";
            return JavaTemplate.builder(template)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .build()
                    .apply(getCursor(), mi.getCoordinates().replace(), ListUtils.concat(mock.field(), methodArguments).toArray());
        }

        private static String staticCallAsVerification(J.MethodInvocation call, Cursor cursor) {
            if (call.getArguments().stream().allMatch(J.Empty.class::isInstance) && call.getSelect() instanceof TypeTree &&
                call.getTypeParameters() == null) {
                return call.getSelect().printTrimmed(cursor) + "::" + call.getSimpleName();
            }
            return "() -> " + call.printTrimmed(cursor);
        }
    }
}
