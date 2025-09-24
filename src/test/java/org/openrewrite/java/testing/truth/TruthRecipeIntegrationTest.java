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
package org.openrewrite.java.testing.truth;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify that Truth migration recipes are properly registered and loadable.
 */
class TruthRecipeIntegrationTest implements RewriteTest {

    @Test
    void truthMigrationRecipeExists() {
        Environment env = Environment.builder()
                .scanRuntimeClasspath()
                .build();

        assertThat(env.listRecipes()).anyMatch(recipe ->
                recipe.getName().equals("org.openrewrite.java.testing.truth.MigrateTruthToAssertJ"));
    }

    @Test
    void truthRecipesAreRegistered() {
        Environment env = Environment.builder()
                .scanRuntimeClasspath()
                .build();

        // Verify our Truth-specific recipes are available
        assertThat(env.listRecipes())
                .anyMatch(r -> r.getName().equals("org.openrewrite.java.testing.truth.TruthAssertToAssertThat"))
                .anyMatch(r -> r.getName().equals("org.openrewrite.java.testing.truth.TruthAssertWithMessageToAssertJ"))
                .anyMatch(r -> r.getName().equals("org.openrewrite.java.testing.truth.TruthThrowableAssertions"))
                .anyMatch(r -> r.getName().equals("org.openrewrite.java.testing.truth.TruthCustomSubjectsToAssertJ"));
    }
}