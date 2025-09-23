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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertJDoubleRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertJDoubleRulesRecipes());
    }

    @DocumentExample
    @Test
    void isZero() {
        rewriteRun(
              //language=java
              java(
                    """
                      import org.assertj.core.api.Assertions;

                      class A {
                          public void test(double d) {
                              Assertions.assertThat(d).isEqualTo(0);
                              Assertions.assertThat(d).isEqualTo(0.0);
                              Assertions.assertThat(d).isEqualTo(0d);
                          }
                      }
                      """,
                    """
                      import org.assertj.core.api.Assertions;

                      class A {
                          public void test(double d) {
                              Assertions.assertThat(d).isZero();
                              Assertions.assertThat(d).isZero();
                              Assertions.assertThat(d).isZero();
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
                          public void test(double d) {
                              Assertions.assertThat(d).isNotEqualTo(0);
                              Assertions.assertThat(d).isNotEqualTo(0.0);
                              Assertions.assertThat(d).isNotEqualTo(0d);
                          }
                      }
                      """,
                    """
                      import org.assertj.core.api.Assertions;

                      class A {
                          public void test(double d) {
                              Assertions.assertThat(d).isNotZero();
                              Assertions.assertThat(d).isNotZero();
                              Assertions.assertThat(d).isNotZero();
                          }
                      }
                      """
              )
        );
    }

    @Test
    void isCloseTo() {
        rewriteRun(
          //language=java
          java(
            """
              import org.assertj.core.api.Assertions;

              import static org.assertj.core.data.Offset.offset;

              class A {
                  public void test(double d, double expected) {
                      Assertions.assertThat(d).isEqualTo(expected, offset(0.0));
                      Assertions.assertThat(d).isEqualTo(expected, offset(0d));
                      Assertions.assertThat(d).isEqualTo(expected, offset(1.0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import static org.assertj.core.data.Offset.offset;

              class A {
                  public void test(double d, double expected) {
                      Assertions.assertThat(d).isEqualTo(expected);
                      Assertions.assertThat(d).isEqualTo(expected);
                      Assertions.assertThat(d).isCloseTo(expected, offset(1.0));
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

              import static org.assertj.core.data.Offset.offset;
              import static org.assertj.core.data.Percentage.withPercentage;

              class A {
                  public void test(double d, double expected) {
                      Assertions.assertThat(d).isCloseTo(expected, offset(0.0));
                      Assertions.assertThat(d).isCloseTo(expected, offset(0d));
                      Assertions.assertThat(d).isCloseTo(expected, withPercentage(0));
                      Assertions.assertThat(d).isCloseTo(expected, withPercentage(0.0));
                      Assertions.assertThat(d).isCloseTo(expected, withPercentage(0d));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(double d, double expected) {
                      Assertions.assertThat(d).isEqualTo(expected);
                      Assertions.assertThat(d).isEqualTo(expected);
                      Assertions.assertThat(d).isEqualTo(expected);
                      Assertions.assertThat(d).isEqualTo(expected);
                      Assertions.assertThat(d).isEqualTo(expected);
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
                  public void test(double d, double expected) {
                      Assertions.assertThat(d).isNotCloseTo(expected, offset(0.0));
                      Assertions.assertThat(d).isNotCloseTo(expected, offset(0d));
                      Assertions.assertThat(d).isNotCloseTo(expected, withPercentage(0));
                      Assertions.assertThat(d).isNotCloseTo(expected, withPercentage(0.0));
                      Assertions.assertThat(d).isNotCloseTo(expected, withPercentage(0d));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(double d, double expected) {
                      Assertions.assertThat(d).isNotEqualTo(expected);
                      Assertions.assertThat(d).isNotEqualTo(expected);
                      Assertions.assertThat(d).isNotEqualTo(expected);
                      Assertions.assertThat(d).isNotEqualTo(expected);
                      Assertions.assertThat(d).isNotEqualTo(expected);
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
                  public void test(double d) {
                      Assertions.assertThat(d).isEqualTo(1);
                      Assertions.assertThat(d).isEqualTo(1.0);
                      Assertions.assertThat(d).isEqualTo(1d);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(double d) {
                      Assertions.assertThat(d).isOne();
                      Assertions.assertThat(d).isOne();
                      Assertions.assertThat(d).isOne();
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
                  public void test(double d, double expected) {
                      Assertions.assertThat(d).isOne();
                      Assertions.assertThat(d).isEqualTo(expected);
                      Assertions.assertThat(d).isNotEqualTo(expected);
                      Assertions.assertThat(d).isCloseTo(expected, offset(1.0));
                      Assertions.assertThat(d).isCloseTo(expected, withPercentage(2.0));
                  }
              }
              """
          )
        );
    }
}
