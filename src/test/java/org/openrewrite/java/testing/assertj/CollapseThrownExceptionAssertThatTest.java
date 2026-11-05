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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CollapseThrownExceptionAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "assertj-core-3",
              "junit-jupiter-api-5"))
          .recipe(new CollapseThrownExceptionAssertThat());
    }

    @DocumentExample
    @Test
    void junitAssertThrows() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {
                  void test() {
                      AssertionError e = assertThrows(AssertionError.class, () -> run());
                      assertThat(e).hasMessageStartingWith("A recipe")
                        .hasMessageContaining("static fields");
                  }

                  void run() {
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  void test() {
                      assertThatThrownBy(() -> run()).isInstanceOf(AssertionError.class)
                        .hasMessageStartingWith("A recipe")
                        .hasMessageContaining("static fields");
                  }

                  void run() {
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThatExceptionOfTypeActual() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

              class MyTest {
                  void test() {
                      IllegalStateException e = assertThatExceptionOfType(IllegalStateException.class).isThrownBy(this::run).actual();
                      assertThat(e).hasMessage("boom");
                  }

                  void run() {
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  void test() {
                      assertThatThrownBy(this::run).isInstanceOf(IllegalStateException.class).hasMessage("boom");
                  }

                  void run() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsOtherUsesOfImports() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {
                  void test() {
                      IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                      assertThat(e).hasMessage("boom");
                      assertThrows(IllegalArgumentException.class, () -> run());
                      assertThat("a").isNotEmpty();
                  }

                  void run() {
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.assertThatThrownBy;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {
                  void test() {
                      assertThatThrownBy(() -> run()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
                      assertThrows(IllegalArgumentException.class, () -> run());
                      assertThat("a").isNotEmpty();
                  }

                  void run() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsCommentOnCapture() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class MyTest {
                  void test() {
                      // the call should fail
                      IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                      assertThat(e).hasMessage("boom");
                  }

                  void run() {
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  void test() {
                      // the call should fail
                      assertThatThrownBy(() -> run()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
                  }

                  void run() {
                  }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void variableUsedAgain() {
            //language=java
            rewriteRun(
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                          assertThat(e).hasMessage("boom");
                          assertThat(e.getCause()).isNull();
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void variableUsedInChainArgument() {
            //language=java
            rewriteRun(
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                          assertThat(e).hasMessage(e.getLocalizedMessage());
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertionNotDirectlyAfterCapture() {
            //language=java
            rewriteRun(
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                          run();
                          assertThat(e).hasMessage("boom");
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void chainDependsOnExceptionType() {
            //language=java
            rewriteRun(
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          MyException e = assertThrows(MyException.class, () -> run());
                          assertThat(e).extracting(MyException::getCode).isEqualTo(42);
                      }

                      void run() {
                      }

                      static class MyException extends RuntimeException {
                          int getCode() {
                              return 42;
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void junitExecutableVariable() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.junit.jupiter.api.function.Executable;

                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          Executable executable = () -> run();
                          IllegalStateException e = assertThrows(IllegalStateException.class, executable);
                          assertThat(e).hasMessage("boom");
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void assertThrowsWithMessage() {
            //language=java
            rewriteRun(
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          IllegalStateException e = assertThrows(IllegalStateException.class, () -> run(), "should throw");
                          assertThat(e).hasMessage("boom");
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void commentBeforeAssertion() {
            //language=java
            rewriteRun(
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                          // the message is part of the contract
                          assertThat(e).hasMessage("boom");
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void castInAssertThat() {
            //language=java
            rewriteRun(
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test() {
                          IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                          assertThat((RuntimeException) e).hasMessage("boom");
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void conditionOnExceptionType() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.assertj.core.api.Condition;

                  import static org.assertj.core.api.Assertions.assertThat;
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  class MyTest {
                      void test(Condition<IllegalStateException> condition) {
                          IllegalStateException e = assertThrows(IllegalStateException.class, () -> run());
                          assertThat(e).is(condition);
                      }

                      void run() {
                      }
                  }
                  """
              )
            );
        }
    }
}
