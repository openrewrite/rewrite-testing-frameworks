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
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"SimplifiableAssertion", "ConstantConditions", "UnnecessaryLocalVariable"})
class AssertToAssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13", "hamcrest-3"))
          .recipe(new AssertToAssertions());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/128")
    @Test
    void dontSwitchAssertEqualsStringArguments() {
        //language=java
        rewriteRun(
          java(
            """
              class Entity {
                  String getField() {
                      return "b";
                  }
              }
              """
          ),
          java(
            """
              import static org.junit.Assert.assertEquals;

              class MyTest {
                  void foo() {
                      Entity entity = new Entity();
                      String hello = "a";
                      assertEquals(hello, entity.getField());
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  void foo() {
                      Entity entity = new Entity();
                      String hello = "a";
                      assertEquals(hello, entity.getField());
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void stringArgumentIsMethodInvocation() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.junit.Assert.assertFalse;

              public class MyTest {
                  T t = new T();
                  @Test
                  public void test() {
                      assertFalse(t.getName(), MyTest.class.isAssignableFrom(t.getClass()));
                  }

                  class T {
                      String getName() {
                          return "World";
                      }
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertFalse;

              public class MyTest {
                  T t = new T();
                  @Test
                  public void test() {
                      assertFalse(MyTest.class.isAssignableFrom(t.getClass()), t.getName());
                  }

                  class T {
                      String getName() {
                          return "World";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void lineBreakInArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import static org.junit.Assert.assertFalse;

              public class MyTest {
                  @Test
                  public void test() {
                      assertFalse("boom",
                              true);
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertFalse;

              public class MyTest {
                  @Test
                  public void test() {
                      assertFalse(true,
                              "boom");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertWithoutMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;

              class MyTest {

                  void foo() {
                      Assert.assertEquals(1, 2);
                      Assert.assertArrayEquals(new int[]{}, new int[]{});
                      Assert.assertNotEquals(1, 2);
                      Assert.assertFalse(false);
                      Assert.assertTrue(true);
                      Assert.assertEquals("foo", "foo");
                      Assert.assertNull(null);
                      Assert.fail();
                      String value1 = "value1";
                      String value2 = value1;
                      Assert.assertEquals(value1, value2);
                      String value3 = "value3";
                      Assert.assertNotEquals(value1, value3);
                      Assert.assertSame(value1, value2);
                      Assert.assertNotSame(value1, value3);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;

              class MyTest {

                  void foo() {
                      Assertions.assertEquals(1, 2);
                      Assertions.assertArrayEquals(new int[]{}, new int[]{});
                      Assertions.assertNotEquals(1, 2);
                      Assertions.assertFalse(false);
                      Assertions.assertTrue(true);
                      Assertions.assertEquals("foo", "foo");
                      Assertions.assertNull(null);
                      Assertions.fail();
                      String value1 = "value1";
                      String value2 = value1;
                      Assertions.assertEquals(value1, value2);
                      String value3 = "value3";
                      Assertions.assertNotEquals(value1, value3);
                      Assertions.assertSame(value1, value2);
                      Assertions.assertNotSame(value1, value3);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticAssertWithoutMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.Assert.*;

              class MyTest {

                  void foo() {
                      assertEquals(1, 2);
                      assertArrayEquals(new int[]{}, new int[]{});
                      assertNotEquals(1, 2);
                      assertFalse(false);
                      assertTrue(true);
                      assertEquals("foo", "foo");
                      assertNull(null);
                      fail();
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {

                  void foo() {
                      assertEquals(1, 2);
                      assertArrayEquals(new int[]{}, new int[]{});
                      assertNotEquals(1, 2);
                      assertFalse(false);
                      assertTrue(true);
                      assertEquals("foo", "foo");
                      assertNull(null);
                      fail();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertWithMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;

              class MyTest {

                  void foo() {
                      Assert.assertEquals("One is one", 1, 1);
                      Assert.assertArrayEquals("Empty is empty", new int[]{}, new int[]{});
                      Assert.assertNotEquals("one is not two", 1, 2);
                      Assert.assertFalse("false is false", false);
                      Assert.assertTrue("true is true", true);
                      Assert.assertEquals("foo is foo", "foo", "foo");
                      Assert.assertNull("null is null", null);
                      String value = null;
                      Assert.assertNull("value is null", value);
                      value = "hello";
                      Assert.assertNotNull("value is not null", value);
                      Assert.fail("fail");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;

              class MyTest {

                  void foo() {
                      Assertions.assertEquals(1, 1, "One is one");
                      Assertions.assertArrayEquals(new int[]{}, new int[]{}, "Empty is empty");
                      Assertions.assertNotEquals(1, 2, "one is not two");
                      Assertions.assertFalse(false, "false is false");
                      Assertions.assertTrue(true, "true is true");
                      Assertions.assertEquals("foo", "foo", "foo is foo");
                      Assertions.assertNull(null, "null is null");
                      String value = null;
                      Assertions.assertNull(value, "value is null");
                      value = "hello";
                      Assertions.assertNotNull(value, "value is not null");
                      Assertions.fail("fail");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/58")
    @Test
    void staticallyImportAssertions() {
        rewriteRun(
          spec -> spec
            .recipe(Environment.builder()
              .scanRuntimeClasspath("org.openrewrite.java.testing")
              .build()
              .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices")),
          //language=java
          java(
            """
              import org.junit.Assert;

              class Test {
                  void test() {
                      Assert.assertEquals("One is one", 1, 1);
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class Test {
                  void test() {
                      assertEquals(1, 1, "One is one");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/56")
    @Test
    void swapAssertTrueArgumentsWhenMessageIsBinaryExpression() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;

              class Test {
                  void test() {
                      Assert.assertTrue("one" + "one", true);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;

              class Test {
                  void test() {
                      Assertions.assertTrue(true, "one" + "one");
                  }
              }
              """
          )
        );
    }

    @Issue("#76")
    @Test
    void isJUnitAssertMethodChecksDeclaringType() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.Assert.assertNotNull;
              class MyTest {
                  Long l = 1L;
                  void testNestedPartitionStepStepReference() {
                      assertNotNull("message", l);
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertNotNull;

              class MyTest {
                  Long l = 1L;
                  void testNestedPartitionStepStepReference() {
                      assertNotNull(l, "message");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/423")
    @Test
    void assertThrows() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.Assert.assertThrows;

              class Test {
                  void test(Runnable run) {
                      assertThrows(
                              "Exception from cleanable.clean() should be rethrown",
                              IllegalStateException.class,
                              run::run
                      );
                      assertThrows(
                              "Exception from cleanable.clean() should be rethrown",
                              IllegalStateException.class,
                              run::run // do not remove
                      );
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertThrows;

              class Test {
                  void test(Runnable run) {
                      assertThrows(
                              IllegalStateException.class,
                              run::run,
                              "Exception from cleanable.clean() should be rethrown"
                      );
                      assertThrows(
                              IllegalStateException.class,
                              run::run, // do not remove
                              "Exception from cleanable.clean() should be rethrown"
                      );
                  }
              }
              """
          )
        );
    }

    @Test
    void missingTypeInfo() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import static org.junit.Assert.*;

              class MyTest {
                  void test() {
                      assertNotNull(UnknownType.unknownMethod());
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.*;

              class MyTest {
                  void test() {
                      assertNotNull(UnknownType.unknownMethod());
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesAssertStatementsWithMissingTypeInfo() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import static org.junit.Assert.assertNotNull;

              class MyTest {
                  void test() {
                      assertNotNull(UnknownType.unknownMethod());
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertNotNull;

              class MyTest {
                  void test() {
                      assertNotNull(UnknownType.unknownMethod());
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/515")
    void verifyClassExtendsAssertMethodArgumentsOrderRetained() {
        //language=java
        rewriteRun(
          java(
            """
              package foo;
              import org.junit.Assert;
              public class Verify extends Assert {
                  public static void assertContains(String expected, String actual) {
                  }
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
              import foo.Verify;
              import org.junit.Assert;
              import java.util.List;

              class A {
                  void test(String message, String expected, String actual) {
                      Verify.assertContains(expected, actual);
                      Assert.assertEquals(message, expected, actual);
                  }
              }
              """,
            """
              import foo.Verify;
              import org.junit.jupiter.api.Assertions;

              import java.util.List;

              class A {
                  void test(String message, String expected, String actual) {
                      Verify.assertContains(expected, actual);
                      Assertions.assertEquals(expected, actual, message);
                  }
              }
              """
          )
        );
    }
}
