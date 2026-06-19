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

class TestNgAssertEqualsDeepToAssertThatTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("testng"))
          .recipe(new TestNgAssertEqualsDeepToAssertThat());
    }

    @DocumentExample
    @Test
    void assertEqualsDeep() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              import static org.testng.Assert.assertEqualsDeep;

              class Test {
                  void test(Map<String, Object> actual, Map<String, Object> expected) {
                      assertEqualsDeep(actual, expected);
                      assertEqualsDeep(actual, expected, "foo");
                  }
              }
              """,
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Map<String, Object> actual, Map<String, Object> expected) {
                      assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
                      assertThat(actual).as("foo").usingRecursiveComparison().isEqualTo(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void assertNotEqualsDeep() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Set;

              import static org.testng.Assert.assertNotEqualsDeep;

              class Test {
                  void test(Set<Object> actual, Set<Object> expected) {
                      assertNotEqualsDeep(actual, expected);
                  }
              }
              """,
            """
              import java.util.Set;

              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test(Set<Object> actual, Set<Object> expected) {
                      assertThat(actual).usingRecursiveComparison().isNotEqualTo(expected);
                  }
              }
              """
          )
        );
    }
}
