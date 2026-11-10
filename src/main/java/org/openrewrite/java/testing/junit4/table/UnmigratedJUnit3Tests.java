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
package org.openrewrite.java.testing.junit4.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class UnmigratedJUnit3Tests extends DataTable<UnmigratedJUnit3Tests.Row> {

    public UnmigratedJUnit3Tests(Recipe recipe) {
        super(recipe, "Unmigrated JUnit 3 tests",
                "JUnit 3 test classes that were left extending `junit.framework.TestCase`, because moving them to " +
                        "JUnit 4 annotations would change what they do. JUnit 4 still runs them through its JUnit 3 adapter.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path of the test source file.")
        String sourcePath;

        @Column(displayName = "Test class",
                description = "The fully qualified name of the test class that was left as it is.")
        String testClass;

        @Column(displayName = "Reason",
                description = "What makes the class unsafe to migrate automatically.")
        String reason;
    }
}
