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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.testing.mockito.PowerMockitoMockStaticToMockito.classLiteral;
import static org.openrewrite.java.testing.mockito.ScopedMocks.MOCKED_CONSTRUCTION;

public class PowerMockitoWhenNewToMockito extends Recipe {

    private static final MethodMatcher WHEN_NEW = new MethodMatcher("org.powermock.api.mockito.PowerMockito whenNew(java.lang.Class)");
    private static final MethodMatcher WITH_ARGUMENTS = new MethodMatcher("org.powermock.api.mockito.expectation..* with*(..)");
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
                        Set<UUID> migratable = new HashSet<>();
                        ScopedMocks mocks = collectConstructionMocks(cd, framework, migratable);
                        if (migratable.isEmpty()) {
                            return cd;
                        }

                        cd = mocks.declareFields(this, cd, ctx);
                        cd = (J.ClassDeclaration) new WhenNewVisitor(cd, mocks, migratable).visitNonNull(cd, ctx, getCursor().getParentOrThrow());
                        cd = mocks.closeAfterEachTest(this, cd, "tearDownStaticMocks", ctx);
                        maybeAddImport("org.mockito.AdditionalAnswers");
                        maybeAddImport("org.mockito.Mockito");
                        maybeRemoveImport("org.powermock.api.mockito.PowerMockito");
                        maybeRemoveImport("org.powermock.api.mockito.PowerMockito.whenNew");
                        return cd;
                    }
                });
    }

    private static final class WhenNew {
        final JavaType.FullyQualified mockedType;
        final Expression type;
        final Expression instance;

        WhenNew(JavaType.FullyQualified mockedType, Expression type, Expression instance) {
            this.mockedType = mockedType;
            this.type = type;
            this.instance = instance;
        }
    }

    private static @Nullable WhenNew whenNew(J tree) {
        if (!(tree instanceof J.MethodInvocation) || !THEN_RETURN.matches((J.MethodInvocation) tree) ||
            ((J.MethodInvocation) tree).getArguments().size() != 1) {
            return null;
        }
        J.MethodInvocation thenReturn = (J.MethodInvocation) tree;
        Expression select = thenReturn.getSelect();
        while (select instanceof J.MethodInvocation && WITH_ARGUMENTS.matches(select)) {
            select = ((J.MethodInvocation) select).getSelect();
        }
        if (select == thenReturn.getSelect() || !(select instanceof J.MethodInvocation) || !WHEN_NEW.matches(select)) {
            return null;
        }
        Expression type = ((J.MethodInvocation) select).getArguments().get(0);
        JavaType.FullyQualified mockedType = classLiteral(type);
        return mockedType == null ? null : new WhenNew(mockedType, type, thenReturn.getArguments().get(0));
    }

    private static ScopedMocks collectConstructionMocks(J.ClassDeclaration cd, TestFramework framework, Set<UUID> migratable) {
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

        ScopedMocks mocks = new ScopedMocks(cd, framework);
        for (Cursor candidate : candidates) {
            JavaType.FullyQualified mockedType = requireNonNull(whenNew(candidate.getValue())).mockedType;
            // A single MockedConstruction cannot tell apart stubbings that differ in their constructor arguments
            if (stubbingsPerMethodAndType.get(methodAndType(candidate, mockedType)) == 1) {
                mocks.register(MOCKED_CONSTRUCTION, mockedType, "mockedConstruction", candidate);
                migratable.add(candidate.<J>getValue().getId());
            }
        }
        return mocks;
    }

    private static String methodAndType(Cursor cursor, JavaType.FullyQualified mockedType) {
        J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
        return (method == null ? "" : method.getId() + ":") + mockedType.getFullyQualifiedName();
    }

    private static class WhenNewVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.ClassDeclaration classDecl;
        private final ScopedMocks mocks;
        private final Set<UUID> migratable;

        WhenNewVisitor(J.ClassDeclaration classDecl, ScopedMocks mocks, Set<UUID> migratable) {
            this.classDecl = classDecl;
            this.mocks = mocks;
            this.migratable = migratable;
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
                if (migratable.contains(statement.getId())) {
                    rewritten.addAll(mockConstruction((J.MethodInvocation) statement, ctx));
                } else {
                    rewritten.add(statement);
                }
            }
            return b.withStatements(rewritten);
        }

        private List<Statement> mockConstruction(J.MethodInvocation stubbing, ExecutionContext ctx) {
            WhenNew whenNew = requireNonNull(whenNew(stubbing));
            ScopedMocks.ScopedMock mock = requireNonNull(mocks.get(MOCKED_CONSTRUCTION, whenNew.mockedType));
            J.MethodInvocation mockConstruction = JavaTemplate.builder(
                            "Mockito.mockConstructionWithAnswer(#{any(java.lang.Class)}, AdditionalAnswers.delegatesTo(#{any()}))")
                    .imports("org.mockito.AdditionalAnswers", "org.mockito.Mockito")
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                    .build()
                    .apply(new Cursor(getCursor(), stubbing), stubbing.getCoordinates().replace(), whenNew.type, whenNew.instance);
            J.Assignment assignment = mocks.assign(mock, mockConstruction, stubbing.getPrefix()).withId(stubbing.getId());
            Statement close = mocks.closeIfOpen(mock, new Cursor(getCursor(), assignment), assignment, ctx);
            return close == null ?
                    singletonList(assignment) :
                    Arrays.asList(close.withPrefix(stubbing.getPrefix().withComments(emptyList())), assignment);
        }
    }
}
