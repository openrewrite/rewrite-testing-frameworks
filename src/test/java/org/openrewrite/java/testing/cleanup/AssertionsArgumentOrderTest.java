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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertionsArgumentOrderTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertionsArgumentOrder())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "testng-7", "junit-4"));
    }

    @DocumentExample
    @Test
    void junitAssertEqualsHavingPrimitiveArg() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  void someMethod() {
                      assertEquals(result(), "result");
                      assertEquals(result(), "result", "message");
                      assertEquals(0L, 1L);
                  }
                  String result() {
                      return "result";
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  void someMethod() {
                      assertEquals("result", result());
                      assertEquals("result", result(), "message");
                      assertEquals(0L, 1L);
                  }
                  String result() {
                      return "result";
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/636")
    void junit4AssertEqualsHavingStringArg() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.Assert.assertEquals;

              class MyTest {
                  void someMethod() {
                      assertEquals(result(), "result");
                      assertEquals("result", result());
                      assertEquals("message", result(), "result");
                      assertEquals("message", "result", result());
                  }
                  String result() {
                      return "result";
                  }
              }
              """,
            """
              import static org.junit.Assert.assertEquals;

              class MyTest {
                  void someMethod() {
                      assertEquals("result", result());
                      assertEquals("result", result());
                      assertEquals("message", "result", result());
                      assertEquals("message", "result", result());
                  }
                  String result() {
                      return "result";
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/636")
    void junit4AssertEqualsHavingPrimitiveArg() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.Assert.assertEquals;

              class MyTest {
                  void someMethod() {
                      assertEquals(0L, 1L);
                      assertEquals(1L, 0L);
                      assertEquals(longResult(), 0L);
                      assertEquals(0L, longResult());
                      assertEquals("message", 0L, longResult());
                      assertEquals("message", longResult(), 0L);
                  }
                  long longResult() {
                      return 0L;
                  }
              }
              """,
            """
              import static org.junit.Assert.assertEquals;

              class MyTest {
                  void someMethod() {
                      assertEquals(0L, 1L);
                      assertEquals(1L, 0L);
                      assertEquals(0L, longResult());
                      assertEquals(0L, longResult());
                      assertEquals("message", 0L, longResult());
                      assertEquals("message", 0L, longResult());
                  }
                  long longResult() {
                      return 0L;
                  }
              }
              """
          )
        );
    }

    @Test
    void jupiterAssertNullAndAssertNotNull() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.junit.jupiter.api.Assertions.assertNull;

              class MyTest {
                  void someMethod() {
                      assertNull(result(), "message");
                      assertNull("message", result());
                      assertNotNull(result(), "message");
                      assertNotNull("message", result());
                  }
                  String result() {
                      return "result";
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.junit.jupiter.api.Assertions.assertNull;

              class MyTest {
                  void someMethod() {
                      assertNull(result(), "message");
                      assertNull(result(), "message");
                      assertNotNull(result(), "message");
                      assertNotNull(result(), "message");
                  }
                  String result() {
                      return "result";
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/636")
    void junitAssertNullAndAssertNotNull() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.Assert.assertNotNull;
              import static org.junit.Assert.assertNull;

              class MyTest {
                  void someMethod() {
                      assertNull(result(), "message");
                      assertNull("message", result());
                      assertNotNull(result(), "message");
                      assertNotNull("message", result());
                  }
                  String result() {
                      return "result";
                  }
              }
              """,
            """
              import static org.junit.Assert.assertNotNull;
              import static org.junit.Assert.assertNull;

              class MyTest {
                  void someMethod() {
                      assertNull("message", result());
                      assertNull("message", result());
                      assertNotNull("message", result());
                      assertNotNull("message", result());
                  }
                  String result() {
                      return "result";
                  }
              }
              """
          )
        );
    }

    @Test
    void jupiterAssertSameNotSame() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertSame;
              import static org.junit.jupiter.api.Assertions.assertNotSame;

              class MyTest {
                  private static final Integer LIMIT = 0;
                  private static String MESSAGE = "";
                  void someMethod() {
                      assertSame(getCount(), LIMIT);
                      assertSame(getCount(), MyTest.LIMIT);
                      assertSame(LIMIT, getCount());

                      assertNotSame(getMsg(), MESSAGE);
                  }
                  String getMsg() {
                      return "";
                  }
                  Integer getCount() {
                      return 1;
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertSame;
              import static org.junit.jupiter.api.Assertions.assertNotSame;

              class MyTest {
                  private static final Integer LIMIT = 0;
                  private static String MESSAGE = "";
                  void someMethod() {
                      assertSame(LIMIT, getCount());
                      assertSame(MyTest.LIMIT, getCount());
                      assertSame(LIMIT, getCount());

                      assertNotSame(getMsg(), MESSAGE);
                  }
                  String getMsg() {
                      return "";
                  }
                  Integer getCount() {
                      return 1;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/636")
    void junit4AssertSameNotSame() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.Assert.assertSame;
              import static org.junit.Assert.assertNotSame;

              class MyTest {
                  private static final Integer LIMIT = 0;
                  private static String MESSAGE = "";
                  void someMethod() {
                      assertSame(getCount(), LIMIT);
                      assertSame(getCount(), MyTest.LIMIT);
                      assertSame(LIMIT, getCount());

                      assertNotSame(getMsg(), MESSAGE);
                  }
                  String getMsg() {
                      return "";
                  }
                  Integer getCount() {
                      return 1;
                  }
              }
              """,
            """
              import static org.junit.Assert.assertSame;
              import static org.junit.Assert.assertNotSame;

              class MyTest {
                  private static final Integer LIMIT = 0;
                  private static String MESSAGE = "";
                  void someMethod() {
                      assertSame(LIMIT, getCount());
                      assertSame(MyTest.LIMIT, getCount());
                      assertSame(LIMIT, getCount());

                      assertNotSame(getMsg(), MESSAGE);
                  }
                  String getMsg() {
                      return "";
                  }
                  Integer getCount() {
                      return 1;
                  }
              }
              """
          )
        );
    }

    @Test
    void jupiterAssertArrayEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              class MyTest {
                  void someMethod() {
                      assertArrayEquals(result(), new String[]{""});
                      assertArrayEquals(result(), new String[]{""}, "message");
                  }
                  String[] result() {
                      return null;
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              class MyTest {
                  void someMethod() {
                      assertArrayEquals(new String[]{""}, result());
                      assertArrayEquals(new String[]{""}, result(), "message");
                  }
                  String[] result() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/636")
    void junitAssertArrayEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.Assert.assertArrayEquals;

              class MyTest {
                  void someMethod() {
                      assertArrayEquals(result(), new String[]{""});
                      assertArrayEquals(new String[]{""}, result());
                      assertArrayEquals("message", new String[]{""}, result());
                      assertArrayEquals("message", result(), new String[]{""});
                  }
                  String[] result() {
                      return null;
                  }
              }
              """,
            """
              import static org.junit.Assert.assertArrayEquals;

              class MyTest {
                  void someMethod() {
                      assertArrayEquals(new String[]{""}, result());
                      assertArrayEquals(new String[]{""}, result());
                      assertArrayEquals("message", new String[]{""}, result());
                      assertArrayEquals("message", new String[]{""}, result());
                  }
                  String[] result() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void junitIterableEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.ArrayList;
              import java.util.Collections;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertIterableEquals;

              class MyTest {
                  static final Iterable<Double> COSNT_LIST = new ArrayList<>();
                  void someTest() {
                      assertIterableEquals(COSNT_LIST, doubleList());
                      assertIterableEquals(doubleList(), COSNT_LIST);
                      assertIterableEquals(doubleList(), Collections.singleton(0));
                  }
                  List<Double> doubleList() {
                      return null;
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.Collections;
              import java.util.List;

              import static org.junit.jupiter.api.Assertions.assertIterableEquals;

              class MyTest {
                  static final Iterable<Double> COSNT_LIST = new ArrayList<>();
                  void someTest() {
                      assertIterableEquals(COSNT_LIST, doubleList());
                      assertIterableEquals(COSNT_LIST, doubleList());
                      assertIterableEquals(Collections.singleton(0), doubleList());
                  }
                  List<Double> doubleList() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void ngAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertEquals;

              class MyTest {
                  void someTest() {
                      assertEquals("abc", someString());
                      assertEquals("abc", someString(), "message");
                  }
                  String someString() {
                      return null;
                  }
              }
              """,
            """
              import static org.testng.Assert.assertEquals;

              class MyTest {
                  void someTest() {
                      assertEquals(someString(), "abc");
                      assertEquals(someString(), "abc", "message");
                  }
                  String someString() {
                      return null;
                  }
              }
              """
          )
        );
    }
}
