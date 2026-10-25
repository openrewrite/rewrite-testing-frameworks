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
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("ControlFlowWithEmptyBody")
class KotlinTestMethodsShouldReturnUnitTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5", "junit-jupiter-params-5"))
          .recipe(new KotlinTestMethodsShouldReturnUnit())
          // https://github.com/openrewrite/rewrite/issues/6408
          .typeValidationOptions(TypeValidation.all().methodInvocations(false));
    }

    @DocumentExample
    @Test
    void addUnitReturnTypeToSingleExprTestMethod() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() = run {
                      doSomething()
                  }

                  fun doSomething(): Int {
                      return 5
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest(): Unit = run {
                      doSomething()
                  }

                  fun doSomething(): Int {
                      return 5
                  }
              }
              """
          )
        );
    }

    @Test
    void removeReturnTypeAndReturnExpression() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest(): String {
                      println("test")
                      return "done"
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      println("test")
                  }
              }
              """
          )
        );
    }

    @Test
    void removeReturnTypeFromTestNGTestMethod() {
        //language=kotlin
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(),
              "testng-7")),
          kotlin(
            """
              import org.testng.annotations.Test

              class ATest {
                  @Test
                  fun myTest(): String {
                      println("test")
                      return "done"
                  }
              }
              """,
            """
              import org.testng.annotations.Test

              class ATest {
                  @Test
                  fun myTest() {
                      println("test")
                  }
              }
              """
          )
        );
    }

    @Test
    void removeReturnTypeAndPreserveReturnStatement() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest(): String {
                      doSomething()
                      return doSomething()
                  }

                  fun doSomething(): Int {
                      return 5
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      doSomething()
                      doSomething()
                  }

                  fun doSomething(): Int {
                      return 5
                  }
              }
              """
          )
        );
    }

    @Test
    void removeMultipleReturns() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest(): String {
                      if (true) {
                          return "true"
                      } else {
                          return "false"
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      if (true) {
                          return
                      } else {
                          return
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveLabeledReturnInLambda() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() : Int {
                      val value = run { return@run 42 }
                      return value
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      val value = run { return@run 42 }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveReturnInLocalFunction() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() : Int {
                      fun helper(): Int {
                          return 42
                      }
                      return helper()
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      fun helper(): Int {
                          return 42
                      }
                      helper()
                  }
              }
              """
          )
        );
    }

    @Test
    void keepEarlyReturnAsGuard() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest(): Int {
                      if (System.getenv("SKIP") != null) {
                          return 0
                      }
                      println("test")
                      return 1
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      if (System.getenv("SKIP") != null) {
                          return
                      }
                      println("test")
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeNonLocalReturnFromInlineLambda() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest(): Int {
                      listOf(1, 2).forEach {
                          if (it == 2) return it
                      }
                      return 0
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeEarlyReturnWithSideEffects() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest(): Int {
                      if (System.getenv("SKIP") != null) {
                          return cleanUp()
                      }
                      return 1
                  }

                  fun cleanUp(): Int {
                      return 0
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeTestInvokedAsHelper() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun compute(): Int {
                      return 42
                  }

                  @Test
                  fun reuse() {
                      println(compute())
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveReturnInAnonymousClass() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() : String {
                      val r = Runnable() {
                          override fun run() {
                              println("running")
                          }

                          fun getValue() : String {
                              return "value"
                          }
                      }
                      r.run()
                      return "done"
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      val r = Runnable() {
                          override fun run() {
                              println("running")
                          }

                          fun getValue() : String {
                              return "value"
                          }
                      }
                      r.run()
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveReturnInObjectExpression() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() : String {
                      val r = object : Runnable() {
                          override fun run() {
                              println("running")
                          }

                          fun getValue() : String {
                              return "value"
                          }
                      }
                      r.run()
                      return "done"
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun myTest() {
                      val r = object : Runnable() {
                          override fun run() {
                              println("running")
                          }

                          fun getValue() : String {
                              return "value"
                          }
                      }
                      r.run()
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotChangeTestFactoryMethods() {
        // `@TestFactory` methods are required by JUnit Jupiter to return a value such as
        // `Collection<DynamicTest>`, unlike `@Test`/`@ParameterizedTest`/`@RepeatedTest`/`@TestTemplate`,
        // so they must not be forced to `Unit`.
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.DynamicTest
              import org.junit.jupiter.api.TestFactory

              class ATest {
                  @TestFactory
                  fun myTestFactory() = listOf(DynamicTest.dynamicTest("test") {})
              }
              """
          )
        );
    }

    @Test
    void doNotChangeAlreadyUnitTestMethods() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class ATest {
                  @Test
                  fun testMethod() {
                      println("already unit")
                  }
              }
              """
          )
        );
    }
}
