package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class UseAssertSameTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
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
