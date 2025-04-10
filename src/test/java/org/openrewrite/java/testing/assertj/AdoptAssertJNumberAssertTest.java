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
import org.openrewrite.DocumentExample;
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AdoptAssertJNumberAssertTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new AdoptAssertJNumberAssert())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"));
    }

    @DocumentExample
    @Test
    void isEqualToZeroToIsZero() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getNano()).isEqualTo(0);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getNano()).isZero();
                  }
              }
              """
          )
        );
    }

    @Test
    void isGreaterThanZeroToIsPositive() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isGreaterThan(0);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isPositive();
                  }
              }
              """
          )
        );
    }

    @Test
    void isLessThanZeroToIsNegative() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isLessThan(0);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isNegative();
                  }
              }
              """
          )
        );
    }
}
