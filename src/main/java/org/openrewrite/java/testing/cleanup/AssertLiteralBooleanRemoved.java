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
package org.openrewrite.java.testing.cleanup;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RecipeDescriptor(
        name = "Remove JUnit `assertTrue(true)` and `assertFalse(false)`",
        description = "These assertions are redundant and do not provide any value. They can be safely removed."
)
public class AssertLiteralBooleanRemoved {

    @BeforeTemplate
    void assertFalseBefore(String message) {
        assertFalse(false, message);
    }

    @BeforeTemplate
    void assertTrueBefore(String message) {
        assertTrue(true, message);
    }

    @BeforeTemplate
    void assertFalseBefore() {
        assertFalse(false);
    }

    @BeforeTemplate
    void assertTrueBefore() {
        assertTrue(true);
    }

    @AfterTemplate
    void after() {
    }
}
