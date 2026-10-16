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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;

class KotlinJUnit5MigrationCleanupTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new KotlinJUnit5MigrationCleanup())
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"));
    }

    @Test
    void removesDuplicateImportsWithoutMigrationMarker() {
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.Test

              class Example {
                  @Test fun test() {}
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class Example {
                  @Test fun test() {}
              }
              """
          )
        );
    }

    @Test
    void validJupiterImportsAndCallsAreUnchanged() {
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.Assertions.assertEquals

              class Example {
                  @Test fun test() { assertEquals(1, 1) }
              }
              """
          )
        );
    }

    @Test
    void unrelatedKotlinIsUnchanged() {
        rewriteRun(kotlin("import java.util.List\nimport java.io.File\n\nclass Example"));
    }
}
