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
package org.openrewrite.java.testing.mockito.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class PowerMockTestsDisabled extends DataTable<PowerMockTestsDisabled.Row> {

    public PowerMockTestsDisabled(Recipe recipe) {
        super(recipe, "PowerMock tests disabled for manual migration",
                "Tests disabled because they use a PowerMock feature with no Mockito equivalent. Each row is an " +
                        "action item: rework the test so it does not reach into private members, then re-enable it.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path of the test source file.")
        String sourcePath;

        @Column(displayName = "Test class",
                description = "The test class the disabled test belongs to.")
        String testClass;

        @Column(displayName = "Disabled element",
                description = "The test method that was disabled, or the class name when the usage sits outside a " +
                        "test method and the whole class had to be disabled.")
        String disabledElement;

        @Column(displayName = "Scope",
                description = "`METHOD` when a single test was disabled, `CLASS` when the whole test class was.")
        String scope;

        @Column(displayName = "Reason",
                description = "The PowerMock usage that cannot be migrated.")
        String reason;
    }
}
