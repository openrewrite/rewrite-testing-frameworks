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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;

public class MigrateJUnit3Assertions extends Recipe {

    @Getter
    final String displayName = "Migrate JUnit 3 assertions to JUnit 4";

    @Getter
    final String description = "Replace the assertion methods of `junit.framework.Assert`, whether called through " +
            "`TestCase`, `Assert` or a static import, with those of `org.junit.Assert`. " +
            "A two-argument `assertEquals` on `double` or `float` values compared their boxed values in JUnit 3, " +
            "whereas JUnit 4 resolves it to a deprecated overload that always fails, so a delta of `0` is added. " +
            "Files that use `junit.framework.TestResult` are left untouched.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        SupportsAnnotations.precondition(),
                        Preconditions.or(
                                new UsesType<>("junit.framework.Assert", false),
                                new UsesType<>("junit.framework.TestCase", false)),
                        Preconditions.not(new UsesType<>("junit.framework.TestResult", true))),
                new JUnit3AssertionsVisitor());
    }
}
