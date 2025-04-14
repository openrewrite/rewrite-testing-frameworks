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

class AssertJFloatRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertJFloatRulesRecipes());
    }

    @Test
    void isCloseTo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;

              class A {
                  public void test(float f, float compare) {
                      Assertions.assertThat(f).isEqualTo(compare, Offset.offset(0.0f));
                      Assertions.assertThat(f).isEqualTo(compare, Offset.offset(1.0f));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;

              class A {
                  public void test(float f, float compare) {
                      Assertions.assertThat(f).isEqualTo(compare);
                      Assertions.assertThat(f).isCloseTo(compare, Offset.offset(1.0f));
                  }
              }
              """
          )
        );
    }

    @Test
    void isEqualTo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;

              class A {
                  public void test(float f, float compare) {
                      Assertions.assertThat(f).isCloseTo(compare, Offset.offset(0.0f));
                      Assertions.assertThat(f).isCloseTo(compare, Percentage.withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;

              class A {
                  public void test(float f, float compare) {
                      Assertions.assertThat(f).isEqualTo(compare);
                      Assertions.assertThat(f).isEqualTo(compare);
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
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;

              class A {
                  public void test(float f, float compare) {
                      Assertions.assertThat(f).isNotCloseTo(compare, Offset.offset(0.0f));
                      Assertions.assertThat(f).isNotCloseTo(compare, Percentage.withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;

              class A {
                  public void test(float f, float compare) {
                      Assertions.assertThat(f).isNotEqualTo(compare);
                      Assertions.assertThat(f).isNotEqualTo(compare);
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
                  public void test(float f) {
                      Assertions.assertThat(f).isEqualTo(0.0f);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(float f) {
                      Assertions.assertThat(f).isZero();
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
                  public void test(float f) {
                      Assertions.assertThat(f).isNotEqualTo(0.0f);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(float f) {
                      Assertions.assertThat(f).isNotZero();
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
                  public void test(float f) {
                      Assertions.assertThat(f).isEqualTo(1.0f);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(float f) {
                      Assertions.assertThat(f).isOne();
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
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;

              class A {
                  public void test(float f, float compare) {
                      Assertions.assertThat(f).isOne();
                      Assertions.assertThat(f).isEqualTo(compare);
                      Assertions.assertThat(f).isNotEqualTo(compare);
                      Assertions.assertThat(f).isCloseTo(compare, Offset.offset(1.0f));
                      Assertions.assertThat(f).isCloseTo(compare, Percentage.withPercentage(2.0f));
                  }
              }
              """
          )
        );
    }
}
