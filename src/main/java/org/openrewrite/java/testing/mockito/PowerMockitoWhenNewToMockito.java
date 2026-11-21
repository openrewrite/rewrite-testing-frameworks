/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.kotlin.KotlinFileChecker;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.testing.mockito.PowerMockitoMockStaticToMockito.classLiteral;
import static org.openrewrite.java.testing.mockito.ScopedMocks.MOCKED_CONSTRUCTION;

public class PowerMockitoWhenNewToMockito extends Recipe {

    private static final MethodMatcher WHEN_NEW = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(java.lang.Class)");
    private static final MethodMatcher WITH_ARGUMENTS = new MethodMatcher("org.powermock.api.mockito.expectation..* with*(..)");
    private static final String UNSUPPORTED = "";
    private static final MethodMatcher MATCHER = new MethodMatcher("org.mockito.ArgumentMatchers *(..)");
    private static final MethodMatcher LEGACY_MATCHER = new MethodMatcher("org.mockito.Matchers *(..)");
    private static final Map<String, String> MATCHED_TYPES = new HashMap<String, String>() {{
        put("anyString", "String");
        put("anyInt", "Integer");
        put("anyLong", "Long");
        put("anyShort", "Short");
        put("anyByte", "Byte");
        put("anyChar", "Character");
        put("anyBoolean", "Boolean");
        put("anyDouble", "Double");
        put("anyFloat", "Float");
        put("anyList", "List");
        put("anySet", "Set");
        put("anyMap", "Map");
        put("anyCollection", "Collection");
        put("anyIterable", "Iterable");
    }};
    private static final MethodMatcher VERIFY_NEW = new MethodMatcher("org.powermock.api.mockito.PowerMockito verifyNew(java.lang.Class, ..)");
    private static final MethodMatcher VERIFY_NEW_ARGUMENTS = new MethodMatcher("org.powermock.api.mockito.verification.ConstructorArgumentsVerification with*(..)");
    private static final MethodMatcher TIMES = new MethodMatcher("org.mockito.Mockito times(int)");
    private static final MethodMatcher NEVER = new MethodMatcher("org.mockito.Mockito never()");
    private static final MethodMatcher THEN_RETURN = new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)");

    @Getter
    final String displayName = "Replace `PowerMockito.whenNew` with Mockito counterpart";

    @Getter
    final String description = "Replaces `PowerMockito.whenNew(Type.class).with...().thenReturn(instance)` with " +
            "`Mockito.mockConstructionWithAnswer(Type.class, delegatesTo(instance))`, assigned to a field that is closed " +
            "after each test. Every `Type` constructed while the mock is active delegates to `instance`, so stubbing and " +
            "verification on `instance` keep working. Constructor argument matchers are not retained, and when the same " +
            "type is stubbed more than once in a method, the calls are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(new UsesMethod<>(WHEN_NEW), Preconditions.not(new KotlinFileChecker<>())),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                        TestFramework framework = TestFramework.detect(getCursor().firstEnclosingOrThrow(JavaSourceFile.class));
                        Plan plan = new Plan();
                        ScopedMocks mocks = collectConstructionMocks(cd, framework, plan);
                        if (plan.rewrite.isEmpty()) {
                            return cd;
                        }

                        cd = mocks.declareFields(this, cd, ctx);
                        cd = (J.ClassDeclaration) new WhenNewVisitor(cd, mocks, plan).visitNonNull(cd, ctx, getCursor().getParentOrThrow());
                        cd = mocks.closeAfterEachTest(this, cd, "tearDownStaticMocks", ctx);
                        maybeRemoveImport("org.powermock.api.mockito.PowerMockito");
                        maybeRemoveImport("org.powermock.api.mockito.PowerMockito.whenNew");
                        maybeAddImport("org.mockito.AdditionalAnswers");
                        maybeAddImport("org.mockito.Mockito");
                        for (String type : new String[]{"Arrays", "Collection", "Collections", "List", "Map", "Objects", "Set"}) {
                            maybeAddImport("java.util." + type);
                        }
                        maybeRemoveImport("org.powermock.api.mockito.PowerMockito.verifyNew");
                        maybeRemoveImport("org.mockito.Mockito.times");
                        maybeRemoveImport("org.mockito.Mockito.never");
                        maybeAddImport(framework.assertionsClass);
                        for (String owner : new String[]{"org.mockito.ArgumentMatchers.", "org.mockito.Matchers.", "org.mockito.Mockito."}) {
                            for (String matcher : MATCHER_NAMES) {
                                maybeRemoveImport(owner + matcher);
                            }
                        }
                        return cd;
                    }
                });
    }

    private static final List<String> MATCHER_NAMES = Stream.concat(Stream.of("any", "anyObject", "eq", "isA"),
            MATCHED_TYPES.keySet().stream()).collect(toList());

    /// `verifyNew(Type.class, mode).withX(..)` as the type, and the assertion on the number of constructions that
    /// stands for it, with its parameters; null for what a count of constructions cannot say, such as the values the
    /// constructor was called with.
    private static final class VerifyNew {
        final JavaType.FullyQualified mockedType;
        final String expectedCount;
        final @Nullable Expression times;

        VerifyNew(JavaType.FullyQualified mockedType, String expectedCount, @Nullable Expression times) {
            this.mockedType = mockedType;
            this.expectedCount = expectedCount;
            this.times = times;
        }
    }

    private static @Nullable VerifyNew verifyNew(Statement statement) {
        if (!(statement instanceof J.MethodInvocation) || !VERIFY_NEW_ARGUMENTS.matches((J.MethodInvocation) statement)) {
            return null;
        }
        J.MethodInvocation with = (J.MethodInvocation) statement;
        Expression select = with.getSelect();
        if (!(select instanceof J.MethodInvocation) || !VERIFY_NEW.matches(select)) {
            return null;
        }
        // Only the number of constructions is known, so the arguments can only be matched by their kind
        for (Expression argument : with.getArguments()) {
            if (!(argument instanceof J.Empty) && !(argument instanceof J.MethodInvocation &&
                    (MATCHER.matches(argument) || LEGACY_MATCHER.matches(argument)))) {
                return null;
            }
        }
        List<Expression> verifyNewArguments = ((J.MethodInvocation) select).getArguments();
        JavaType.FullyQualified mockedType = classLiteral(verifyNewArguments.get(0));
        if (mockedType == null) {
            return null;
        }
        if (verifyNewArguments.size() == 1) {
            return new VerifyNew(mockedType, "1", null);
        }
        Expression mode = verifyNewArguments.get(1);
        if (TIMES.matches(mode)) {
            return new VerifyNew(mockedType, "#{any(int)}", ((J.MethodInvocation) mode).getArguments().get(0));
        } else if (NEVER.matches(mode)) {
            return new VerifyNew(mockedType, "0", null);
        }
        return null;
    }

    private static final class WhenNew {
        final JavaType.FullyQualified mockedType;
        final Expression type;
        final Expression instance;
        final J.MethodInvocation with;

        WhenNew(JavaType.FullyQualified mockedType, Expression type, Expression instance, J.MethodInvocation with) {
            this.mockedType = mockedType;
            this.type = type;
            this.instance = instance;
            this.with = with;
        }
    }

    private static @Nullable WhenNew whenNew(J tree) {
        if (!(tree instanceof J.MethodInvocation) || !THEN_RETURN.matches((J.MethodInvocation) tree) ||
            ((J.MethodInvocation) tree).getArguments().size() != 1) {
            return null;
        }
        J.MethodInvocation thenReturn = (J.MethodInvocation) tree;
        Expression select = thenReturn.getSelect();
        J.MethodInvocation with = null;
        while (select instanceof J.MethodInvocation && WITH_ARGUMENTS.matches(select)) {
            with = (J.MethodInvocation) select;
            select = ((J.MethodInvocation) select).getSelect();
        }
        if (with == null || !(select instanceof J.MethodInvocation) || !WHEN_NEW.matches(select)) {
            return null;
        }
        Expression type = ((J.MethodInvocation) select).getArguments().get(0);
        JavaType.FullyQualified mockedType = classLiteral(type);
        return mockedType == null ? null : new WhenNew(mockedType, type, thenReturn.getArguments().get(0), with);
    }

    /// What to rewrite: each statement that becomes a `mockConstruction`, with the stubbings it stands for, and the
    /// statements dropped because a later one in the same block stands for them.
    private static final class Plan {
        final Map<UUID, List<WhenNew>> rewrite = new HashMap<>();
        final Set<UUID> remove = new HashSet<>();
    }

    private static ScopedMocks collectConstructionMocks(J.ClassDeclaration cd, TestFramework framework, Plan plan) {
        Map<String, Integer> stubbingsPerMethodAndType = new HashMap<>();
        List<Cursor> candidates = new ArrayList<>();
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Integer p) {
                return classDecl == cd ? super.visitClassDeclaration(classDecl, p) : classDecl;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
                if (WHEN_NEW.matches(method)) {
                    JavaType.FullyQualified mockedType = classLiteral(method.getArguments().get(0));
                    if (mockedType != null) {
                        stubbingsPerMethodAndType.merge(methodAndType(getCursor(), mockedType), 1, Integer::sum);
                    }
                } else if (getCursor().getParentTreeCursor().getValue() instanceof J.Block && whenNew(method) != null) {
                    candidates.add(getCursor());
                }
                return super.visitMethodInvocation(method, p);
            }
        }.visit(cd, 0);

        Map<String, List<Cursor>> candidatesPerMethodAndType = new LinkedHashMap<>();
        for (Cursor candidate : candidates) {
            JavaType.FullyQualified mockedType = requireNonNull(whenNew(candidate.getValue())).mockedType;
            candidatesPerMethodAndType.computeIfAbsent(methodAndType(candidate, mockedType), k -> new ArrayList<>()).add(candidate);
        }

        ScopedMocks mocks = new ScopedMocks(cd, framework);
        String contextName = usesIdentifier(cd, "context") ? "constructionContext" : "context";
        candidatesPerMethodAndType.forEach((methodAndType, group) -> {
            // Stubbings that are not plain statements of one block cannot be folded into one `MockedConstruction`
            if (stubbingsPerMethodAndType.get(methodAndType) != group.size()) {
                return;
            }
            Cursor site = group.get(group.size() - 1);
            List<WhenNew> stubbings = new ArrayList<>();
            for (Cursor candidate : group) {
                stubbings.add(requireNonNull(whenNew(candidate.getValue())));
            }
            if (group.size() > 1 && !canDispatchOnArguments(stubbings, group, contextName)) {
                return;
            }
            mocks.register(MOCKED_CONSTRUCTION, stubbings.get(0).mockedType, "mockedConstruction", site);
            plan.rewrite.put(site.<J>getValue().getId(), stubbings);
            for (Cursor candidate : group.subList(0, group.size() - 1)) {
                plan.remove.add(candidate.<J>getValue().getId());
            }
        });
        return mocks;
    }

    private static boolean usesIdentifier(J.ClassDeclaration cd, String name) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                if (name.equals(identifier.getSimpleName())) {
                    found.set(true);
                }
                return identifier;
            }
        }.reduce(cd, new AtomicBoolean()).get();
    }

    /// Several stubbings of one type become a single `MockedConstruction`, whose settings are chosen from the
    /// arguments of the construction. That takes arguments that can be compared, and values that a lambda can capture.
    private static boolean canDispatchOnArguments(List<WhenNew> stubbings, List<Cursor> sites, String contextName) {
        J.MethodDeclaration method = sites.get(0).firstEnclosing(J.MethodDeclaration.class);
        for (WhenNew stubbing : stubbings) {
            if (!(stubbing.instance instanceof J.Identifier || stubbing.instance instanceof J.FieldAccess) ||
                UNSUPPORTED.equals(constructionMatch(stubbing, contextName, new ArrayList<>())) ||
                !captureable(stubbing.instance, method) ||
                !stubbing.with.getArguments().stream().allMatch(argument -> captureable(argument, method))) {
                return false;
            }
        }
        return true;
    }

    /// A local variable a lambda captures must not be assigned again, whereas fields and constants always can be.
    private static boolean captureable(Expression expression, J.@Nullable MethodDeclaration method) {
        Set<String> locals = new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> p) {
                JavaType.Variable variable = identifier.getFieldType();
                if (variable != null && variable.getOwner() instanceof JavaType.Method) {
                    p.add(identifier.getSimpleName());
                }
                return identifier;
            }
        }.reduce(expression, new HashSet<>());
        if (locals.isEmpty()) {
            return true;
        }
        if (method == null) {
            return false;
        }
        Set<String> reassigned = new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, Set<String> p) {
                record(assignment.getVariable(), p);
                return super.visitAssignment(assignment, p);
            }

            @Override
            public J.AssignmentOperation visitAssignmentOperation(J.AssignmentOperation assignOp, Set<String> p) {
                record(assignOp.getVariable(), p);
                return super.visitAssignmentOperation(assignOp, p);
            }

            @Override
            public J.Unary visitUnary(J.Unary unary, Set<String> p) {
                if (unary.getOperator().isModifying()) {
                    record(unary.getExpression(), p);
                }
                return super.visitUnary(unary, p);
            }

            private void record(Expression target, Set<String> p) {
                if (target instanceof J.Identifier) {
                    p.add(((J.Identifier) target).getSimpleName());
                }
            }
        }.reduce(method, new HashSet<>());
        return disjoint(locals, reassigned);
    }

    private static String methodAndType(Cursor cursor, JavaType.FullyQualified mockedType) {
        J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
        return (method == null ? "" : method.getId() + ":") + mockedType.getFullyQualifiedName();
    }

    /// The condition on the arguments of a construction under which the stubbing applies, with the expressions it
    /// refers to appended to `parameters`; null where the stubbing applies to every construction, and the empty
    /// string for one that cannot be expressed.
    private static @Nullable String constructionMatch(WhenNew stubbing, String contextName, List<Expression> parameters) {
        String name = stubbing.with.getSimpleName();
        if ("withAnyArguments".equals(name)) {
            return null;
        }
        List<Expression> arguments = stubbing.with.getArguments();
        if ("withNoArguments".equals(name) || noArguments(stubbing.with)) {
            return contextName + ".arguments().isEmpty()";
        }
        if (!"withArguments".equals(name)) {
            return UNSUPPORTED;
        }
        if (arguments.stream().allMatch(argument -> exactValue(argument) != null)) {
            arguments.stream().map(PowerMockitoWhenNewToMockito::exactValue).forEach(parameters::add);
            String values = String.join(", ", nCopies(arguments.size(), "#{any()}"));
            return contextName + ".arguments().equals(" + (arguments.size() == 1 ? "Collections.singletonList(" : "Arrays.asList(") + values + "))";
        }
        StringBuilder match = new StringBuilder(contextName + ".arguments().size() == " + arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            String at = contextName + ".arguments().get(" + i + ")";
            Expression argument = arguments.get(i);
            Expression value = exactValue(argument);
            if (value != null) {
                match.append(" && Objects.equals(").append(at).append(", #{any()})");
                parameters.add(value);
                continue;
            }
            J.MethodInvocation matcher = (J.MethodInvocation) argument;
            String matcherName = matcher.getSimpleName();
            if (("any".equals(matcherName) || "isA".equals(matcherName)) && matcher.getArguments().size() == 1 && !noArguments(matcher)) {
                match.append(" && #{any(java.lang.Class)}.isInstance(").append(at).append(")");
                parameters.add(matcher.getArguments().get(0));
            } else if (MATCHED_TYPES.containsKey(matcherName) && noArguments(matcher)) {
                match.append(" && ").append(at).append(" instanceof ").append(MATCHED_TYPES.get(matcherName));
            } else if (!(("any".equals(matcherName) || "anyObject".equals(matcherName)) && noArguments(matcher))) {
                return UNSUPPORTED;
            }
        }
        return match.toString();
    }

    /// The value an argument of `withArguments` must equal, either given as is or wrapped in `eq(..)`; null for other
    /// argument matchers.
    private static @Nullable Expression exactValue(Expression argument) {
        if (!(argument instanceof J.MethodInvocation && (MATCHER.matches(argument) || LEGACY_MATCHER.matches(argument)))) {
            return argument;
        }
        J.MethodInvocation matcher = (J.MethodInvocation) argument;
        return "eq".equals(matcher.getSimpleName()) && matcher.getArguments().size() == 1 && !noArguments(matcher) ?
                matcher.getArguments().get(0) : null;
    }

    private static boolean noArguments(J.MethodInvocation method) {
        return method.getArguments().stream().allMatch(J.Empty.class::isInstance);
    }

    private static class WhenNewVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.ClassDeclaration classDecl;
        private final ScopedMocks mocks;
        private final Plan plan;
        private final String contextName;

        WhenNewVisitor(J.ClassDeclaration classDecl, ScopedMocks mocks, Plan plan) {
            this.classDecl = classDecl;
            this.mocks = mocks;
            this.plan = plan;
            this.contextName = usesIdentifier(classDecl, "context") ? "constructionContext" : "context";
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
            return cd == classDecl ? super.visitClassDeclaration(cd, ctx) : cd;
        }

        @Override
        public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
            J.Block b = super.visitBlock(block, ctx);
            List<Statement> rewritten = new ArrayList<>(b.getStatements().size());
            for (Statement statement : b.getStatements()) {
                List<WhenNew> stubbings = plan.rewrite.get(statement.getId());
                VerifyNew verifyNew = verifyNew(statement);
                if (stubbings != null) {
                    rewritten.addAll(mockConstruction(statement, stubbings, ctx));
                } else if (verifyNew != null && mocks.get(MOCKED_CONSTRUCTION, verifyNew.mockedType) != null) {
                    rewritten.add(assertConstructed(statement, verifyNew, ctx));
                } else if (!plan.remove.contains(statement.getId())) {
                    rewritten.add(statement);
                }
            }
            return b.withStatements(rewritten);
        }

        private List<Statement> mockConstruction(Statement stubbing, List<WhenNew> stubbings, ExecutionContext ctx) {
            WhenNew first = stubbings.get(0);
            ScopedMocks.ScopedMock mock = requireNonNull(mocks.get(MOCKED_CONSTRUCTION, first.mockedType));
            J.MethodInvocation mockConstruction = stubbings.size() == 1 ?
                    mockConstructionWithAnswer(stubbing, first, ctx) :
                    mockConstructionPerArguments(stubbing, stubbings, ctx);
            J.Assignment assignment = mocks.assign(mock, mockConstruction, stubbing.getPrefix()).withId(stubbing.getId());
            Statement close = mocks.closeIfOpen(mock, new Cursor(getCursor(), assignment), assignment, ctx);
            return close == null ?
                    singletonList(assignment) :
                    Arrays.asList(close.withPrefix(stubbing.getPrefix().withComments(emptyList())), assignment);
        }

        private Statement assertConstructed(Statement verification, VerifyNew verifyNew, ExecutionContext ctx) {
            ScopedMocks.ScopedMock mock = requireNonNull(mocks.get(MOCKED_CONSTRUCTION, verifyNew.mockedType));
            TestFramework framework = TestFramework.detect(getCursor().firstEnclosingOrThrow(JavaSourceFile.class));
            String assertions = framework.assertionsClass.substring(framework.assertionsClass.lastIndexOf('.') + 1);
            List<Object> parameters = new ArrayList<>();
            parameters.add(mock.field());
            String actual = "#{any(org.mockito.MockedConstruction)}.constructed().size()";
            String call = assertions + ".assertEquals(" + framework.assertCount(actual, verifyNew.expectedCount) + ");";
            if (verifyNew.times != null) {
                // Parameters appear in the order of the template
                parameters.clear();
                if (framework == TestFramework.TESTNG) {
                    parameters.add(mock.field());
                    parameters.add(verifyNew.times);
                } else {
                    parameters.add(verifyNew.times);
                    parameters.add(mock.field());
                }
            }
            return JavaTemplate.builder(call)
                    .imports(framework.assertionsClass, "org.mockito.MockedConstruction")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12", framework.classpathResource))
                    .build()
                    .apply(new Cursor(getCursor(), verification), verification.getCoordinates().replace(), parameters.toArray());
        }

        private J.MethodInvocation mockConstructionWithAnswer(Statement stubbing, WhenNew whenNew, ExecutionContext ctx) {
            return JavaTemplate.builder(
                            "Mockito.mockConstructionWithAnswer(#{any(java.lang.Class)}, AdditionalAnswers.delegatesTo(#{any()}))")
                    .imports("org.mockito.AdditionalAnswers", "org.mockito.Mockito")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .build()
                    .apply(new Cursor(getCursor(), stubbing), stubbing.getCoordinates().replace(), whenNew.type, whenNew.instance);
        }

        /// Later stubbings win over earlier ones, as they did in PowerMock, so they are tried first. A construction
        /// that no stubbing matches is mocked with Mockito's defaults, as its constructor cannot run.
        private J.MethodInvocation mockConstructionPerArguments(Statement stubbing, List<WhenNew> stubbings, ExecutionContext ctx) {
            List<Object> parameters = new ArrayList<>();
            parameters.add(stubbings.get(0).type);
            StringBuilder template = new StringBuilder("Mockito.mockConstruction(#{any(java.lang.Class)}, " + contextName + " -> {\n");
            String settings = "Mockito.withSettings().defaultAnswer(AdditionalAnswers.delegatesTo(#{any()}))";
            String otherwise = "Mockito.withSettings()";
            for (int i = stubbings.size() - 1; i >= 0; i--) {
                WhenNew whenNew = stubbings.get(i);
                List<Expression> matchParameters = new ArrayList<>();
                String match = constructionMatch(whenNew, contextName, matchParameters);
                parameters.addAll(matchParameters);
                parameters.add(whenNew.instance);
                if (match == null) {
                    otherwise = settings;
                    break;
                }
                template.append("if (").append(match).append(") {\nreturn ").append(settings).append(";\n}\n");
            }
            template.append("return ").append(otherwise).append(";\n})");
            return JavaTemplate.builder(template.toString())
                    .imports("java.util.Arrays", "java.util.Collection", "java.util.Collections", "java.util.List", "java.util.Map", "java.util.Objects", "java.util.Set", "org.mockito.AdditionalAnswers", "org.mockito.Mockito")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .build()
                    .apply(new Cursor(getCursor(), stubbing), stubbing.getCoordinates().replace(), parameters.toArray());
        }
    }
}
