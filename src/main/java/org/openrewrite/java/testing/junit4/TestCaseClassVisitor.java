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
package org.openrewrite.java.testing.junit4;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A migrated class no longer extends {@code TestCase}: calls of the {@code setUp()} and {@code tearDown()} it
 * inherited go, and {@code getName()} reads a {@code TestName} rule instead.
 */
class TestCaseClassVisitor extends JUnit3MigrationVisitor {

    private static final MethodMatcher SET_UP = new MethodMatcher(JUnit3MigrationPlan.TEST_CASE + " setUp()");
    private static final MethodMatcher TEAR_DOWN = new MethodMatcher(JUnit3MigrationPlan.TEST_CASE + " tearDown()");
    private static final MethodMatcher GET_NAME = new MethodMatcher(JUnit3MigrationPlan.TEST_CASE + " getName()");
    private static final String TEST_NAME_FIELD = "testName";

    TestCaseClassVisitor(JUnit3MigrationPlan plan) {
        super(plan);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        if (!plan.isMigrated(fqn(classDecl))) {
            return super.visitClassDeclaration(classDecl, ctx);
        }
        J.ClassDeclaration cd = classDecl;
        if (usesGetName(cd)) {
            cd = addTestNameRule(cd);
            setCursor(new Cursor(getCursor().getParentOrThrow(), cd));
        }
        cd = super.visitClassDeclaration(cd, ctx);
        if (cd.getExtends() != null && cd.getExtends().getType() != null &&
            JUnit3MigrationPlan.TEST_CASE.equals(TypeUtils.asFullyQualified(cd.getExtends().getType()).getFullyQualifiedName())) {
            cd = cd.withExtends(null);
        }
        return withoutTestCaseSupertype(cd);
    }

    @Override
    public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        if ((SET_UP.matches(method) || TEAR_DOWN.matches(method)) && inMigratedClass() &&
            getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
            return null;
        }
        if (isTestCaseGetName(method) && inMigratedClass()) {
            return template(TEST_NAME_FIELD + ".getMethodName()").imports("org.junit.rules.TestName").contextSensitive().build()
                    .apply(getCursor(), method.getCoordinates().replace());
        }
        return super.visitMethodInvocation(method, ctx);
    }

    /**
     * Recipes that run later in the same cycle see the type, not the {@code extends} clause, so an assertion would
     * still look inherited from {@code TestCase} and shadow the static import it moves to.
     */
    private static J.ClassDeclaration withoutTestCaseSupertype(J.ClassDeclaration cd) {
        if (!(cd.getType() instanceof JavaType.Class)) {
            return cd;
        }
        JavaType.Class type = withoutTestCase((JavaType.Class) cd.getType());
        return cd.withType(type).withName(cd.getName().withType(type));
    }

    private static JavaType.Class withoutTestCase(JavaType.Class type) {
        JavaType.FullyQualified supertype = type.getSupertype();
        if (supertype == null) {
            return type;
        }
        if (JUnit3MigrationPlan.TEST_CASE.equals(supertype.getFullyQualifiedName())) {
            JavaType.FullyQualified object = supertype;
            while (object.getSupertype() != null) {
                object = object.getSupertype();
            }
            return type.withSupertype(object);
        }
        return supertype instanceof JavaType.Class ? type.withSupertype(withoutTestCase((JavaType.Class) supertype)) : type;
    }

    private static boolean usesGetName(J.ClassDeclaration cd) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, AtomicBoolean found) {
                if (isTestCaseGetName(m)) {
                    found.set(true);
                }
                return super.visitMethodInvocation(m, found);
            }
        }.reduce(cd.getBody(), new AtomicBoolean()).get();
    }

    private static boolean isTestCaseGetName(J.MethodInvocation m) {
        return GET_NAME.matches(m) && (m.getSelect() == null || m.getSelect() instanceof J.Identifier && "this".equals(((J.Identifier) m.getSelect()).getSimpleName()));
    }

    private J.ClassDeclaration addTestNameRule(J.ClassDeclaration cd) {
        maybeAddImport("org.junit.Rule");
        maybeAddImport("org.junit.rules.TestName");
        J.Block body = template("@Rule\npublic TestName " + TEST_NAME_FIELD + " = new TestName();")
                .imports("org.junit.Rule", "org.junit.rules.TestName").build()
                .apply(new Cursor(getCursor(), cd.getBody()), cd.getBody().getCoordinates().firstStatement());
        return cd.withBody(body.withStatements(ListUtils.map(body.getStatements(), (i, st) ->
                i == 1 && st.getPrefix().getComments().isEmpty() && st.getPrefix().getWhitespace().chars().filter(c -> c == '\n').count() < 2 ?
                        st.withPrefix(Space.format("\n\n" + st.getPrefix().getIndent())) : st)));
    }
}
