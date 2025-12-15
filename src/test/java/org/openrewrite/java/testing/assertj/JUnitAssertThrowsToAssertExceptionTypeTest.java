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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"Convert2MethodRef", "ThrowableNotThrown"})
class JUnitAssertThrowsToAssertExceptionTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new JUnitAssertThrowsToAssertExceptionType());
    }

    @DocumentExample
    @Test
    void toAssertExceptionOfType() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      assertThrows(NullPointerException.class, () -> foo());
                  }
                  void foo() {
                      throw new NullPointerException();
                  }
              }
              """,
            """
              import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> foo());
                  }
                  void foo() {
                      throw new NullPointerException();
                  }
              }
              """
          )
        );
    }

    @Test
    void memberReference() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.concurrent.CompletableFuture;
              import java.util.concurrent.ExecutionException;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class MemberReferenceTest {

                  public void throwsWithMemberReference() {
                      CompletableFuture<Boolean> future = new CompletableFuture<>();
                      assertThrows(ExecutionException.class, future::get);
                  }
              }
              """,
            """
              import java.util.concurrent.CompletableFuture;
              import java.util.concurrent.ExecutionException;

              import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

              public class MemberReferenceTest {

                  public void throwsWithMemberReference() {
                      CompletableFuture<Boolean> future = new CompletableFuture<>();
                      assertThatExceptionOfType(ExecutionException.class).isThrownBy(future::get);
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThrowsAssignment() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      NullPointerException npe = assertThrows(NullPointerException.class, () -> {
                          throw new NullPointerException();
                      });
                  }
              }
              """,
            """
              import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      NullPointerException npe = assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> {
                          throw new NullPointerException();
                      }).actual();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThrowsVarAssignment() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      var npe = assertThrows(NullPointerException.class, () -> {
                          throw new NullPointerException();
                      });
                  }
              }
              """,
            """
              import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      var npe = assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> {
                          throw new NullPointerException();
                      }).actual();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThrowsTernaryAssignment() {
        //language=java
        rewriteRun(
          java(
            """
             import static org.junit.jupiter.api.Assertions.assertThrows;

             public class SimpleExpectedExceptionTest {
                 public void throwsExceptionWithSpecificType() {
                     NullPointerException npe = hashCode() == 42
                       ? new NullPointerException()
                       : assertThrows(NullPointerException.class, () -> {
                         throw new NullPointerException();
                     });
                 }
             }
             """,
            """
              import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      NullPointerException npe = hashCode() == 42
                        ? new NullPointerException()
                        : assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> {
                          throw new NullPointerException();
                      }).actual();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThrowsMethodReturn() {
        //language=java
        rewriteRun(
          java(
            """
                import static org.junit.jupiter.api.Assertions.assertThrows;

                public class SimpleExpectedExceptionTest {
                    public void throwsExceptionWithSpecificType() {
                        NullPointerException npe = exception();
                    }
                    NullPointerException exception() {
                        return assertThrows(NullPointerException.class, () -> {
                            throw new NullPointerException();
                        });
                    }
                }
                """,
            """
                import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

                public class SimpleExpectedExceptionTest {
                    public void throwsExceptionWithSpecificType() {
                        NullPointerException npe = exception();
                    }
                    NullPointerException exception() {
                        return assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> {
                            throw new NullPointerException();
                        }).actual();
                    }
                }
                """
          )
        );
    }

    /**
     * A degenerate case showing we don't perform the conversion when the <code>assertThrows</code> appears
     * immediately inside a J.Lambda.
     */
    @Test
    void assertThrowsConsumerUsage() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Consumer;

              import static org.junit.jupiter.api.Assertions.assertThrows;

              public class SimpleExpectedExceptionTest {
                  public void throwsExceptionWithSpecificType() {
                      Consumer<? extends Throwable> c = ex -> assertThrows(ex.getClass(), () -> {
                          throw ex;
                      });
                  }
              }
              """
          )
        );
    }

    /**
     * A degenerate case showing we don't perform the conversion when the <code>assertThrows</code> appears
     * immediately inside a J.Lambda.
     */
    @Test
    void assertThrowsSupplierUsage() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Supplier;

              import static org.junit.jupiter.api.Assertions.assertThrows;

                public class SimpleExpectedExceptionTest {
                    public void throwsExceptionWithSpecificType() {
                        Supplier<NullPointerException> s = () -> assertThrows(NullPointerException.class, () -> { throw new NullPointerException(); });
                    }
                }
              """
          )
        );
    }

    @Nested
    class WithMessage {

        @ParameterizedTest
        @ValueSource(strings = {"\"message\"", "() -> \"message\""})
        void messageFromDirectObject(String message) {
            //language=java
            rewriteRun(
              java(
                      """
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          assertThrows(NullPointerException.class, () -> foo(), %s);
                      }
                      void foo() {
                          throw new NullPointerException();
                      }
                  }
                  """.formatted(message),
                      """
                  import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          assertThatExceptionOfType(NullPointerException.class).as(%s).isThrownBy(() -> foo());
                      }
                      void foo() {
                          throw new NullPointerException();
                      }
                  }
                  """.formatted(message)
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"\"message\"", "() -> \"message\""})
        void withMemberReference(String message) {
            //language=java
            rewriteRun(
              java(
                      """
                  import java.util.concurrent.CompletableFuture;
                  import java.util.concurrent.ExecutionException;

                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          CompletableFuture<Boolean> future = new CompletableFuture<>();
                          assertThrows(ExecutionException.class, future::get, %s);
                      }
                  }
                  """.formatted(message),
                      """
                  import java.util.concurrent.CompletableFuture;
                  import java.util.concurrent.ExecutionException;

                  import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          CompletableFuture<Boolean> future = new CompletableFuture<>();
                          assertThatExceptionOfType(ExecutionException.class).as(%s).isThrownBy(future::get);
                      }
                  }
                  """.formatted(message)
              )
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"\"message\"", "() -> \"message\""})
        void withReturnValue(String message) {
            //language=java
            rewriteRun(
              java(
                      """
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          NullPointerException npe = assertThrows(NullPointerException.class, this::foo, %s);
                      }
                      void foo() {
                          throw new NullPointerException();
                      }
                  }
                  """.formatted(message),
                      """
                  import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          NullPointerException npe = assertThatExceptionOfType(NullPointerException.class).as(%s).isThrownBy(this::foo).actual();
                      }
                      void foo() {
                          throw new NullPointerException();
                      }
                  }
                  """.formatted(message)
              )
            );

        }

        @ParameterizedTest
        @ValueSource(strings = {"String message = \"message\"", "java.util.function.Supplier<String> message = () -> \"message\""})
        void messageFromVariable(String message) {
            //language=java
            rewriteRun(
              java(
                      """
                  import static org.junit.jupiter.api.Assertions.assertThrows;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          %s;

                          assertThrows(NullPointerException.class, () -> foo(), message);
                      }
                      void foo() {
                          throw new NullPointerException();
                      }
                  }
                  """.formatted(message),
                      """
                  import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

                  public class SimpleExpectedExceptionTest {
                      public void throwsExceptionWithSpecificType() {
                          %s;

                          assertThatExceptionOfType(NullPointerException.class).as(message).isThrownBy(() -> foo());
                      }
                      void foo() {
                          throw new NullPointerException();
                      }
                  }
                  """.formatted(message)
              )
            );
        }
    }
}
