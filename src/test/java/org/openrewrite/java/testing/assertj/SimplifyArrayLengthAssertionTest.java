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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyArrayLengthAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifyArrayLengthAssertion());
    }

    @DocumentExample
    @Test
    void hasSize() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(String[] array) {
                      assertThat(array.length).isEqualTo(7);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(String[] array) {
                      assertThat(array).hasSize(7);
                  }
              }
              """
          )
        );
    }

    @CsvSource(delimiter = '|', value = {
      "assertThat(x.length).isZero()                    | assertThat(x).isEmpty()",
      "assertThat(x.length).isEqualTo(0)                | assertThat(x).isEmpty()",
      "assertThat(x.length).isEqualTo(7)                | assertThat(x).hasSize(7)",
      "assertThat(x.length).isEqualTo(y.length)         | assertThat(x).hasSameSizeAs(y)",
      "assertThat(x.length).isLessThan(5)               | assertThat(x).hasSizeLessThan(5)",
      "assertThat(x.length).isLessThanOrEqualTo(2)      | assertThat(x).hasSizeLessThanOrEqualTo(2)",
      "assertThat(x.length).isGreaterThan(4)            | assertThat(x).hasSizeGreaterThan(4)",
      "assertThat(x.length).isGreaterThanOrEqualTo(1)   | assertThat(x).hasSizeGreaterThanOrEqualTo(1)",
    })
    @ParameterizedTest
    void simplifiesArrayLengthAssertions(String before, String after) {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(int[] x, int[] y) {
                      %s;
                  }
              }
              """.formatted(before),
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(int[] x, int[] y) {
                      %s;
                  }
              }
              """.formatted(after)
          )
        );
    }

    @Test
    void doesNotChangeCollectionSize() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(list.size()).isEqualTo(7);
                  }
              }
              """
          )
        );
    }
}
