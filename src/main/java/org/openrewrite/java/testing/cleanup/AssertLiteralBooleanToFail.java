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
import com.google.errorprone.refaster.annotation.UseImportPolicy;
import org.openrewrite.java.template.RecipeDescriptor;

import static com.google.errorprone.refaster.ImportPolicy.STATIC_IMPORT_ALWAYS;
import static org.junit.jupiter.api.Assertions.*;

@RecipeDescriptor(
        name = "Replace JUnit `assertTrue(false, \"reason\")` and `assertFalse(true, \"reason\")` with `fail(\"reason\")`",
        description = "Using fail is more direct and clear."
)
@SuppressWarnings({"DataFlowIssue", "SimplifiableAssertion"})
public class AssertLiteralBooleanToFail {

    static class WithMessage {
        @BeforeTemplate
        void assertFalseBefore(String message) {
            assertFalse(true, message);
        }

        @BeforeTemplate
        void assertTrueBefore(String message) {
            assertTrue(false, message);
        }

        @AfterTemplate
        @UseImportPolicy(STATIC_IMPORT_ALWAYS)
        void after(String message) {
            fail(message);
        }
    }

    static class WithoutMessage {
        @BeforeTemplate
        void assertFalseBefore() {
            assertFalse(true);
        }

        @BeforeTemplate
        void assertTrueBefore() {
            assertTrue(false);
        }

        @AfterTemplate
        @UseImportPolicy(STATIC_IMPORT_ALWAYS)
        void after() {
            fail();
        }
    }
}
