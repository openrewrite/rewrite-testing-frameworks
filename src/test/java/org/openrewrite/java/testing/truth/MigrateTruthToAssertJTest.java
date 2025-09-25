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
package org.openrewrite.java.testing.truth;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.srcTestJava;

class MigrateTruthToAssertJTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResource("/META-INF/rewrite/truth.yml", "org.openrewrite.java.testing.truth.MigrateTruthToAssertJ")
          .parser(JavaParser.fromJavaVersion()
          .classpathFromResources(new InMemoryExecutionContext(), "guava", "truth"));
    }

    @DocumentExample
    @Test
    void basicAssertThatConversion() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      String actual = "hello";
                      assertThat(actual).isEqualTo("hello");
                      assertThat(actual).isNotEqualTo("world");
                      assertThat(actual).isNotNull();
                      assertThat(actual).contains("ell");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      String actual = "hello";
                      assertThat(actual).isEqualTo("hello");
                      assertThat(actual).isNotEqualTo("world");
                      assertThat(actual).isNotNull();
                      assertThat(actual).contains("ell");
                  }
              }
              """
          )
        );
    }

    @Test
    void stringAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      String str = "hello world";
                      assertThat(str).containsMatch("h.*d");
                      assertThat(str).doesNotContainMatch("foo");
                      assertThat(str).hasLength(11);
                      assertThat(str).startsWith("hello");
                      assertThat(str).endsWith("world");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      String str = "hello world";
                      assertThat(str).matches("h.*d");
                      assertThat(str).doesNotMatch("foo");
                      assertThat(str).hasSize(11);
                      assertThat(str).startsWith("hello");
                      assertThat(str).endsWith("world");
                  }
              }
              """
          )
        );
    }

    @Test
    void comparableAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Integer value = 5;
                      assertThat(value).isGreaterThan(3);
                      assertThat(value).isLessThan(10);
                      assertThat(value).isAtLeast(5);
                      assertThat(value).isAtMost(5);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      Integer value = 5;
                      assertThat(value).isGreaterThan(3);
                      assertThat(value).isLessThan(10);
                      assertThat(value).isGreaterThanOrEqualTo(5);
                      assertThat(value).isLessThanOrEqualTo(5);
                  }
              }
              """
          )
        );
    }

    @Test
    void objectAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Object obj1 = new Object();
                      Object obj2 = obj1;
                      Object obj3 = new Object();

                      assertThat(obj1).isSameInstanceAs(obj2);
                      assertThat(obj1).isNotSameInstanceAs(obj3);
                      assertThat(obj1).isInstanceOf(Object.class);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      Object obj1 = new Object();
                      Object obj2 = obj1;
                      Object obj3 = new Object();

                      assertThat(obj1).isSameAs(obj2);
                      assertThat(obj1).isNotSameAs(obj3);
                      assertThat(obj1).isInstanceOf(Object.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void collectionAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;
              import java.util.List;
              import java.util.Arrays;

              class Test {
                  void test() {
                      List<String> list = Arrays.asList("a", "b", "c");
                      assertThat(list).contains("a");
                      assertThat(list).containsExactly("a", "b", "c");
                      assertThat(list).containsExactlyElementsIn(Arrays.asList("a", "b", "c"));
                      assertThat(list).containsAnyIn(Arrays.asList("a", "z"));
                      assertThat(list).containsNoneOf("x", "y", "z");
                      assertThat(list).hasSize(3);
                      assertThat(list).isEmpty();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.List;
              import java.util.Arrays;

              class Test {
                  void test() {
                      List<String> list = Arrays.asList("a", "b", "c");
                      assertThat(list).contains("a");
                      assertThat(list).containsExactly("a", "b", "c");
                      assertThat(list).containsExactlyElementsOf(Arrays.asList("a", "b", "c"));
                      assertThat(list).containsAnyElementsOf(Arrays.asList("a", "z"));
                      assertThat(list).doesNotContain("x", "y", "z");
                      assertThat(list).hasSize(3);
                      assertThat(list).isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void optionalAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;
              import java.util.Optional;

              class Test {
                  void test() {
                      Optional<String> optional = Optional.of("value");
                      assertThat(optional).isPresent();
                      assertThat(optional).hasValue("value");

                      Optional<String> empty = Optional.empty();
                      assertThat(empty).isEmpty();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;

              class Test {
                  void test() {
                      Optional<String> optional = Optional.of("value");
                      assertThat(optional).isPresent();
                      assertThat(optional).contains("value");

                      Optional<String> empty = Optional.empty();
                      assertThat(empty).isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void mapAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;
              import java.util.Map;
              import java.util.HashMap;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("one", 1);
                      map.put("two", 2);

                      assertThat(map).containsKey("one");
                      assertThat(map).containsEntry("one", 1);
                      assertThat(map).doesNotContainKey("three");
                      assertThat(map).hasSize(2);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Map;
              import java.util.HashMap;

              class Test {
                  void test() {
                      Map<String, Integer> map = new HashMap<>();
                      map.put("one", 1);
                      map.put("two", 2);

                      assertThat(map).containsKey("one");
                      assertThat(map).containsEntry("one", 1);
                      assertThat(map).doesNotContainKey("three");
                      assertThat(map).hasSize(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedRecipes() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertWithMessage;

              class Test {
                  void test() {
                      String text = "hello world";
                      assertWithMessage("Text validation").that(text)
                              .contains("hello");
                      assertWithMessage("Text length").that(text)
                              .hasLength(11);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      String text = "hello world";
                      assertThat(text).as("Text validation")
                              .contains("hello");
                      assertThat(text).as("Text length")
                              .hasSize(11);
                  }
              }
              """
          )
        );
    }

    @Test
    void numericAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      int intValue = 42;
                      long longValue = 100L;
                      double doubleValue = 3.14;
                      float floatValue = 2.5f;

                      assertThat(intValue).isEqualTo(42);
                      assertThat(longValue).isGreaterThan(50L);
                      assertThat(doubleValue).isLessThan(4.0);
                      assertThat(floatValue).isNotEqualTo(3.0f);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      int intValue = 42;
                      long longValue = 100L;
                      double doubleValue = 3.14;
                      float floatValue = 2.5f;

                      assertThat(intValue).isEqualTo(42);
                      assertThat(longValue).isGreaterThan(50L);
                      assertThat(doubleValue).isLessThan(4.0);
                      assertThat(floatValue).isNotEqualTo(3.0f);
                  }
              }
              """
          )
        );
    }

    @Test
    void booleanAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      boolean flag = true;
                      Boolean boxedFlag = Boolean.FALSE;

                      assertThat(flag).isTrue();
                      assertThat(boxedFlag).isFalse();
                      assertThat(flag).isEqualTo(true);
                      assertThat(boxedFlag).isNotNull();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      boolean flag = true;
                      Boolean boxedFlag = Boolean.FALSE;

                      assertThat(flag).isTrue();
                      assertThat(boxedFlag).isFalse();
                      assertThat(flag).isEqualTo(true);
                      assertThat(boxedFlag).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void primitiveArrayAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      int[] intArray = {1, 2, 3};
                      long[] longArray = {10L, 20L};
                      double[] doubleArray = {1.0, 2.0, 3.0};
                      boolean[] boolArray = {true, false, true};

                      assertThat(intArray).isNotNull();
                      assertThat(longArray).isNotEmpty();
                      assertThat(doubleArray).isEqualTo(new double[]{1.0, 2.0, 3.0});
                      assertThat(boolArray).isNotNull();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      int[] intArray = {1, 2, 3};
                      long[] longArray = {10L, 20L};
                      double[] doubleArray = {1.0, 2.0, 3.0};
                      boolean[] boolArray = {true, false, true};

                      assertThat(intArray).isNotNull();
                      assertThat(longArray).isNotEmpty();
                      assertThat(doubleArray).isEqualTo(new double[]{1.0, 2.0, 3.0});
                      assertThat(boolArray).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void truth8OptionalAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth8.assertThat;
              import java.util.Optional;
              import java.util.OptionalInt;
              import java.util.OptionalLong;
              import java.util.OptionalDouble;

              class Test {
                  void test() {
                      Optional<String> optional = Optional.of("value");
                      OptionalInt optInt = OptionalInt.of(42);
                      OptionalLong optLong = OptionalLong.of(100L);
                      OptionalDouble optDouble = OptionalDouble.of(3.14);

                      assertThat(optional).isPresent();
                      assertThat(optional).hasValue("value");
                      assertThat(optInt).hasValue(42);
                      assertThat(optLong).hasValue(100L);
                      assertThat(optDouble).isPresent();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.util.Optional;
              import java.util.OptionalInt;
              import java.util.OptionalLong;
              import java.util.OptionalDouble;

              class Test {
                  void test() {
                      Optional<String> optional = Optional.of("value");
                      OptionalInt optInt = OptionalInt.of(42);
                      OptionalLong optLong = OptionalLong.of(100L);
                      OptionalDouble optDouble = OptionalDouble.of(3.14);

                      assertThat(optional).isPresent();
                      assertThat(optional).contains("value");
                      assertThat(optInt).hasValue(42);
                      assertThat(optLong).hasValue(100L);
                      assertThat(optDouble).isPresent();
                  }
              }
              """
          )
        );
    }

    @Test
    void classAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;

              class Test {
                  void test() {
                      Class<?> stringClass = String.class;
                      Class<?> numberClass = Number.class;

                      assertThat(stringClass).isEqualTo(String.class);
                      assertThat(numberClass).isNotEqualTo(String.class);
                      assertThat(Integer.class.getSuperclass()).isEqualTo(Number.class);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      Class<?> stringClass = String.class;
                      Class<?> numberClass = Number.class;

                      assertThat(stringClass).isEqualTo(String.class);
                      assertThat(numberClass).isNotEqualTo(String.class);
                      assertThat(Integer.class.getSuperclass()).isEqualTo(Number.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void bigDecimalAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static com.google.common.truth.Truth.assertThat;
              import java.math.BigDecimal;

              class Test {
                  void test() {
                      BigDecimal value1 = new BigDecimal("10.00");
                      BigDecimal value2 = new BigDecimal("10.0");
                      BigDecimal value3 = new BigDecimal("20.5");

                      assertThat(value1).isEqualTo(new BigDecimal("10.00"));
                      assertThat(value1).isNotEqualTo(value3);
                      assertThat(value3).isGreaterThan(value1);
                      assertThat(value1).isLessThan(value3);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import java.math.BigDecimal;

              class Test {
                  void test() {
                      BigDecimal value1 = new BigDecimal("10.00");
                      BigDecimal value2 = new BigDecimal("10.0");
                      BigDecimal value3 = new BigDecimal("20.5");

                      assertThat(value1).isEqualTo(new BigDecimal("10.00"));
                      assertThat(value1).isNotEqualTo(value3);
                      assertThat(value3).isGreaterThan(value1);
                      assertThat(value1).isLessThan(value3);
                  }
              }
              """
          )
        );
    }

    @Test
    void addDependency_assertj_core() {
        rewriteRun(
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              """,
            """
              plugins {
                  id 'java'
              }
              dependencies {
                  testImplementation 'org.assertj:assertj-core:3.27.6'
              }
              """),
          //language=java
          srcTestJava(java(
            """
              import com.google.common.truth.Truth;

              class Test {
                  void test() {
                      Truth.assertThat(true).isTrue();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class Test {
                  void test() {
                      Assertions.assertThat(true).isTrue();
                  }
              }
              """
          )
        ));
    }
}
