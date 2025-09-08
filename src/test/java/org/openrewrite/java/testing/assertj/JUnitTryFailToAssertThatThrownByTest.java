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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JUnitTryFailToAssertThatThrownByTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "assertj-core-3"))
          .recipe(new JUnitTryFailToAssertThatThrownBy());
    }

    @DocumentExample
    @Test
    void simpleTryCatchFailBlock() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testExceptionIsThrown() {
                      try {
                          someMethodThatShouldThrow();
                          fail("Expected IllegalArgumentException to be thrown");
                      } catch (IllegalArgumentException e) {
                          // Expected exception
                      }
                  }

                  void someMethodThatShouldThrow() {
                      throw new IllegalArgumentException("Invalid argument");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  @Test
                  void testExceptionIsThrown() {
                      assertThatThrownBy(() -> someMethodThatShouldThrow()).isInstanceOf(IllegalArgumentException.class);
                  }

                  void someMethodThatShouldThrow() {
                      throw new IllegalArgumentException("Invalid argument");
                  }
              }
              """
          )
        );
    }

    @Test
    void tryCatchWithMessageAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  @Test
                  void testExceptionWithMessage() {
                      try {
                          process();
                          fail("Should have thrown NullPointerException");
                      } catch (NullPointerException e) {
                          assertEquals("Input cannot be null", e.getMessage());
                      }
                  }

                  void process() {
                      throw new NullPointerException("Input cannot be null");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  @Test
                  void testExceptionWithMessage() {
                      assertThatThrownBy(() -> process()).isInstanceOf(NullPointerException.class).hasMessage("Input cannot be null");
                  }

                  void process() {
                      throw new NullPointerException("Input cannot be null");
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleStatementsInTryBlock() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testComplexException() {
                      try {
                          String input = prepareInput();
                          validateInput(input);
                          processInput(input);
                          fail("Expected exception");
                      } catch (IllegalStateException e) {
                          // Expected
                      }
                  }

                  String prepareInput() { return "test"; }
                  void validateInput(String s) { }
                  void processInput(String s) { throw new IllegalStateException(); }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  @Test
                  void testComplexException() {
                      assertThatThrownBy(() -> {
                          String input = prepareInput();
                          validateInput(input);
                          processInput(input);
                      }).isInstanceOf(IllegalStateException.class);
                  }

                  String prepareInput() { return "test"; }
                  void validateInput(String s) { }
                  void processInput(String s) { throw new IllegalStateException(); }
              }
              """
          )
        );
    }

    @Test
    void assertjFailMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testExceptionIsThrown() {
                      try {
                          someMethodThatShouldThrow();
                          fail("Expected IllegalArgumentException to be thrown");
                      } catch (IllegalArgumentException e) {
                          // Expected exception
                      }
                  }

                  void someMethodThatShouldThrow() {
                      throw new IllegalArgumentException("Invalid argument");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  @Test
                  void testExceptionIsThrown() {
                      assertThatThrownBy(() -> someMethodThatShouldThrow()).isInstanceOf(IllegalArgumentException.class);
                  }

                  void someMethodThatShouldThrow() {
                      throw new IllegalArgumentException("Invalid argument");
                  }
              }
              """
          )
        );
    }

    @Test
    void junit4FailMethod() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "assertj-core-3")),
          java(
            """
              import org.junit.Test;
              import static org.junit.Assert.fail;

              public class MyTest {
                  @Test
                  public void testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          // Expected
                      }
                  }

                  void doSomething() {
                      throw new RuntimeException();
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              public class MyTest {
                  @Test
                  public void testException() {
                      assertThatThrownBy(() -> doSomething()).isInstanceOf(RuntimeException.class);
                  }

                  void doSomething() {
                      throw new RuntimeException();
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfNotEndingWithFail() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          System.out.println("No exception thrown");
                      } catch (RuntimeException e) {
                          fail("Unexpected exception");
                      }
                  }

                  void doSomething() { }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfMultipleCatchBlocks() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (IllegalArgumentException e) {
                          // Handle IAE
                      } catch (RuntimeException e) {
                          // Handle other runtime exceptions
                      }
                  }

                  void doSomething() { }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfHasFinally() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          // Expected
                      } finally {
                          cleanup();
                      }
                  }

                  void doSomething() { }
                  void cleanup() { }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfCatchHasMultipleStatements() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          System.out.println("Got exception: " + e);
                          assertEquals("error", e.getMessage());
                      }
                  }

                  void doSomething() { }
              }
              """
          )
        );
    }

    @Test
    void singleStatementLambda() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          service.doWork();
                          fail();
                      } catch (Exception e) {
                          // Expected
                      }
                  }

                  Service service = new Service();

                  class Service {
                      void doWork() throws Exception {
                          throw new Exception();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  @Test
                  void testException() {
                      assertThatThrownBy(() -> service.doWork()).isInstanceOf(Exception.class);
                  }

                  Service service = new Service();

                  class Service {
                      void doWork() throws Exception {
                          throw new Exception();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedTryCatchIsConverted() {
        // Note: This test documents current behavior where nested try-catch blocks
        // are converted. A future enhancement could detect and skip these cases.
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          try {
                              innerMethod();
                          } catch (IllegalStateException e) {
                              // Handle inner exception
                          }
                          outerMethod();
                          fail("Expected exception");
                      } catch (RuntimeException e) {
                          // Expected
                      }
                  }

                  void innerMethod() { }
                  void outerMethod() { throw new RuntimeException(); }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  @Test
                  void testException() {
                      assertThatThrownBy(() -> {
                          try {
                              innerMethod();
                          } catch (IllegalStateException e) {
                              // Handle inner exception
                          }
                          outerMethod();
                      }).isInstanceOf(RuntimeException.class);
                  }

                  void innerMethod() { }
                  void outerMethod() { throw new RuntimeException(); }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfCatchHasReturnStatement() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  boolean testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          return true;
                      }
                      return false;
                  }

                  void doSomething() { throw new RuntimeException(); }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfExceptionIsRethrown() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() throws Exception {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          throw new Exception("Wrapped", e);
                      }
                  }

                  void doSomething() { throw new RuntimeException(); }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfExceptionIsLogged() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail("Expected exception");
                      } catch (RuntimeException e) {
                          System.out.println("Got expected exception: " + e);
                      }
                  }

                  void doSomething() { throw new RuntimeException("error"); }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfExceptionVariableIsUsed() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  private Exception lastException;

                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          lastException = e;
                      }
                  }

                  void doSomething() { throw new RuntimeException(); }
              }
              """
          )
        );
    }

    @Test
    void dropsCommentsInCatchBlock() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail("Should throw exception");
                      } catch (RuntimeException e) {
                          // This is the expected behavior
                          // The method should throw RuntimeException
                          // when given invalid input
                      }
                  }

                  void doSomething() { throw new RuntimeException(); }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThatThrownBy;

              class MyTest {
                  @Test
                  void testException() {
                      assertThatThrownBy(() -> doSomething()).isInstanceOf(RuntimeException.class);
                  }

                  void doSomething() { throw new RuntimeException(); }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfTryWithResources() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.io.StringReader;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try (StringReader reader = new StringReader("test")) {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          // Expected
                      }
                  }

                  void doSomething() { throw new RuntimeException(); }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfCatchHasCustomAssertions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          assertTrue(e.getMessage().contains("error"));
                          assertNotNull(e.getCause());
                      }
                  }

                  void doSomething() {
                      throw new RuntimeException("error", new IllegalStateException());
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeIfExceptionUsedInComplexWay() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.fail;

              class MyTest {
                  @Test
                  void testException() {
                      try {
                          doSomething();
                          fail();
                      } catch (RuntimeException e) {
                          String message = e.getMessage();
                          if (message != null && message.contains("specific")) {
                              System.out.println("Got specific error: " + message);
                          }
                      }
                  }

                  void doSomething() { throw new RuntimeException("specific error"); }
              }
              """
          )
        );
    }
}
