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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TruthThrowableAssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TruthThrowableAssertions())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"));
    }

    @DocumentExample
    @Test
    void hasMessageThatContains() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception e = new IllegalArgumentException("Invalid argument provided");
                      assertThat(e).hasMessageThat().contains("Invalid");
                  }
              }
              """,
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception e = new IllegalArgumentException("Invalid argument provided");
                      assertThat(e).hasMessageContaining("Invalid");
                  }
              }
              """
          )
        );
    }

    @Test
    void hasMessageThatIsEqualTo() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception e = new RuntimeException("Error occurred");
                      assertThat(e).hasMessageThat().isEqualTo("Error occurred");
                  }
              }
              """,
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception e = new RuntimeException("Error occurred");
                      assertThat(e).hasMessage("Error occurred");
                  }
              }
              """
          )
        );
    }

    @Test
    void hasCauseThatIsInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception cause = new IllegalStateException("Bad state");
                      Exception e = new RuntimeException("Wrapper", cause);
                      assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
                  }
              }
              """,
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception cause = new IllegalStateException("Bad state");
                      Exception e = new RuntimeException("Wrapper", cause);
                      assertThat(e).hasCauseInstanceOf(IllegalStateException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleThrowableAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception cause = new IllegalStateException("Inner error");
                      Exception e = new RuntimeException("Outer error", cause);

                      assertThat(e).hasMessageThat().contains("Outer");
                      assertThat(e).hasMessageThat().isEqualTo("Outer error");
                      assertThat(e).hasCauseThat().isInstanceOf(IllegalStateException.class);
                  }
              }
              """,
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Exception cause = new IllegalStateException("Inner error");
                      Exception e = new RuntimeException("Outer error", cause);

                      assertThat(e).hasMessageContaining("Outer");
                      assertThat(e).hasMessage("Outer error");
                      assertThat(e).hasCauseInstanceOf(IllegalStateException.class);
                  }
              }
              """
          )
        );
    }
}
