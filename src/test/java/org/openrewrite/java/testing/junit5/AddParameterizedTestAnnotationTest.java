package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class AddParameterizedTestAnnotationTest implements RewriteTest {
    @Test
    void replaceTestWithParameterizedTest() {
        rewriteRun(
          java(
            """
              @Test
              @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
              void testIsOdd(int number) {
                  assertTrue(number % 2 != 0);
              }
              """,
            """
              @ParameterizedTest
              @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
              void testIsOdd(int number) {
                  assertTrue(number % 2 != 0);
              } 
              """
          )
        );
    }

    @Test
    void onlyReplacesWithValueSourceAnnotation() {
        rewriteRun(
          java(
            """
              @Test
              void testIsOdd(int number) {
                assertTrue(number % 2 != 0);
              }
              """,
            """
              @Test
              void testIsOdd(int number) {
                assertTrue(number % 2 != 0);
              }
              """
          )
        );
    }
}