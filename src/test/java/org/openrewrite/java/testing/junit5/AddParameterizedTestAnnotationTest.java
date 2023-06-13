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
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "junit-jupiter-params-5.9"))
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
    void replaceTestWithParameterizedTestRegardlessOfOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.params.provider.ValueSource;
                            
              class NumbersTest {
                @ValueSource(ints = {1, 3, 5, -3, 15,Integer.MAX_VALUE})
                @Test
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
              import java.util.stream.Stream;
                            
              class TestClass {
                @ParameterizedTest
                @MethodSource("someMethod")
                void foo() {
                  System.out.println("bar");
                }
                
                static Stream<String> someMethod() {
                    return Stream.of("data1", "data2", "data3");
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
              import org.junit.jupiter.params.provider.ValueSource;
                           
              class TestClass {
                 @ValueSource(ints = {1, 3, 5, -3, 15,Integer.MAX_VALUE})
                 void testIsOdd(int number) {
                     assertTrue(number % 2 != 0);
                 }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;
                           
              class TestClass {
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
}
