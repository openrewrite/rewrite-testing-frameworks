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
package org.openrewrite.java.testing.testng;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TestNgToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("testng"))
          .recipeFromResources("org.openrewrite.java.testing.assertj.Assertj");
    }

    /// Verify that we can throw new Exception from the JavaTemplate in the generated recipe.
    @DocumentExample
    @Test
    void failWithMessage() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.fail;

              class Test {
                  void test() {
                      fail("foo");
                      fail("foo", new IllegalStateException());
                      fail();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.fail;

              class Test {
                  void test() {
                      fail("foo");
                      fail("foo", new IllegalStateException());
                      fail();
                  }
              }
              """
          )
        );
    }

    /// Verify some assertions as implemented through Refaster rules converted to Recipes. No need to test all variants.
    @Test
    void assertTrueFalse() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertFalse;
              import static org.testng.Assert.assertTrue;

              class Test {
                  void test() {
                      assertTrue(true);
                      assertTrue(true, "foo");
                      assertFalse(false);
                      assertFalse(false, "foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(true).isTrue();
                      assertThat(true).withFailMessage("foo").isTrue();
                      assertThat(false).isFalse();
                      assertThat(false).withFailMessage("foo").isFalse();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertNullAndNotNull() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertNotNull;
              import static org.testng.Assert.assertNull;

              class Test {
                  void aaa(Object obj) {
                      assertNull(obj);
                      assertNull(obj, "foo");
                  }
                  void bbb(Object obj) {
                      assertNotNull(obj);
                      assertNotNull(obj, "foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void aaa(Object obj) {
                      assertThat(obj).isNull();
                      assertThat(obj).withFailMessage("foo").isNull();
                  }
                  void bbb(Object obj) {
                      assertThat(obj).isNotNull();
                      assertThat(obj).withFailMessage("foo").isNotNull();
                  }
              }
              """
          )
        );
    }

    /// Qualified `Assert.assertX(...)` calls should become static-imported `assertThat(...)`, rather than
    /// mirroring the source qualification as `Assertions.assertThat(...)`; see
    /// <a href="https://github.com/openrewrite/rewrite-testing-frameworks/issues/1029">issue 1029</a>.
    @Test
    void qualifiedAssertBecomesStaticImport() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.testng.TestNgToAssertj"),
          //language=java
          java(
            """
              import org.testng.Assert;

              class Test {
                  void test(Object value, Object actual, Object expected, boolean flag) {
                      Assert.assertNotNull(value);
                      Assert.assertEquals(actual, expected);
                      Assert.assertTrue(flag, "should be true");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Object value, Object actual, Object expected, boolean flag) {
                      assertThat(value).isNotNull();
                      assertThat(actual).isEqualTo(expected);
                      assertThat(flag).withFailMessage("should be true").isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertEqualsAndNotEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertEquals;
              import static org.testng.Assert.assertNotEquals;

              class Test {
                  void aaa(Object obj) {
                      assertEquals(1, 1);
                      assertEquals(1, 1, "foo");
                      assertNotEquals(1, 2);
                      assertNotEquals(1, 2, "foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void aaa(Object obj) {
                      assertThat(1).isEqualTo(1);
                      assertThat(1).withFailMessage("foo").isEqualTo(1);
                      assertThat(1).isNotEqualTo(2);
                      assertThat(1).withFailMessage("foo").isNotEqualTo(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertFullFlow() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test() {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertEquals("actual", "expected");
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  void test() {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat("actual").isEqualTo("expected");
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertBooleanAndMessage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test(boolean cond) {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertTrue(cond);
                      softAssert.assertFalse(cond, "foo");
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  void test(boolean cond) {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(cond).isTrue();
                      softAssert.assertThat(cond).as("foo").isFalse();
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertNullSameAndNotEquals() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test(Object a, Object b) {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertNull(a);
                      softAssert.assertNotNull(b, "foo");
                      softAssert.assertSame(a, b);
                      softAssert.assertNotSame(a, b, "foo");
                      softAssert.assertNotEquals(a, b);
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  void test(Object a, Object b) {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(a).isNull();
                      softAssert.assertThat(b).as("foo").isNotNull();
                      softAssert.assertThat(a).isSameAs(b);
                      softAssert.assertThat(a).as("foo").isNotSameAs(b);
                      softAssert.assertThat(a).isNotEqualTo(b);
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertFloatingPointDelta() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test() {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertEquals(1.0, 2.0, 0.1);
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              import static org.assertj.core.api.Assertions.within;

              class Test {
                  void test() {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(1.0).isCloseTo(2.0, within(0.1));
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertEqualsAndNotEqualsAllOverloads() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test() {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertEquals(3, 3, "foo");
                      softAssert.assertEquals(1.0, 2.0, 0.1, "foo");
                      softAssert.assertNotEquals(3, 4, "foo");
                      softAssert.assertNotEquals(1.0, 2.0, 0.1);
                      softAssert.assertNotEquals(1.0, 2.0, 0.1, "foo");
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              import static org.assertj.core.api.Assertions.within;

              class Test {
                  void test() {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(3).as("foo").isEqualTo(3);
                      softAssert.assertThat(1.0).as("foo").isCloseTo(2.0, within(0.1));
                      softAssert.assertThat(3).as("foo").isNotEqualTo(4);
                      softAssert.assertThat(1.0).isNotCloseTo(2.0, within(0.1));
                      softAssert.assertThat(1.0).as("foo").isNotCloseTo(2.0, within(0.1));
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertBooleanOverloadSymmetry() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test(boolean cond) {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertTrue(cond, "foo");
                      softAssert.assertFalse(cond);
                      softAssert.assertNull(new Object(), "foo");
                      softAssert.assertNotNull(new Object());
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  void test(boolean cond) {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(cond).as("foo").isTrue();
                      softAssert.assertThat(cond).isFalse();
                      softAssert.assertThat(new Object()).as("foo").isNull();
                      softAssert.assertThat(new Object()).isNotNull();
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertEqualsNoOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test(Object[] actual, Object[] expected) {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertEqualsNoOrder(actual, expected);
                      softAssert.assertEqualsNoOrder(actual, expected, "foo");
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  void test(Object[] actual, Object[] expected) {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(actual).containsExactlyInAnyOrder(expected);
                      softAssert.assertThat(actual).as("foo").containsExactlyInAnyOrder(expected);
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertAllWithMessageDropsMessage() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test() {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertTrue(true);
                      softAssert.assertAll("some collected failures");
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  void test() {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(true).isTrue();
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertFail() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test() {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.fail();
                      softAssert.fail("foo");
                      softAssert.fail("foo", new IllegalStateException());
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  void test() {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.fail();
                      softAssert.fail("foo");
                      softAssert.fail("foo", new IllegalStateException());
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertNonVariableReceivers() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  private final SoftAssert field = new SoftAssert();

                  void inlineReceiver() {
                      new SoftAssert().assertTrue(true);
                  }

                  void fieldReceiver() {
                      field.assertEquals("actual", "expected");
                      field.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              class Test {
                  private final SoftAssertions field = new SoftAssertions();

                  void inlineReceiver() {
                      new SoftAssertions().assertThat(true).isTrue();
                  }

                  void fieldReceiver() {
                      field.assertThat("actual").isEqualTo("expected");
                      field.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void softAssertBoxedFloatingPointDelta() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.SoftAssert;

              class Test {
                  void test(Double actual, Double expected, Double delta) {
                      SoftAssert softAssert = new SoftAssert();
                      softAssert.assertEquals(actual, expected, delta);
                      softAssert.assertAll();
                  }
              }
              """,
            """
              import org.assertj.core.api.SoftAssertions;

              import static org.assertj.core.api.Assertions.within;

              class Test {
                  void test(Double actual, Double expected, Double delta) {
                      SoftAssertions softAssert = new SoftAssertions();
                      softAssert.assertThat(actual).isCloseTo(expected, within(delta));
                      softAssert.assertAll();
                  }
              }
              """
          )
        );
    }

    @Test
    void assertionInstanceMigratedViaAggregate() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.Assertion;

              class Test {
                  void test() {
                      Assertion assertion = new Assertion();
                      assertion.assertEquals("actual", "expected");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat("actual").isEqualTo("expected");
                  }
              }
              """
          )
        );
    }

    @Test
    void customAssertionSubclassIsNotChanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.Assertion;

              class MyAssertion extends Assertion {
              }
              """
          ),
          //language=java
          java(
            """
              class Test {
                  void test(MyAssertion assertion) {
                      assertion.assertEquals("a", "b");
                  }
              }
              """
          )
        );
    }
}
