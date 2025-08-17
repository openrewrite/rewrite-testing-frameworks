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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("JUnitMalformedDeclaration")
class TestMethodsShouldBeVoidTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "junit-jupiter-params-5"))
          .recipe(new TestMethodsShouldBeVoid());
    }

    @DocumentExample
    @Test
    void changeReturnTypeToVoid() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  int testMethod() {
                      int i = 42;
                      return i;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                      int i = 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void removeReturnStatementOnly() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  String testMethod() {
                      System.out.println("test");
                      return "done";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                      System.out.println("test");
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveReturnInLambda() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.function.Supplier;

              class ATest {
                  @Test
                  int testMethod() {
                      Supplier<Integer> supplier = () -> {
                          return 42;
                      };
                      return supplier.get();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.function.Supplier;

              class ATest {
                  @Test
                  void testMethod() {
                      Supplier<Integer> supplier = () -> {
                          return 42;
                      };
                      supplier.get();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveReturnInAnonymousClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  String testMethod() {
                      Runnable r = new Runnable() {
                          @Override
                          public void run() {
                              System.out.println("running");
                          }

                          public String getValue() {
                              return "value";
                          }
                      };
                      r.run();
                      return "done";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                      Runnable r = new Runnable() {
                          @Override
                          public void run() {
                              System.out.println("running");
                          }

                          public String getValue() {
                              return "value";
                          }
                      };
                      r.run();
                  }
              }
              """
          )
        );
    }

    @Test
    void handleParameterizedTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {
                  @ParameterizedTest
                  @ValueSource(strings = {"test1", "test2"})
                  boolean testMethod(String value) {
                      System.out.println(value);
                      return value.length() > 0;
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {
                  @ParameterizedTest
                  @ValueSource(strings = {"test1", "test2"})
                  void testMethod(String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void handleRepeatedTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.RepeatedTest;

              class ATest {
                  @RepeatedTest(3)
                  String testMethod() {
                      return "repeated";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.RepeatedTest;

              class ATest {
                  @RepeatedTest(3)
                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeVoidMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                      System.out.println("already void");
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonTestMethods() {
        //language=java
        rewriteRun(
          java(
            """
              class ATest {
                  int notATestMethod() {
                      return 42;
                  }

                  String helperMethod() {
                      return "helper";
                  }
              }
              """
          )
        );
    }

    @Test
    void handleMultipleReturns() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  int testMethod() {
                      if (true) {
                          return 1;
                      } else {
                          return 2;
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                      if (true) {
                      } else {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveExpressionFromReturn() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  int testMethod() {
                      return calculateValue();
                  }

                  private int calculateValue() {
                      return 42;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                      calculateValue();
                  }

                  private int calculateValue() {
                      return 42;
                  }
              }
              """
          )
        );
    }
}
