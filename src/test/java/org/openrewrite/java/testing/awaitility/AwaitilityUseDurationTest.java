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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AwaitilityUseDurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "awaitility-4", "hamcrest"))
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
    void leaveUnsupportedUnitsAndDurationAlone() {
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
                      await().pollDelay(5, TimeUnit.MICROSECONDS).until(() -> true);
                  }
              }
              """
          )
        );
    }
}
