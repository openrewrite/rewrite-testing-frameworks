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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class AssertTrueInstanceofToAssertInstanceOfTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "junit-4"))
          .recipe(new AssertTrueInstanceofToAssertInstanceOf());
    }

    @DocumentExample
    @Test
    void jUnit5() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof List);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(List.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void jUnit5WithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof Iterable, "Not instance of Iterable");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list, "Not instance of Iterable");
                  }
              }
              """
          ));
    }

    @Test
    void jUnit4() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.Assert.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof Iterable);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void jUnit4WithReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.Assert.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue("Not instance of Iterable", list instanceof Iterable);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(Iterable.class, list, "Not instance of Iterable");
                  }
              }
              """
          ));
    }

    @Test
    void jUnit4GenericInstanceOf() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.Assert.assertTrue;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertTrue(list instanceof List<?>);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.ArrayList;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void testJUnit5() {
                      List<String> list = new ArrayList<>();
                      assertInstanceOf(List.class, list);
                  }
              }
              """
          ));
    }

    @Test
    void customType() {
        //language=java
        rewriteRun(
          java(
            """
              class Foo {}
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class ATest {
                  @Test
                  void test() {
                      Object obj = new Foo();
                      assertTrue(obj instanceof Foo);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class ATest {
                  @Test
                  void test() {
                      Object obj = new Foo();
                      assertInstanceOf(Foo.class, obj);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedCallsUseJupiterOwner() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Supplier;

              class ATest {
                  static void assertInstanceOf(Class<?> type, Object value) {
                      throw new AssertionError("wrong owner");
                  }

                  static void assertInstanceOf(String message) {
                  }

                  static void assertInstanceOf(Class<?> type, Object value, String message) {
                      throw new AssertionError("wrong owner");
                  }

                  static void assertInstanceOf(Class<?> type, Object value, Supplier<String> message) {
                      throw new AssertionError("wrong owner");
                  }

                  static class Assertions {
                      static void assertInstanceOf(Class<?> type, Object value) {
                          throw new AssertionError("wrong owner");
                      }
                  }

                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof String);
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof String, "not a String");
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof String, () -> "not a String");
                      org.junit.Assert.assertTrue(value instanceof String);
                      org.junit.Assert.assertTrue("not a String", value instanceof String);
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof java.util.List<?>);
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof java.util.Map.Entry);
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof String[]);
                  }
              }
              """,
            """
              import java.util.function.Supplier;

              class ATest {
                  static void assertInstanceOf(Class<?> type, Object value) {
                      throw new AssertionError("wrong owner");
                  }

                  static void assertInstanceOf(String message) {
                  }

                  static void assertInstanceOf(Class<?> type, Object value, String message) {
                      throw new AssertionError("wrong owner");
                  }

                  static void assertInstanceOf(Class<?> type, Object value, Supplier<String> message) {
                      throw new AssertionError("wrong owner");
                  }

                  static class Assertions {
                      static void assertInstanceOf(Class<?> type, Object value) {
                          throw new AssertionError("wrong owner");
                      }
                  }

                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value, "not a String");
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value, () -> "not a String");
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value, "not a String");
                      org.junit.jupiter.api.Assertions.assertInstanceOf(java.util.List.class, value);
                      org.junit.jupiter.api.Assertions.assertInstanceOf(java.util.Map.Entry.class, value);
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String[].class, value);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedJUnit5NotCapturedByInheritedMethod() {
        //language=java
        rewriteRun(
          java(
            """
              class BaseAssert {
                  static void assertInstanceOf(Class<?> type, Object value) {
                      throw new AssertionError("wrong owner");
                  }
              }
              """
          ),
          java(
            """
              class ATest extends BaseAssert {
                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof String);
                  }
              }
              """,
            """
              class ATest extends BaseAssert {
                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedJUnit5SubtypeSelectorHidingAssertInstanceOf() {
        //language=java
        rewriteRun(
          java(
            """
              public class BaseAssert extends org.junit.jupiter.api.Assertions {
                  public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue) {
                      throw new AssertionError("wrong owner");
                  }
              }
              """
          ),
          java(
            """
              class ATest {
                  void test(Object value) {
                      BaseAssert.assertTrue(value instanceof String);
                  }
              }
              """,
            """
              class ATest {
                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedJUnit5SubtypeSelectorHidingAssertInstanceOfWithReason() {
        //language=java
        rewriteRun(
          java(
            """
              public class BaseAssert extends org.junit.jupiter.api.Assertions {
                  public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, String message) {
                      throw new AssertionError("wrong owner");
                  }
              }
              """
          ),
          java(
            """
              class ATest {
                  void test(Object value) {
                      BaseAssert.assertTrue(value instanceof String, "not a String");
                  }
              }
              """,
            """
              class ATest {
                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value, "not a String");
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedJUnit5InstanceSelector() {
        //language=java
        rewriteRun(
          java(
            """
              public class BaseAssert extends org.junit.jupiter.api.Assertions {
                  public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue) {
                      throw new AssertionError("wrong owner");
                  }
              }
              """
          ),
          java(
            """
              class ATest {
                  void test(BaseAssert base, Object value) {
                      base.assertTrue(value instanceof String);
                  }
              }
              """,
            """
              class ATest {
                  void test(BaseAssert base, Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedJUnit5SubtypeSelector() {
        //language=java
        rewriteRun(
          java(
            """
              package com.sample;

              public class MyAssertions extends org.junit.jupiter.api.Assertions {
              }
              """
          ),
          java(
            """
              package com.sample.test;

              import com.sample.MyAssertions;

              class ATest {
                  void test(Object value) {
                      MyAssertions.assertTrue(value instanceof String);
                  }
              }
              """,
            """
              package com.sample.test;

              class ATest {
                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedJUnit4SubtypeSelector() {
        //language=java
        rewriteRun(
          java(
            """
              package com.sample;

              public class MyAssert extends org.junit.Assert {
              }
              """
          ),
          java(
            """
              package com.sample.test;

              import com.sample.MyAssert;

              class ATest {
                  void test(Object value) {
                      MyAssert.assertTrue(value instanceof String);
                  }
              }
              """,
            """
              package com.sample.test;

              class ATest {
                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedJUnit4DoesNotShadowSamePackageAssertionsHelper() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              public class Assertions {
                  public static void assertThat(Object value) {
                  }
              }
              """
          ),
          java(
            """
              package com.example;

              class ATest {
                  void test(Object value) {
                      Assertions.assertThat(value);
                      org.junit.Assert.assertTrue(value instanceof String);
                  }
              }
              """,
            """
              package com.example;

              class ATest {
                  void test(Object value) {
                      Assertions.assertThat(value);
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                  }
              }
              """
          ));
    }

    @Test
    void qualifiedOutputIsStableOnASecondRun() {
        //language=java
        rewriteRun(
          java(
            """
              class Test {
                  static void assertInstanceOf(Class<?> type, Object value) {
                      throw new AssertionError("wrong owner");
                  }

                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertInstanceOf(String.class, value);
                  }
              }
              """
          ));
    }

    @Test
    void noChangeWhenAssertTrueIsNotAttributed() {
        //language=java
        rewriteRun(
          spec -> spec
            .parser(JavaParser.fromJavaVersion())
            .typeValidationOptions(TypeValidation.none()),
          java(
            """
              class Test {
                  void test(Object value) {
                      org.junit.jupiter.api.Assertions.assertTrue(value instanceof String);
                  }
              }
              """
          ));
    }
}
