/*
 * Copyright 2021 the original author or authors.
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

class AssertThrowsOnLastStatementTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9.2"))
          .recipe(new AssertThrowsOnLastStatement());
    }

    @DocumentExample
    @Test
    void applyToLastStatementWithDeclaringVariableThreeLines() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;
                            
              class MyTest {
                            
                  @Test
                  public void test() {
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
                          foo();
                          System.out.println("foo");
                          foo();
                      });
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;
                            
              class MyTest {
                            
                  @Test
                  public void test() {
                      foo();
                      System.out.println("foo");
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> foo());
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void applyToLastStatementNoDeclaringVariableTwoLines() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;
                            
              class MyTest {
                            
                  @Test
                  public void test() {
                      assertThrows(IllegalArgumentException.class, () -> {
                          System.out.println("foo");
                          foo();
                      });
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertThrows;
                            
              class MyTest {
                            
                  @Test
                  public void test() {
                      System.out.println("foo");
                      assertThrows(IllegalArgumentException.class, () -> foo());
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void makeNoChagesAsOneLine() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;
                            
              class MyTest {
                            
                  @Test
                  public void test() {
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> foo());
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertThrows;
                            
              class MyTest {
                            
                  @Test
                  public void test() {
                      Throwable exception = assertThrows(IllegalArgumentException.class, () -> foo());
                      assertEquals("Error message", exception.getMessage());
                  }
                  void foo() {
                  }
              }
              """
          )
        );
    }
}
