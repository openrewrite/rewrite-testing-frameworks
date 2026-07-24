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
package org.openrewrite.java.testing.testng;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TestNgAssertionToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("testng"))
          .recipe(new TestNgAssertionToAssertJ());
    }

    @DocumentExample
    @Test
    void assertEqualsAllOverloads() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.Assertion;

              class Test {
                  void test(Assertion assertion) {
                      assertion.assertEquals("a", "b");
                      assertion.assertEquals("a", "b", "msg");
                      assertion.assertEquals(1.0, 2.0, 0.1);
                      assertion.assertEquals(1.0, 2.0, 0.1, "msg");
                      assertion.assertNotEquals(3, 4);
                      assertion.assertNotEquals(1.0, 2.0, 0.1);
                  }
              }
              """,
            """
              import org.testng.asserts.Assertion;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              class Test {
                  void test(Assertion assertion) {
                      assertThat("a").isEqualTo("b");
                      assertThat("a").as("msg").isEqualTo("b");
                      assertThat(1.0).isCloseTo(2.0, within(0.1));
                      assertThat(1.0).as("msg").isCloseTo(2.0, within(0.1));
                      assertThat(3).isNotEqualTo(4);
                      assertThat(1.0).isNotCloseTo(2.0, within(0.1));
                  }
              }
              """
          )
        );
    }

    @Test
    void booleanNullSameNoOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.Assertion;

              class Test {
                  void test(Assertion assertion, boolean c, Object a, Object b, Object[] xs, Object[] ys) {
                      assertion.assertTrue(c);
                      assertion.assertFalse(c, "msg");
                      assertion.assertNull(a);
                      assertion.assertNotNull(b, "msg");
                      assertion.assertSame(a, b);
                      assertion.assertNotSame(a, b, "msg");
                      assertion.assertEqualsNoOrder(xs, ys);
                      assertion.assertEqualsNoOrder(xs, ys, "msg");
                  }
              }
              """,
            """
              import org.testng.asserts.Assertion;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Assertion assertion, boolean c, Object a, Object b, Object[] xs, Object[] ys) {
                      assertThat(c).isTrue();
                      assertThat(c).as("msg").isFalse();
                      assertThat(a).isNull();
                      assertThat(b).as("msg").isNotNull();
                      assertThat(a).isSameAs(b);
                      assertThat(a).as("msg").isNotSameAs(b);
                      assertThat(xs).containsExactlyInAnyOrder(ys);
                      assertThat(xs).as("msg").containsExactlyInAnyOrder(ys);
                  }
              }
              """
          )
        );
    }

    @Test
    void failVariants() {
        rewriteRun(
          //language=java
          java(
            """
              import org.testng.asserts.Assertion;

              class Test {
                  void test(Assertion assertion) {
                      assertion.fail();
                      assertion.fail("msg");
                      assertion.fail("msg", new IllegalStateException());
                  }
              }
              """,
            """
              import org.testng.asserts.Assertion;

              import static org.assertj.core.api.Assertions.fail;

              class Test {
                  void test(Assertion assertion) {
                      fail("");
                      fail("msg");
                      fail("msg", new IllegalStateException());
                  }
              }
              """
          )
        );
    }
}
