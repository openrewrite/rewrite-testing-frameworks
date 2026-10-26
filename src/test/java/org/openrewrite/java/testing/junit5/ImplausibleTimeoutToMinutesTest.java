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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ImplausibleTimeoutToMinutesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new ImplausibleTimeoutToMinutes(null));
    }

    @DocumentExample
    @Test
    void implicitValueAndUnit() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              class MyTest {
                  @Timeout(7200)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 120, unit = TimeUnit.MINUTES)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void explicitValueImplicitUnit() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              class MyTest {
                  @Timeout(value = 1200)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 20, unit = TimeUnit.MINUTES)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void explicitSecondsUnit() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 6000, unit = TimeUnit.SECONDS)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 100, unit = TimeUnit.MINUTES)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void longLiteralValue() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              class MyTest {
                  @Timeout(6000L)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 100, unit = TimeUnit.MINUTES)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void plausibleTimeoutUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              class MyTest {
                  @Timeout(30)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void explicitNonSecondsUnitUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void customThreshold() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new ImplausibleTimeoutToMinutes(300)),
          java(
            """
              import org.junit.jupiter.api.Timeout;

              class MyTest {
                  @Timeout(600)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 10, unit = TimeUnit.MINUTES)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsThreadMode() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              class MyTest {
                  @Timeout(value = 3600, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 60, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsThreadModeAndArgumentOrder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(threadMode = Timeout.ThreadMode.SEPARATE_THREAD, unit = TimeUnit.SECONDS, value = 3600)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(threadMode = Timeout.ThreadMode.SEPARATE_THREAD, unit = TimeUnit.MINUTES, value = 60)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void staticallyImportedSeconds() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              import static java.util.concurrent.TimeUnit.SECONDS;

              class MyTest {
                  @Timeout(value = 3600, unit = SECONDS)
                  void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              class MyTest {
                  @Timeout(value = 60, unit = TimeUnit.MINUTES)
                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void notWholeMinutesUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Timeout;

              class MyTest {
                  @Timeout(10000)
                  void a() {
                  }

                  @Timeout(value = 1000, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
                  void b() {
                  }
              }
              """
          )
        );
    }
}
