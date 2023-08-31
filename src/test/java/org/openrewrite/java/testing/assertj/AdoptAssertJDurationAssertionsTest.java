/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AdoptAssertJDurationAssertionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new AdoptAssertJDurationAssertions())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3.24"));
    }

    @Test
    @DocumentExample
    void getSecondEqualToTest() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isEqualTo(1);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB)).hasSeconds(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void worksWithGetNano() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getNano()).isEqualTo(1);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB)).hasNanos(1);
                  }
              }
              """
          )
        );
    }

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

    @Test
    void millisToSeconds() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasMillis(5000);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasSeconds(5);
                  }
              }
              """
          )
        );
    }

    @Test
    void secondsToMinutes() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasSeconds(600);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasMinutes(10);
                  }
              }
              """
          )
        );
    }

    @Test
    void millisToMinutes() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasMillis(300000);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasMinutes(5);
                  }
              }
              """
          )
        );
    }

    @Test
    void millisToHours() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasMillis(18000000);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasHours(5);
                  }
              }
              """
          )
        );
    }

    @Test
    void millisToDays() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasMillis(432000000);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasDays(5);
                  }
              }
              """
          )
        );
    }

    @Test
    void minutesToHours() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasMinutes(120);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasHours(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void hoursToDays() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasHours(48);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasDays(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void cannotBeSimplified() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasHours(34);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRunOnNonMultArithmetic() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasHours(34 + 5);
                      assertThat(time).hasHours(34 - 5);
                      assertThat(time).hasHours(34 / 5);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeWhenConstantIsMultiplied() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time, int constant) {
                      assertThat(time).hasHours(34 * constant);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesChangeOnMultiplication() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasHours(24 * 2);
                  }
              }
              """,
            """
              import java.time.Duration;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Duration time) {
                      assertThat(time).hasDays(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRetainAsDescription() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).as("description").isEqualTo(1);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB)).as("description").hasSeconds(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRetainWhiteSpace() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds())
                        .isEqualTo(1);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB))
                        .hasSeconds(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotMatchUnrelatedToDurations() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              
              class Foo {
                  void testMethod() {
                      assertThat(bar()).isEqualTo(0);
                  }
                  int bar() {
                      return 0;  
                  }
              }
              """
          )
        );
    }
}
