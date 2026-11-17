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

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.openrewrite.java.testing.junit4.JUnit3MigrationVisitor.fqn;
import static org.openrewrite.java.testing.junit4.JUnit3MigrationVisitor.inClass;

/**
 * Runs the steps of the migration of {@code TestCase} classes over a source file, in order.
 */
class JUnit3TestCaseVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final List<String> JUNIT3_IMPORTS = Arrays.asList("junit.framework.TestCase", "junit.framework.Test",
            "junit.framework.TestSuite", "junit.framework.JUnit4TestAdapter", "junit.extensions.TestSetup", "junit.textui.TestRunner");

    private final JUnit3MigrationPlan plan;

    JUnit3TestCaseVisitor(JUnit3MigrationPlan plan) {
        this.plan = plan;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        Cursor parent = getCursor().getParentOrThrow();
        boolean usesJUnit = new UsesType<>("junit..*", false).visit(cu, ctx) != cu;
        // callers of a constructor that loses its test name need not use anything from JUnit
        if (!usesJUnit && !usesClassDroppingNameArgument(cu)) {
            return cu;
        }
        J.CompilationUnit c = (J.CompilationUnit) new NameConstructorVisitor(plan).visitNonNull(cu, ctx, parent);
        if (!usesJUnit) {
            return c;
        }
        boolean migratesTestCase = declaresMigratedClass(c);
        // a TestResult tells an AssertionFailedError (a failure) from any other error, which JUnit 4 assertions do not throw
        boolean usesTestResult = new UsesType<>("junit.framework.TestResult", false).visit(c, ctx) != c;
        for (TreeVisitor<?, ExecutionContext> step : Arrays.asList(new TestCaseClassVisitor(plan), new SuiteVisitor(plan), new TestRunnerVisitor(plan))) {
            c = (J.CompilationUnit) step.visitNonNull(c, ctx, parent);
        }
        J.CompilationUnit beforeAssertions = c;
        c = (J.CompilationUnit) new JUnit3AssertionsVisitor(cursor -> !usesTestResult && !inClass(cursor, plan::isUnmigrated), migratesTestCase)
                .visitNonNull(c, ctx, parent);
        if (migratesTestCase || c != beforeAssertions) {
            // JUnit 4 assertions throw an AssertionError, which a catch of an AssertionFailedError would not catch
            c = (J.CompilationUnit) new ChangeType("junit.framework.AssertionFailedError", "java.lang.AssertionError", true)
                    .getVisitor().visitNonNull(c, ctx, parent);
            c = (J.CompilationUnit) new ChangeType("junit.framework.ComparisonFailure", "org.junit.ComparisonFailure", true)
                    .getVisitor().visitNonNull(c, ctx, parent);
        }
        if (c != cu || migratesTestCase) {
            // before @Test is added, as an import of junit.framework.Test would keep org.junit.Test from being imported;
            // not otherwise, as a file with incomplete type attribution would lose imports it still needs
            for (String type : JUNIT3_IMPORTS) {
                c = (J.CompilationUnit) new RemoveImport<ExecutionContext>(type).visitNonNull(c, ctx, parent);
            }
            c = (J.CompilationUnit) new LifecycleMethodsVisitor(plan).visitNonNull(c, ctx, parent);
        }
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                String fqn = fqn(cd);
                return plan.isUnmigrated(fqn) ? SearchResult.found(cd, "Not migrated from JUnit 3: " + plan.pinReasonByFqn.get(fqn)) : cd;
            }
        }.visitNonNull(c, ctx, parent);
    }

    private boolean usesClassDroppingNameArgument(J.CompilationUnit cu) {
        if (plan.nameArgumentDroppedFqns.isEmpty()) {
            return false;
        }
        for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
            if (type instanceof JavaType.FullyQualified && plan.dropsNameArgument(((JavaType.FullyQualified) type).getFullyQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private boolean declaresMigratedClass(J.CompilationUnit cu) {
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, AtomicBoolean found) {
                if (plan.isMigrated(fqn(cd))) {
                    found.set(true);
                }
                return super.visitClassDeclaration(cd, found);
            }
        }.reduce(cu, new AtomicBoolean()).get();
    }
}
