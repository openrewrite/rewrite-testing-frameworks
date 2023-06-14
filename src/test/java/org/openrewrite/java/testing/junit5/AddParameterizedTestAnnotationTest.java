package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddParameterizedTestAnnotationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "junit-jupiter-params-5.9"))
          .recipe(new AddParameterizedTestAnnotation());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/314")
    @Test
    @DocumentExample
    void replaceTestWithParameterizedTest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.params.provider.ValueSource;
              import static org.junit.jupiter.api.Assertions.*;
                            
              class NumbersTest {
                  @Test
                  @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
                  void testIsOdd(int number) {
                      assertTrue(number % 2 != 0);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;
              import static org.junit.jupiter.api.Assertions.*;
                            
              class NumbersTest {
                  @ParameterizedTest
                  @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
                  void testIsOdd(int number) {
                      assertTrue(number % 2 != 0);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceTestWithParameterizedTestRegardlessOfOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.params.provider.ValueSource;
              import static org.junit.jupiter.api.Assertions.*;
                            
              class NumbersTest {
                  @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
                  @Test
                  void testIsOdd(int number) {
                      assertTrue(number % 2 != 0);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;
              import static org.junit.jupiter.api.Assertions.*;
                            
              class NumbersTest {
                  @ParameterizedTest
                  @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
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
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              class NumbersTest {
                  @Test
                  void printMessage() {
                      System.out.println("message");
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesCsvSource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.CsvSource;
              import org.junit.jupiter.api.Test;
                            
              class TestClass {
                  @Test
                  @CsvSource({"test@test.com"})
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;
                            
              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"test@test.com"})
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesMethodSource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.params.provider.MethodSource;
                            
              class TestClass {
                  @Test
                  @MethodSource()
                  void foo() {
                      System.out.println("bar");
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.MethodSource;
                            
              class TestClass {
                  @ParameterizedTest
                  @MethodSource()
                  void foo() {
                      System.out.println("bar");
                  }
              }
              """
          )
        );
    }

    @Test
    void addMissingAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.params.provider.ValueSource;
              import static org.junit.jupiter.api.Assertions.*;
                           
              class TestClass {
                  @Test
                  @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
                  void testIsOdd(int number) {
                      assertTrue(number % 2 != 0);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;
              import static org.junit.jupiter.api.Assertions.*;
                            
              class TestClass {
                  @ParameterizedTest
                  @ValueSource(ints = {1, 3, 5, -3, 15, Integer.MAX_VALUE})
                  void testIsOdd(int number) {
                      assertTrue(number % 2 != 0);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesNullSource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.NullSource;
              import org.junit.jupiter.api.Test;
                           
              class TestClass {
                  @Test
                  @NullSource
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.NullSource;
                            
              class TestClass {
                  @ParameterizedTest
                  @NullSource
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesEmptySource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.EmptySource;
              import org.junit.jupiter.api.Test;
                           
              class TestClass {
                  @Test
                  @EmptySource
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.EmptySource;
                            
              class TestClass {
                  @ParameterizedTest
                  @EmptySource
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesNullAndEmptySource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.NullAndEmptySource;
              import org.junit.jupiter.api.Test;
                           
              class TestClass {
                  @Test
                  @NullAndEmptySource
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.NullAndEmptySource;
                            
              class TestClass {
                  @ParameterizedTest
                  @NullAndEmptySource
                  void processUserData(String email) {
                      System.out.println(email);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesEnumSource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.EnumSource;
              import org.junit.jupiter.api.Test;
                           
              class TestClass {
                  enum time {
                      MORNING,
                      NOON,
                      AFTERNOON,
                      MIDNIGHT
                  }
                  
                  @Test
                  @EnumSource
                  void processTime(time timeOfDay) {
                      System.out.println("Its " + timeOfDay);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.EnumSource;
                            
              class TestClass {
                  enum time {
                      MORNING,
                      NOON,
                      AFTERNOON,
                      MIDNIGHT
                  }
                  
                  @ParameterizedTest
                  @EnumSource
                  void processTime(time timeOfDay) {
                      System.out.println("Its " + timeOfDay);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesCsvFileSource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.CsvFileSource;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.*;
                           
              class TestClass {
                  @Test
                  @CsvFileSource(files = "src/test/resources/two-column.csv", numLinesToSkip = 1)
                  void testWithCsvFileSourceFromFile(String country, int reference) {
                      assertNotNull(country);
                      assertNotEquals(0, reference);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvFileSource;
              import static org.junit.jupiter.api.Assertions.*;
                            
              class TestClass {
                  @ParameterizedTest
                  @CsvFileSource(files = "src/test/resources/two-column.csv", numLinesToSkip = 1)
                  void testWithCsvFileSourceFromFile(String country, int reference) {
                      assertNotNull(country);
                      assertNotEquals(0, reference);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesArgumentSource() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.provider.ArgumentsSource;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.*;
                           
              class TestClass {
                  @Test
                  @ArgumentsSource(MyArgumentsProvider.class)
                  void testWithArgumentsSource(String argument) {
                      assertNotNull(argument);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ArgumentsSource;
              import static org.junit.jupiter.api.Assertions.*;
                            
              class TestClass {
                  @ParameterizedTest
                  @ArgumentsSource(MyArgumentsProvider.class)
                  void testWithArgumentsSource(String argument) {
                      assertNotNull(argument);
                  }
              }
              """
          )
        );
    }
}
