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
package org.openrewrite.java.testing.awaitility;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class AwaitilityUseDurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "awaitility-4",
            "hamcrest-3"))
          .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(),
            "awaitility-4",
            "hamcrest-3"))
          .recipe(new AwaitilityUseDuration());
    }

    @DocumentExample
    @Test
    void replaceLongAndTimeUnitWithDuration() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.TimeUnit;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().atMost(5, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).until(() -> true);
                  }
              }
              """,
            """
              import java.time.Duration;

              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).until(() -> true);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticallyImportedTimeUnit() {
        rewriteRun(
          //language=java
          java(
            """
              import static java.util.concurrent.TimeUnit.MINUTES;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().atLeast(1, MINUTES).timeout(2, MINUTES).until(() -> true);
                  }
              }
              """,
            """
              import java.time.Duration;

              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().atLeast(Duration.ofMinutes(1)).timeout(Duration.ofMinutes(2)).until(() -> true);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticAwaitilityMethods() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.TimeUnit;
              import org.awaitility.Awaitility;
              import static org.awaitility.Awaitility.waitAtMost;
              import static org.awaitility.pollinterval.FixedPollInterval.fixed;

              class A {
                  void test() {
                      Awaitility.setDefaultTimeout(10, TimeUnit.SECONDS);
                      waitAtMost(5, TimeUnit.SECONDS).pollInterval(fixed(100, TimeUnit.MILLISECONDS)).during(1, TimeUnit.SECONDS).until(() -> true);
                  }
              }
              """,
            """
              import org.awaitility.Awaitility;

              import java.time.Duration;
              import static org.awaitility.Awaitility.waitAtMost;
              import static org.awaitility.pollinterval.FixedPollInterval.fixed;

              class A {
                  void test() {
                      Awaitility.setDefaultTimeout(Duration.ofSeconds(10));
                      waitAtMost(Duration.ofSeconds(5)).pollInterval(fixed(Duration.ofMillis(100))).during(Duration.ofSeconds(1)).until(() -> true);
                  }
              }
              """
          )
        );
    }

    @Test
    void microseconds() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.TimeUnit;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().pollDelay(5, TimeUnit.MICROSECONDS).until(() -> true);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.ChronoUnit;

              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().pollDelay(Duration.of(5, ChronoUnit.MICROS)).until(() -> true);
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifyUnits() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.TimeUnit;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().atMost(5000, TimeUnit.MILLISECONDS).until(() -> true);
                  }
              }
              """,
            """
              import java.time.Duration;

              import static org.awaitility.Awaitility.await;

              class A {
                  void test() {
                      await().atMost(Duration.ofSeconds(5)).until(() -> true);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepTimeUnitImportWhenStillUsed() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.TimeUnit;
              import static org.awaitility.Awaitility.await;

              class A {
                  long timeout = TimeUnit.SECONDS.toMillis(5);

                  void test() {
                      await().atMost(5, TimeUnit.SECONDS).until(() -> true);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.util.concurrent.TimeUnit;
              import static org.awaitility.Awaitility.await;

              class A {
                  long timeout = TimeUnit.SECONDS.toMillis(5);

                  void test() {
                      await().atMost(Duration.ofSeconds(5)).until(() -> true);
                  }
              }
              """
          )
        );
    }

    @Test
    void kotlinSource() {
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              import java.util.concurrent.TimeUnit
              import org.awaitility.Awaitility.await

              class A {
                  fun test() {
                      await().atMost(5, TimeUnit.SECONDS).until { true }
                  }
              }
              """,
            """
              import org.awaitility.Awaitility.await

              import java.time.Duration

              class A {
                  fun test() {
                      await().atMost(Duration.ofSeconds(5)).until { true }
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void durationOrVariableUnit() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.time.Duration;
                  import java.util.concurrent.TimeUnit;
                  import static org.awaitility.Awaitility.await;

                  class A {
                      void test(TimeUnit unit) {
                          await().atMost(Duration.ofSeconds(5)).until(() -> true);
                          await().atMost(5, unit).until(() -> true);
                      }
                  }
                  """
              )
            );
        }
    }
}
