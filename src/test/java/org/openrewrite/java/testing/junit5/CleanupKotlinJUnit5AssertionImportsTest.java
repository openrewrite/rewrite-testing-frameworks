/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.kotlin.Assertions.kotlin;

class CleanupKotlinJUnit5AssertionImportsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new CleanupKotlinJUnit5AssertionImports())
          .typeValidationOptions(TypeValidation.all().methodInvocations(false));
    }

    @DocumentExample
    @Test
    void removesStaticAssertionsWildcardImportWhenApiWildcardPresent() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.*
              import org.junit.jupiter.api.Assertions.*

              class ATest {
                  @Test
                  fun testSomething() {
                      assertNull(null)
                      assertNotNull("test")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.*

              class ATest {
                  @Test
                  fun testSomething() {
                      assertNull(null)
                      assertNotNull("test")
                  }
              }
              """
          )
        );
    }

    @Test
    void removesSpecificStaticAssertionsImportWhenApiWildcardPresent() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.*
              import org.junit.jupiter.api.Assertions.assertNull
              import org.junit.jupiter.api.Assertions.assertNotNull

              class ATest {
                  @Test
                  fun testSomething() {
                      assertNull(null)
                      assertNotNull("test")
                  }
              }
              """,
            """
              import org.junit.jupiter.api.*

              class ATest {
                  @Test
                  fun testSomething() {
                      assertNull(null)
                      assertNotNull("test")
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenOnlyApiWildcardImportPresent() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.*

              class ATest {
                  @Test
                  fun testSomething() {
                      assertNull(null)
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenOnlyStaticAssertionsImportPresent() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test
              import org.junit.jupiter.api.Assertions.*

              class ATest {
                  @Test
                  fun testSomething() {
                      assertNull(null)
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForJavaFiles() {
        // Java files should not be affected by this recipe
        // This test ensures the recipe only operates on Kotlin files
        rewriteRun(
          spec -> spec.parser(org.openrewrite.java.JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5")),
          org.openrewrite.java.Assertions.java(
            """
              import org.junit.jupiter.api.*;
              import static org.junit.jupiter.api.Assertions.*;

              class ATest {
                  @Test
                  void testSomething() {
                      assertNull(null);
                  }
              }
              """
          )
        );
    }
}
