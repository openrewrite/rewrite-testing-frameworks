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

class AssertJByteRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertJByteRulesRecipes());
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
                  public void test(byte b, byte expected) {
                      Assertions.assertThat(b).isCloseTo(expected, offset((byte)0));
                      Assertions.assertThat(b).isCloseTo(expected, withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(byte b, byte expected) {
                      Assertions.assertThat(b).isEqualTo(expected);
                      Assertions.assertThat(b).isEqualTo(expected);
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
                  public void test(byte b, byte expected) {
                      Assertions.assertThat(b).isNotCloseTo(expected, offset((byte)0));
                      Assertions.assertThat(b).isNotCloseTo(expected, withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(byte b, byte expected) {
                      Assertions.assertThat(b).isNotEqualTo(expected);
                      Assertions.assertThat(b).isNotEqualTo(expected);
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
                  public void test(byte b) {
                      Assertions.assertThat(b).isEqualTo((byte)0);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(byte b) {
                      Assertions.assertThat(b).isZero();
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
                  public void test(byte b) {
                      Assertions.assertThat(b).isNotEqualTo((byte)0);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(byte b) {
                      Assertions.assertThat(b).isNotZero();
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
                  public void test(byte b) {
                      Assertions.assertThat(b).isEqualTo((byte)1);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(byte b) {
                      Assertions.assertThat(b).isOne();
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
                  public void test(byte b, byte expected) {
                      Assertions.assertThat(b).isOne();
                      Assertions.assertThat(b).isEqualTo(expected);
                      Assertions.assertThat(b).isNotEqualTo(expected);
                      Assertions.assertThat(b).isCloseTo(expected, offset((byte)1));
                      Assertions.assertThat(b).isCloseTo(expected, withPercentage(2));
                  }
              }
              """
          )
        );
    }
}
