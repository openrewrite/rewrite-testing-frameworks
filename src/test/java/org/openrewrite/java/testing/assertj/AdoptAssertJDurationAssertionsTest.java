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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    void getSecondsToHasSeconds() {
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
    void getNanoToHasNanos() {
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
    void isEqualToVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
                            
              import static org.assertj.core.api.Assertions.assertThat;
                            
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      int zero = 0;
                      assertThat(Duration.between(timestampA, timestampB).getNano()).isEqualTo(zero);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;
                            
              import static org.assertj.core.api.Assertions.assertThat;
                            
              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      int zero = 0;
                      assertThat(Duration.between(timestampA, timestampB)).hasNanos(zero);
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

    @ParameterizedTest
    @CsvSource({
      "hasNanos(6000000L),hasSeconds(6)",
      "hasMillis(5000),hasSeconds(5)",
      "hasSeconds(600),hasMinutes(10)",
      "hasMillis(300000),hasMinutes(5)",
      "hasMillis(18000000),hasHours(5)",
      "hasMillis(432000000),hasDays(5)",
      "hasMinutes(120),hasHours(2)",
      "hasHours(48),hasDays(2)",
      "hasHours(24 * 2),hasDays(2)",
    })
    void simplifyDurationAssertions(String before, String after) {
        //language=java
        String template = """
          import java.time.Duration;
                        
          import static org.assertj.core.api.Assertions.assertThat;
                        
          class Foo {
              void testMethod(Duration time) {
                  assertThat(time).%s;
              }
          }
          """;
        rewriteRun(java(template.formatted(before), template.formatted(after)));
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
    void doesNotChangeWhenVariableIsMultiplied() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
                            
              import static org.assertj.core.api.Assertions.assertThat;
                            
              class Foo {
                  void testMethod(Duration time, int variable) {
                      assertThat(time).hasHours(2 * variable);
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
