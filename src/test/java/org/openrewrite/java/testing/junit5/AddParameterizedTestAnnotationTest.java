package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class AddParameterizedTestAnnotationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "junit-jupiter-api-5.9"))
          .recipe(new AddParameterizedTestAnnotation());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/314")
    @Test
    void replaceTestWithParameterizedTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.params.provider.ValueSource;
              
              class NumbersTest {
                @Test
                @ValueSource(ints = {1, 3, 5, -3, 15,Integer.MAX_VALUE})
                void testIsOdd(int number) {
                    assertTrue(number % 2 != 0);
                }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;
              
              class NumbersTest {
                @ParameterizedTest
                @ValueSource(ints = {1, 3, 5, -3, 15,Integer.MAX_VALUE})
                void testIsOdd(int number) {
                    assertTrue(number % 2 != 0);
                }
              }
              """
          )
        );
    }

    @Test
    void onlyReplacesWithValueSourceAnnotation() {
        /*
        This test ensures that the recipe will only run on code that includes a
        @ValueSource(...) annotation.
         */
        rewriteRun(
          java(
            """
              @Test
              void testIsOdd(int number) {
                assertTrue(number % 2 != 0);
              }
              """
          )
        );
    }

    @Test
    void replacesCsvSource() {
        rewriteRun(
          java(
            """
              @Test
              @CsvSource({"test@test.com"})
              void processUserData(String email) {
                System.out.println(email);
              }
              """,
            """
              @ParameterizedTest
              @CsvSource({"test@test.com"})
              void processUserData(String email) {
                System.out.println(email);
              }
              """
          )
        );
    }

    @Test
    void replacesMethodSource() {
        rewriteRun(
          java(
            """
              @Test
              @MethodSource()
              void foo() {
                System.out.println("bar");
              }
              """,
            """
              @ParameterizedTest
              @MethodSource()
              void foo() {
                System.out.println("bar");
              }
              """
          )
        );
    }
}
