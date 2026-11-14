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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.search.UsesType;

import java.util.*;

public class MigrateJUnit3TestCase extends ScanningRecipe<MigrateJUnit3TestCase.Accumulator> {

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
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>("junit.framework.TestCase", false),
                        new UsesType<>("junit.framework.Test", false),
                        new UsesType<>("junit.framework.TestSuite", false),
                        new UsesType<>("junit.textui.TestRunner", false)),
                new JUnit3Scanner(acc));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        acc.resolve();
        // no UsesType: callers of a constructor that loses its test name need not use anything from JUnit
        return new JUnit3TestCaseVisitor(acc);
    }

    /**
     * What is known about the {@code TestCase} subclasses declared in source, from all source files. Classes are keyed
     * on their fully qualified name, and class literals, calls and declarations on the id of their LST element.
     */
    public static class Accumulator {
        static final String TEST_CASE = "junit.framework.TestCase";

        final Map<String, ClassInfo> testCasesByFqn = new LinkedHashMap<>();
        /**
         * Why a class, as it is declared or used, cannot be migrated.
         */
        final Map<String, String> pinReasonByFqn = new HashMap<>();
        /**
         * Classes whose constructor is called with a test name argument that may have effects dropping it would lose.
         */
        final Set<String> unsafeNameArgumentFqns = new HashSet<>();
        /**
         * Class literals and {@code suite()} calls that a removed {@code suite()} method takes care of.
         */
        final Set<UUID> consumedIds = new HashSet<>();
        /**
         * The {@code suite()} method declarations that have a JUnit 4 equivalent.
         */
        final Set<UUID> suiteMethodIds = new HashSet<>();
        final Map<String, SuiteShape.Kind> suiteKindByFqn = new HashMap<>();
        /**
         * What the {@code suite()} of a class consumes, which only counts while that suite is removed.
         */
        final Map<String, Set<UUID>> consumedIdsBySuiteFqn = new HashMap<>();
        /**
         * Class literals passed to JUnit 3 APIs, with the class they refer to.
         */
        final Map<UUID, String> classLiteralFqnById = new HashMap<>();
        /**
         * Calls of {@code suite()}, with the class that declares it.
         */
        final Map<UUID, String> suiteCallFqnById = new HashMap<>();
        /**
         * Classes of arguments passed where a {@code junit.framework} type is asked for.
         */
        final Set<String> passedAsJUnit3TypeFqns = new HashSet<>();
        private boolean resolved;

        void pin(@Nullable String fqn, String reason) {
            if (fqn != null) {
                pinReasonByFqn.putIfAbsent(fqn, reason);
            }
        }

        boolean isMigrated(@Nullable String fqn) {
            return fqn != null && testCasesByFqn.containsKey(fqn) && !pinReasonByFqn.containsKey(fqn);
        }

        boolean isUnmigrated(@Nullable String fqn) {
            return fqn != null && testCasesByFqn.containsKey(fqn) && pinReasonByFqn.containsKey(fqn);
        }

        /**
         * Whether the {@code suite()} method of the class is removed, because JUnit 4 does what it does.
         */
        boolean removesSuite(@Nullable String fqn) {
            return fqn != null && suiteKindByFqn.containsKey(fqn) && (!testCasesByFqn.containsKey(fqn) || isMigrated(fqn));
        }

        /**
         * Whether the {@code (String name)} constructor of the class is removed along with the argument of its callers.
         */
        boolean dropsNameArgument(@Nullable String fqn) {
            return isMigrated(fqn) && testCasesByFqn.get(fqn).nameConstructor;
        }

        void resolve() {
            if (resolved) {
                return;
            }
            resolved = true;
            for (String fqn : passedAsJUnit3TypeFqns) {
                pin(fqn, "instances are passed where a junit.framework type is expected");
            }
            boolean pinnedMore = true;
            while (pinnedMore) {
                pinnedMore = false;
                // a suite() that stays no longer consumes the classes and suites it lists
                consumedIdsBySuiteFqn.forEach((fqn, ids) -> {
                    if (testCasesByFqn.containsKey(fqn) && pinReasonByFqn.containsKey(fqn)) {
                        consumedIds.removeAll(ids);
                    }
                });
                int before = pinReasonByFqn.size();
                classLiteralFqnById.forEach((id, fqn) -> {
                    if (!consumedIds.contains(id)) {
                        pin(fqn, "its class is passed to a JUnit 3 API without a JUnit 4 equivalent");
                    }
                });
                suiteCallFqnById.forEach((id, fqn) -> {
                    if (!consumedIds.contains(id)) {
                        pin(fqn, "its suite() method is called");
                    }
                });
                pinnedMore = pinReasonByFqn.size() != before;
            }
            for (String fqn : unsafeNameArgumentFqns) {
                ClassInfo info = testCasesByFqn.get(fqn);
                if (info != null && info.nameConstructor) {
                    pin(fqn, "a constructor call passes an argument with effects that dropping the test name would lose");
                }
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (ClassInfo info : testCasesByFqn.values()) {
                    ClassInfo superInfo = testCasesByFqn.get(info.superclass);
                    if (!pinReasonByFqn.containsKey(info.fqn)) {
                        if (superInfo == null && !TEST_CASE.equals(info.superclass)) {
                            pin(info.fqn, "extends " + info.superclass + ", which is not a test class in source");
                            changed = true;
                        } else if (superInfo != null && pinReasonByFqn.containsKey(superInfo.fqn)) {
                            pin(info.fqn, "extends " + superInfo.fqn + ", which is not migrated: " + pinReasonByFqn.get(superInfo.fqn));
                            changed = true;
                        } else if (superInfo != null && info.callsSuperWithName && !superInfo.nameConstructor) {
                            pin(info.fqn, "passes the test name to a constructor of " + superInfo.fqn + " that stays");
                            changed = true;
                        }
                    }
                    // a subclass that stays a JUnit 3 test needs its superclass to stay one
                    if (pinReasonByFqn.containsKey(info.fqn) && superInfo != null && !pinReasonByFqn.containsKey(superInfo.fqn)) {
                        pin(superInfo.fqn, "its subclass " + info.fqn + " is not migrated: " + pinReasonByFqn.get(info.fqn));
                        changed = true;
                    }
                }
            }
        }
    }

    static class ClassInfo {
        final String fqn;
        final String superclass;
        /**
         * The class declares a {@code (String name)} constructor that becomes a no-argument one.
         */
        boolean nameConstructor;
        /**
         * Some constructor passes its test name on to the superclass.
         */
        boolean callsSuperWithName;

        ClassInfo(String fqn, String superclass) {
            this.fqn = fqn;
            this.superclass = superclass;
        }
    }
}
