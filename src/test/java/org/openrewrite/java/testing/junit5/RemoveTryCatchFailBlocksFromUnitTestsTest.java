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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"NumericOverflow", "divzero"})
class RemoveTryCatchFailBlocksFromUnitTestsTest implements RewriteTest {
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9"))
          .recipe(new RemoveTryCatchFailBlocksFromUnitTests());
    }

    @Test
    @DocumentExample
    void removeTryCatchBlock() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;
              import org.junit.jupiter.api.Test;
                            
              class MyTest {
                  @Test
                  public void testMethod() {
                      try {
                          int divide = 50 / 0;
                      }catch (ArithmeticException e) {
                          Assert.fail(e.getMessage());
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;
                            
              class MyTest {
                  @Test
                  public void testMethod() {
                      Assertions.assertDoesNotThrow(() -> {
                          int divide = 50 / 0;
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void failMethodArgIsNotGetMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.Assert;
                           
              class MyTest {
                  @Test
                  void aTest() {
                      try {
                          int divide = 50/0;
                      } catch (Exception e) {
                          Assert.fail(cleanUpAndReturnMessage());
                      }
                  }
                  
                  String cleanUpAndReturnMessage() {
                      System.out.println("clean up code");
                      return "Oh no!";
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRunWithMultipleCatchBlocks() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.Assert;
                            
              class MyTest {
                  @Test
                  void aTest() {
                      try {
                          System.out.println("unsafe code here");
                      } catch (Exception e) {
                          Assert.fail(e.getMessage());
                      } catch (ArithmeticException other) {
                          Assert.fail(other.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void catchHasMultipleStatements() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.Assert;
                            
              class MyTest {
                  @Test
                  void aTest() {
                      try {
                          System.out.println("unsafe code");
                      } catch (Exception e) {
                          System.out.println("hello world");
                          Assert.fail(e.getMessage());
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRunOnTryWithResources() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.io.PrintWriter;
              import org.junit.Assert;
                            
              class MyTest {
                  @Test
                  void aTest()  {
                      try (PrintWriter writer = new PrintWriter("tests.txt")) {
                          writer.println("hello world");
                      } catch (Exception e) {
                          Assert.fail("Some message");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void statementsBeforeAndAfterTryBlock() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;
              import org.junit.jupiter.api.Test;
                            
              class MyTest {
                  @Test
                  public void testMethod() {
                      System.out.println("statements before");
                      int x = 50;
                      try {
                          int divide = 50 / 0;
                          System.out.println("hello world");
                      }catch (ArithmeticException e) {
                          Assert.fail(e.getMessage());
                      }
                      System.out.println("statements after");
                      int y = 50;
                      int z = x + y;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;
                            
              class MyTest {
                  @Test
                  public void testMethod() {
                      System.out.println("statements before");
                      int x = 50;
                      Assertions.assertDoesNotThrow(() -> {
                          int divide = 50 / 0;
                          System.out.println("hello world");
                      });
                      System.out.println("statements after");
                      int y = 50;
                      int z = x + y;
                  }
              }
              """
          )
        );
    }
}
