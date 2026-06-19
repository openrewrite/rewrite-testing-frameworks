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

class TestNgAssertListToAssertThatTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("testng"))
          .recipe(new TestNgAssertListToAssertThat());
    }

    @DocumentExample
    @Test
    void containsAndNotContainsObject() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.testng.Assert.assertListContainsObject;
              import static org.testng.Assert.assertListNotContainsObject;

              class Test {
                  void test(List<String> list, String value) {
                      assertListContainsObject(list, value, "foo");
                      assertListNotContainsObject(list, value, "bar");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(List<String> list, String value) {
                      assertThat(list).as("foo").contains(value);
                      assertThat(list).as("bar").doesNotContain(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void containsAndNotContainsPredicate() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.function.Predicate;

              import static org.testng.Assert.assertListContains;
              import static org.testng.Assert.assertListNotContains;

              class Test {
                  void test(List<String> list, Predicate<String> predicate) {
                      assertListContains(list, predicate, "foo");
                      assertListNotContains(list, predicate, "bar");
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.function.Predicate;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(List<String> list, Predicate<String> predicate) {
                      assertThat(list).as("foo").anyMatch(predicate);
                      assertThat(list).as("bar").noneMatch(predicate);
                  }
              }
              """
          )
        );
    }
}
