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

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.java.search.UsesType;

public class MigrateJUnit3TestCase extends ScanningRecipe<JUnit3MigrationPlan> {

    @Getter
    final String displayName = "Migrate JUnit 3 `TestCase` classes to JUnit 4";

    @Getter
    final String description = "Convert test classes that extend `junit.framework.TestCase` to JUnit 4 annotations: " +
            "`test*` methods get `@Test`, `setUp` and `tearDown` get `@Before` and `@After`, `getName()` reads a `TestName` rule, " +
            "and the `suite()` method becomes `@BeforeClass`/`@AfterClass` or `@RunWith(Suite.class)` where it has an equivalent. " +
            "The `TestCase(String)` constructor is removed together with the test name its callers pass, across source files. " +
            "Assertions move to `org.junit.Assert`. " +
            "A class that would behave differently under JUnit 4, such as one that overrides `run(TestResult)`, " +
            "reports to a `TestResult`, passes its instances around as `Test`s or needs the test name, " +
            "is left as it is, and marked with the reason. JUnit 4 keeps running those through its JUnit 3 adapter.";

    @Override
    public JUnit3MigrationPlan getInitialValue(ExecutionContext ctx) {
        return new JUnit3MigrationPlan();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(JUnit3MigrationPlan plan) {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("junit.framework.TestCase", false),
                        new UsesType<>("junit.framework.Test", false),
                        new UsesType<>("junit.framework.TestSuite", false),
                        new UsesType<>("junit.textui.TestRunner", false)),
                new JUnit3Scanner(plan));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(JUnit3MigrationPlan plan) {
        plan.resolve();
        // no UsesType: callers of a constructor that loses its test name need not use anything from JUnit
        return new JUnit3TestCaseVisitor(plan);
    }
}
