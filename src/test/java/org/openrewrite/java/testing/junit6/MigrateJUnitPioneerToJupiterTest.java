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
package org.openrewrite.java.testing.junit6;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnitPioneerToJupiterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-6", "junit-pioneer-2"))
          .recipeFromResources("org.openrewrite.java.testing.junit6.MigrateJUnitPioneerToJupiter");
    }

    @DocumentExample
    @Test
    void migrateDefaultLocaleAndDefaultTimeZone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.DefaultLocale;
              import org.junitpioneer.jupiter.DefaultTimeZone;

              class MyTest {
                  @Test
                  @DefaultLocale("en-US")
                  @DefaultTimeZone("UTC")
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.util.DefaultLocale;
              import org.junit.jupiter.api.util.DefaultTimeZone;

              class MyTest {
                  @Test
                  @DefaultLocale("en-US")
                  @DefaultTimeZone("UTC")
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void migrateSystemPropertyAnnotations() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.ClearSystemProperty;
              import org.junitpioneer.jupiter.RestoreSystemProperties;
              import org.junitpioneer.jupiter.SetSystemProperty;

              class MyTest {
                  @Test
                  @SetSystemProperty(key = "foo", value = "bar")
                  @ClearSystemProperty(key = "baz")
                  @RestoreSystemProperties
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.util.ClearSystemProperty;
              import org.junit.jupiter.api.util.RestoreSystemProperties;
              import org.junit.jupiter.api.util.SetSystemProperty;

              class MyTest {
                  @Test
                  @SetSystemProperty(key = "foo", value = "bar")
                  @ClearSystemProperty(key = "baz")
                  @RestoreSystemProperties
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveOtherPioneerAnnotationsAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junitpioneer.jupiter.RetryingTest;

              class MyTest {
                  @RetryingTest(3)
                  void flaky() {
                  }
              }
              """
          )
        );
    }
}
