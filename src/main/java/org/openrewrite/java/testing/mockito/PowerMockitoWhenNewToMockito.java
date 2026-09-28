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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.*;

import static java.util.Collections.emptyList;
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
        return Preconditions.check(new UsesMethod<>(WHEN_NEW), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                TestFramework framework = TestFramework.detect(getCursor().firstEnclosingOrThrow(JavaSourceFile.class));
                ScopedMocks mocks = collectConstructionMocks(cd, framework);
                if (mocks.isEmpty()) {
                    return cd;
                }

                cd = mocks.declareFields(this, cd, ctx);
                cd = (J.ClassDeclaration) new WhenNewVisitor(cd, mocks).visitNonNull(cd, ctx, getCursor().getParentOrThrow());
                cd = mocks.closeAfterEachTest(this, cd, "tearDownStaticMocks", ctx);
                maybeAddImport("org.mockito.AdditionalAnswers");
                maybeAddImport("org.mockito.Mockito");
                maybeRemoveImport("org.powermock.api.mockito.PowerMockito");
                maybeRemoveImport("org.powermock.api.mockito.PowerMockito.whenNew");
                return cd;
            }
        });
    }

    /// The mocked type and returned instance of a `whenNew(Type.class).with...().thenReturn(instance)` statement.
    private static final class WhenNew {
        final Expression type;
        final Expression instance;

        WhenNew(Expression type, Expression instance) {
            this.type = type;
            this.instance = instance;
        }
    }

    private static @Nullable WhenNew whenNew(Statement statement) {
        if (!(statement instanceof J.MethodInvocation) || !THEN_RETURN.matches((J.MethodInvocation) statement) ||
            ((J.MethodInvocation) statement).getArguments().size() != 1) {
            return null;
        }
        J.MethodInvocation thenReturn = (J.MethodInvocation) statement;
        Expression select = thenReturn.getSelect();
        while (select instanceof J.MethodInvocation && WITH_ARGUMENTS.matches(select)) {
            select = ((J.MethodInvocation) select).getSelect();
        }
        if (select == thenReturn.getSelect() || !(select instanceof J.MethodInvocation) || !WHEN_NEW.matches(select)) {
            return null;
        }
        Expression type = ((J.MethodInvocation) select).getArguments().get(0);
        return classLiteral(type) == null ? null : new WhenNew(type, thenReturn.getArguments().get(0));
    }

    private static ScopedMocks collectConstructionMocks(J.ClassDeclaration cd, TestFramework framework) {
        ScopedMocks mocks = new ScopedMocks(cd, framework);
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Integer p) {
                return classDecl == cd ? super.visitClassDeclaration(classDecl, p) : classDecl;
            }

            @Override
            public J.Block visitBlock(J.Block block, Integer p) {
                J.Block b = super.visitBlock(block, p);
                Map<String, Integer> stubbingsPerType = new HashMap<>();
                for (Statement statement : b.getStatements()) {
                    WhenNew whenNew = whenNew(statement);
                    if (whenNew != null) {
                        stubbingsPerType.merge(requireClassLiteral(whenNew).getFullyQualifiedName(), 1, Integer::sum);
                    }
                }
                for (Statement statement : b.getStatements()) {
                    WhenNew whenNew = whenNew(statement);
                    if (whenNew != null && stubbingsPerType.get(requireClassLiteral(whenNew).getFullyQualifiedName()) == 1) {
                        mocks.register(MOCKED_CONSTRUCTION, requireClassLiteral(whenNew), "mockedConstruction",
                                new Cursor(getCursor(), statement));
                    }
                }
                return b;
            }
        }.visit(cd, 0);
        return mocks;
    }

    private static JavaType.FullyQualified requireClassLiteral(WhenNew whenNew) {
        return Objects.requireNonNull(classLiteral(whenNew.type));
    }

    private static class WhenNewVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final J.ClassDeclaration classDecl;
        private final ScopedMocks mocks;

        WhenNewVisitor(J.ClassDeclaration classDecl, ScopedMocks mocks) {
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
            Map<String, Integer> stubbingsPerType = new HashMap<>();
            for (Statement statement : b.getStatements()) {
                WhenNew whenNew = whenNew(statement);
                if (whenNew != null) {
                    stubbingsPerType.merge(requireClassLiteral(whenNew).getFullyQualifiedName(), 1, Integer::sum);
                }
            }
            List<Statement> rewritten = new ArrayList<>(b.getStatements().size());
            for (Statement statement : b.getStatements()) {
                WhenNew whenNew = whenNew(statement);
                ScopedMocks.ScopedMock mock = whenNew == null ? null : mocks.get(MOCKED_CONSTRUCTION, classLiteral(whenNew.type));
                if (mock == null || stubbingsPerType.get(mock.mockedType.getFullyQualifiedName()) != 1) {
                    rewritten.add(statement);
                    continue;
                }
                J.MethodInvocation stubbing = (J.MethodInvocation) statement;
                Cursor site = new Cursor(getCursor(), stubbing);
                Statement close = mocks.closeIfOpen(mock, site, stubbing, ctx);
                if (close != null) {
                    rewritten.add(close.withPrefix(stubbing.getPrefix().withComments(emptyList())));
                }
                J.MethodInvocation mockConstruction = JavaTemplate.builder(
                                "Mockito.mockConstructionWithAnswer(#{any(java.lang.Class)}, AdditionalAnswers.delegatesTo(#{any()}))")
                        .imports("org.mockito.AdditionalAnswers", "org.mockito.Mockito")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "mockito-core-3.12"))
                        .build()
                        .apply(site, stubbing.getCoordinates().replace(), whenNew.type, whenNew.instance);
                rewritten.add(mocks.assign(mock, mockConstruction, stubbing.getPrefix()));
            }
            return b.withStatements(rewritten);
        }
    }
}
