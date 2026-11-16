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
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.openrewrite.Tree.randomId;

/**
 * Running migrated test classes with a JUnit 3 {@code TestRunner} becomes running them with {@code JUnitCore}.
 */
class TestRunnerVisitor extends JUnit3MigrationVisitor {

    private static final Set<String> TEST_RUNNERS = new HashSet<>(Arrays.asList(
            "junit.textui.TestRunner", "junit.swingui.TestRunner", "junit.awtui.TestRunner"));

    TestRunnerVisitor(JUnit3MigrationPlan plan) {
        super(plan);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        JavaType.Method type = method.getMethodType();
        if (type != null && TEST_RUNNERS.contains(type.getDeclaringType().getFullyQualifiedName())) {
            J.MethodInvocation replaced = replaceTestRunner(method);
            if (replaced != null) {
                return replaced;
            }
        }
        return super.visitMethodInvocation(method, ctx);
    }

    private J.@Nullable MethodInvocation replaceTestRunner(J.MethodInvocation run) {
        if (JUnit3Scanner.isMainOfTestClasses(run)) {
            maybeAddImport("org.junit.runner.JUnitCore");
            StringBuilder args = new StringBuilder();
            for (Expression e : ((J.NewArray) run.getArguments().get(0).unwrap()).getInitializer()) {
                args.append(args.length() == 0 ? "" : ", ").append(e.printTrimmed(getCursor()));
            }
            return template("JUnitCore.main(" + args + ")").imports("org.junit.runner.JUnitCore").build()
                    .apply(getCursor(), run.getCoordinates().replace());
        }
        if (!"run".equals(run.getSimpleName()) || run.getArguments().size() != 1) {
            return null;
        }
        Expression arg = run.getArguments().get(0).unwrap();
        Expression classLiteral = null;
        if (arg instanceof J.FieldAccess && plan.consumedIds.contains(arg.getId())) {
            classLiteral = arg;
        } else if (arg instanceof J.NewClass && ((J.NewClass) arg).getArguments().size() == 1 &&
                   ((J.NewClass) arg).getArguments().get(0).unwrap() instanceof J.FieldAccess) {
            classLiteral = ((J.NewClass) arg).getArguments().get(0).unwrap();
        } else if (arg instanceof J.MethodInvocation && "suite".equals(((J.MethodInvocation) arg).getSimpleName()) &&
                   ((J.MethodInvocation) arg).getMethodType() != null &&
                   plan.removesSuite(((J.MethodInvocation) arg).getMethodType().getDeclaringType().getFullyQualifiedName())) {
            J.MethodInvocation suite = (J.MethodInvocation) arg;
            Expression select = suite.getSelect();
            if (select == null) {
                JavaType.FullyQualified declaring = suite.getMethodType().getDeclaringType();
                select = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, Collections.emptyList(),
                        SuiteShape.simpleName(declaring.getFullyQualifiedName()), declaring, null);
            }
            classLiteral = SuiteShape.classLiteralOf(select);
        }
        if (classLiteral == null) {
            return null;
        }
        // a test class that stays a JUnit 3 test is still accepted by the JUnit 3 runner
        if (!(arg instanceof J.MethodInvocation) && !plan.isMigrated(fqnOfClassLiteral((J.FieldAccess) classLiteral))) {
            return null;
        }
        maybeAddImport("org.junit.runner.JUnitCore");
        return template("JUnitCore.runClasses(#{any()})").imports("org.junit.runner.JUnitCore").build()
                .apply(getCursor(), run.getCoordinates().replace(), classLiteral.withPrefix(Space.EMPTY));
    }

    private static @Nullable String fqnOfClassLiteral(J.FieldAccess literal) {
        JavaType.FullyQualified t = TypeUtils.asFullyQualified(literal.getTarget().getType());
        return t == null ? null : t.getFullyQualifiedName();
    }
}
