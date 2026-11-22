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
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openrewrite.java.testing.junit4.JUnit3MigrationVisitor.fqn;
import static org.openrewrite.java.testing.junit4.JUnit3MigrationVisitor.inClass;

/**
 * Runs the steps of the migration of {@code TestCase} classes over a source file, in order.
 */
class JUnit3TestCaseVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final List<String> JUNIT3_IMPORTS = Arrays.asList("junit.framework.TestCase", "junit.framework.Test",
            "junit.framework.TestSuite", "junit.framework.JUnit4TestAdapter", "junit.extensions.TestSetup", "junit.textui.TestRunner");

    private final JUnit3MigrationPlan plan;
    private final UsesType<ExecutionContext> usesJUnit = new UsesType<>("junit..*", false);
    private final UsesType<ExecutionContext> usesTestRunner = new UsesType<>("junit.*.TestRunner", false);
    // also through a project's own test base class, which UsesType follows the supertypes to
    private final UsesType<ExecutionContext> usesAssert = new UsesType<>("junit.framework.Assert", false);
    private final UsesType<ExecutionContext> usesTestCase = new UsesType<>(JUnit3MigrationPlan.TEST_CASE, false);
    // a TestResult tells an AssertionFailedError (a failure) from any other error, which JUnit 4 assertions do not throw
    private final UsesType<ExecutionContext> usesTestResult = new UsesType<>("junit.framework.TestResult", false);

    JUnit3TestCaseVisitor(JUnit3MigrationPlan plan) {
        this.plan = plan;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        Cursor parent = getCursor().getParentOrThrow();
        // also a caller of a test class's constructor, as UsesType follows the class's supertypes to TestCase
        if (!matches(usesJUnit, cu, ctx)) {
            return cu;
        }
        J.CompilationUnit c = (J.CompilationUnit) new NameConstructorVisitor(plan).visitNonNull(cu, ctx, parent);
        Set<String> declared = declaredClasses(c);
        boolean migratesTestCase = declared.stream().anyMatch(plan::isMigrated);
        if (migratesTestCase) {
            c = (J.CompilationUnit) new TestCaseClassVisitor(plan).visitNonNull(c, ctx, parent);
        }
        if (declared.stream().anyMatch(plan.suiteKindByFqn::containsKey)) {
            c = (J.CompilationUnit) new SuiteVisitor(plan).visitNonNull(c, ctx, parent);
        }
        if (matches(usesTestRunner, cu, ctx)) {
            c = (J.CompilationUnit) new TestRunnerVisitor(plan).visitNonNull(c, ctx, parent);
        }
        J.CompilationUnit beforeAssertions = c;
        if (matches(usesAssert, cu, ctx) || matches(usesTestCase, cu, ctx)) {
            boolean testResult = matches(usesTestResult, cu, ctx);
            c = (J.CompilationUnit) new JUnit3AssertionsVisitor(cursor -> !testResult && !inClass(cursor, plan::isUnmigrated), migratesTestCase)
                    .visitNonNull(c, ctx, parent);
        }
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
            if (migratesTestCase) {
                c = (J.CompilationUnit) new LifecycleMethodsVisitor(plan).visitNonNull(c, ctx, parent);
            }
        }
        if (declared.stream().noneMatch(plan::isUnmigrated)) {
            return c;
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

    private static boolean matches(UsesType<ExecutionContext> usesType, J.CompilationUnit cu, ExecutionContext ctx) {
        return usesType.visit(cu, ctx) != cu;
    }

    private static Set<String> declaredClasses(J.CompilationUnit cu) {
        return new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, Set<String> declared) {
                String fqn = fqn(cd);
                if (fqn != null) {
                    declared.add(fqn);
                }
                return super.visitClassDeclaration(cd, declared);
            }
        }.reduce(cu, new HashSet<>());
    }
}
