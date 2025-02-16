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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseAssertSameTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new UseAssertSame());
    }

    @DocumentExample
    @Test
    void assertSameForSimpleBooleanComparison() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = number;
                      assertTrue(number == otherNumber);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertSame;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = number;
                      assertSame(number, otherNumber);
                  }
              }
              """
          )
        );
    }

    @Test
    void usingStringMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = number;
                      assertTrue(number == otherNumber, "Something is not right");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertSame;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = number;
                      assertSame(number, otherNumber, "Something is not right");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertFalse() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertFalse;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = "thirty-seven";
                      assertFalse(number == otherNumber);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotSame;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = "thirty-seven";
                      assertNotSame(number, otherNumber);
                  }
              }
              """
          )
        );
    }

    @Test
    void notEqual() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = "thirty-seven";
                      assertTrue(number != otherNumber);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertNotSame;

              class MyTest {

                  @Test
                  public void test() {
                      String number = "thirty-six";
                      String otherNumber = "thirty-seven";
                      assertNotSame(number, otherNumber);
                  }
              }
              """
          )
        );
    }
}
