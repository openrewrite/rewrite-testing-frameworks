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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CsvSourceToValueSourceTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "junit-jupiter-params-5"))
          .recipe(new CsvSourceToValueSource());
    }

    @DocumentExample
    @Test
    void replaceCsvSourceWithValueSourceForStrings() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"apple", "banana", "cherry"})
                  void testWithStrings(String fruit) {
                      System.out.println(fruit);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(strings = {"apple", "banana", "cherry"})
                  void testWithStrings(String fruit) {
                      System.out.println(fruit);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainNewlines() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({
                    "apple",
                    "banana",
                    "cherry"
                  })
                  void testWithStrings(String fruit) {
                      System.out.println(fruit);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(strings = {
                    "apple",
                    "banana",
                    "cherry"
                  })
                  void testWithStrings(String fruit) {
                      System.out.println(fruit);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCsvSourceWithValueSourceForIntegers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"1", "2", "3", "42"})
                  void testWithIntegers(int number) {
                      System.out.println(number);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(ints = {1, 2, 3, 42})
                  void testWithIntegers(int number) {
                      System.out.println(number);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCsvSourceWithValueSourceForLongs() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"100", "200", "999999999999"})
                  void testWithLongs(long number) {
                      System.out.println(number);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(longs = {100L, 200L, 999999999999L})
                  void testWithLongs(long number) {
                      System.out.println(number);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCsvSourceWithValueSourceForDoubles() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"1.5", "2.7", "3.14159"})
                  void testWithDoubles(double number) {
                      System.out.println(number);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(doubles = {1.5, 2.7, 3.14159})
                  void testWithDoubles(double number) {
                      System.out.println(number);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCsvSourceWithValueSourceForBooleans() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"true", "false", "true"})
                  void testWithBooleans(boolean flag) {
                      System.out.println(flag);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(booleans = {true, false, true})
                  void testWithBooleans(boolean flag) {
                      System.out.println(flag);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenMultipleParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"apple, 1", "banana, 2", "cherry, 3"})
                  void testWithMultipleParams(String fruit, int count) {
                      System.out.println(fruit + ": " + count);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotReplaceWhenAdditionalAruguments() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource(value = {"apple", "banana", "N/A"}, nullValues = "N/A")
                  void testWithMultipleParams(String fruit) {
                      System.out.println(fruit);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCsvSourceWithSingleValue() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource("apple")
                  void testWithSingleString(String fruit) {
                      System.out.println(fruit);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(strings = "apple")
                  void testWithSingleString(String fruit) {
                      System.out.println(fruit);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCsvSourceWithValueAttribute() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource(value = {"test1", "test2", "test3"})
                  void testWithValueAttribute(String value) {
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(strings = {"test1", "test2", "test3"})
                  void testWithValueAttribute(String value) {
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceCsvSourceWithChars() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.CsvSource;

              class TestClass {
                  @ParameterizedTest
                  @CsvSource({"a", "b", "c"})
                  void testWithChars(char letter) {
                      System.out.println(letter);
                  }
              }
              """,
            """
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class TestClass {
                  @ParameterizedTest
                  @ValueSource(chars = {'a', 'b', 'c'})
                  void testWithChars(char letter) {
                      System.out.println(letter);
                  }
              }
              """
          )
        );
    }
}
