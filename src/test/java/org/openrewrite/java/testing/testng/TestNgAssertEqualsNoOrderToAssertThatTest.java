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

class TestNgAssertEqualsNoOrderToAssertThatTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("testng"))
          .recipe(new TestNgAssertEqualsNoOrderToAssertThat());
    }

    @DocumentExample
    @Test
    void arraysUseContainsExactlyInAnyOrder() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.testng.Assert.assertEqualsNoOrder;

              class Test {
                  void test(Object[] actual, Object[] expected) {
                      assertEqualsNoOrder(actual, expected);
                      assertEqualsNoOrder(actual, expected, "foo");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Object[] actual, Object[] expected) {
                      assertThat(actual).containsExactlyInAnyOrder(expected);
                      assertThat(actual).as("foo").containsExactlyInAnyOrder(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void collectionsUseElementsOf() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Collection;

              import static org.testng.Assert.assertEqualsNoOrder;

              class Test {
                  void test(Collection<String> actual, Collection<String> expected) {
                      assertEqualsNoOrder(actual, expected);
                  }
              }
              """,
            """
              import java.util.Collection;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Collection<String> actual, Collection<String> expected) {
                      assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
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

              import static org.testng.Assert.assertEqualsNoOrder;

              class Test {
                  void test(Iterator<String> actual, Iterator<String> expected) {
                      assertEqualsNoOrder(actual, expected);
                  }
              }
              """,
            """
              import java.util.Iterator;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Iterator<String> actual, Iterator<String> expected) {
                      assertThat(actual).toIterable().containsExactlyInAnyOrderElementsOf(() -> expected);
                  }
              }
              """
          )
        );
    }
}
