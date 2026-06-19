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

class TestNgAssertEqualsToAssertThatTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("testng"))
          .recipe(new TestNgAssertEqualsToAssertThat());
    }

    @DocumentExample
    @Test
    void collectionsUseElementWiseAssertion() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.testng.Assert.assertEquals;

              class Test {
                  void test(List<String> actual, List<String> expected) {
                      assertEquals(actual, expected);
                      assertEquals(actual, expected, "foo");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(List<String> actual, List<String> expected) {
                      assertThat(actual).containsExactlyElementsOf(expected);
                      assertThat(actual).as("foo").containsExactlyElementsOf(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void arraysUseContainsExactly() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertEquals;

              class Test {
                  void test(String[] actual, String[] expected) {
                      assertEquals(actual, expected);
                      assertEquals(actual, expected, "foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(String[] actual, String[] expected) {
                      assertThat(actual).containsExactly(expected);
                      assertThat(actual).as("foo").containsExactly(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void setsUseInAnyOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Set;

              import static org.testng.Assert.assertEquals;

              class Test {
                  void test(Set<String> actual, Set<String> expected) {
                      assertEquals(actual, expected);
                  }
              }
              """,
            """
              import java.util.Set;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Set<String> actual, Set<String> expected) {
                      assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void mapsKeepIsEqualTo() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              import static org.testng.Assert.assertEquals;

              class Test {
                  void test(Map<String, String> actual, Map<String, String> expected) {
                      assertEquals(actual, expected);
                  }
              }
              """,
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Map<String, String> actual, Map<String, String> expected) {
                      assertThat(actual).isEqualTo(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void iteratorsUseToIterable() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Iterator;

              import static org.testng.Assert.assertEquals;

              class Test {
                  void test(Iterator<String> actual, Iterator<String> expected) {
                      assertEquals(actual, expected);
                  }
              }
              """,
            """
              import java.util.Iterator;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Iterator<String> actual, Iterator<String> expected) {
                      assertThat(actual).toIterable().containsExactlyElementsOf(() -> expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void scalarsKeepIsEqualTo() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertEquals;

              class Test {
                  void test() {
                      assertEquals(1, 1);
                      assertEquals("a", "a", "foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(1).isEqualTo(1);
                      assertThat("a").as("foo").isEqualTo("a");
                  }
              }
              """
          )
        );
    }
}
