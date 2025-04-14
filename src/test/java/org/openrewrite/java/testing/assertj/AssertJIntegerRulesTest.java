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
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertJIntegerRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertJIntegerRulesRecipes());
    }

    @Test
    void isEqualTo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              import static org.assertj.core.data.Offset.offset;
              import static org.assertj.core.data.Percentage.withPercentage;

              class A {
                  public void test(int i, int expected) {
                      Assertions.assertThat(i).isCloseTo(expected, offset(0));
                      Assertions.assertThat(i).isCloseTo(expected, withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i, int expected) {
                      Assertions.assertThat(i).isEqualTo(expected);
                      Assertions.assertThat(i).isEqualTo(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void isNotEqualTo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              import static org.assertj.core.data.Offset.offset;
              import static org.assertj.core.data.Percentage.withPercentage;

              class A {
                  public void test(int i, int expected) {
                      Assertions.assertThat(i).isNotCloseTo(expected, offset(0));
                      Assertions.assertThat(i).isNotCloseTo(expected, withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i, int expected) {
                      Assertions.assertThat(i).isNotEqualTo(expected);
                      Assertions.assertThat(i).isNotEqualTo(expected);
                  }
              }
              """
          )
        );
    }

    @Test
    void isZero() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i) {
                      Assertions.assertThat(i).isEqualTo(0);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i) {
                      Assertions.assertThat(i).isZero();
                  }
              }
              """
          )
        );
    }

    @Test
    void isNotZero() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i) {
                      Assertions.assertThat(i).isNotEqualTo(0);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i) {
                      Assertions.assertThat(i).isNotZero();
                  }
              }
              """
          )
        );
    }

    @Test
    void isOne() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i) {
                      Assertions.assertThat(i).isEqualTo(1);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(int i) {
                      Assertions.assertThat(i).isOne();
                  }
              }
              """
          )
        );
    }

    @Test
    void unchanged() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              import static org.assertj.core.data.Offset.offset;
              import static org.assertj.core.data.Percentage.withPercentage;

              class A {
                  public void test(int i, int expected) {
                      Assertions.assertThat(i).isOne();
                      Assertions.assertThat(i).isEqualTo(expected);
                      Assertions.assertThat(i).isNotEqualTo(expected);
                      Assertions.assertThat(i).isCloseTo(expected, offset(1));
                      Assertions.assertThat(i).isCloseTo(expected, withPercentage(2));
                  }
              }
              """
          )
        );
    }
}
