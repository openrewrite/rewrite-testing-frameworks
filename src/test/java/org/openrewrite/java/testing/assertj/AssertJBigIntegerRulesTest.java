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

class AssertJBigIntegerRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertJBigIntegerRulesRecipes());
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
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger, BigInteger compare) {
                      Assertions.assertThat(bigInteger).isCloseTo(compare, Offset.offset(BigInteger.ZERO));
                      Assertions.assertThat(bigInteger).isCloseTo(compare, Percentage.withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger, BigInteger compare) {
                      Assertions.assertThat(bigInteger).isEqualTo(compare);
                      Assertions.assertThat(bigInteger).isEqualTo(compare);
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
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger, BigInteger compare) {
                      Assertions.assertThat(bigInteger).isNotCloseTo(compare, Offset.offset(BigInteger.ZERO));
                      Assertions.assertThat(bigInteger).isNotCloseTo(compare, Percentage.withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger, BigInteger compare) {
                      Assertions.assertThat(bigInteger).isNotEqualTo(compare);
                      Assertions.assertThat(bigInteger).isNotEqualTo(compare);
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
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger) {
                      Assertions.assertThat(bigInteger).isEqualTo(0);
                      Assertions.assertThat(bigInteger).isEqualTo(0L);
                      Assertions.assertThat(bigInteger).isEqualTo(BigInteger.ZERO);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger) {
                      Assertions.assertThat(bigInteger).isZero();
                      Assertions.assertThat(bigInteger).isZero();
                      Assertions.assertThat(bigInteger).isZero();
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
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger) {
                      Assertions.assertThat(bigInteger).isNotEqualTo(0);
                      Assertions.assertThat(bigInteger).isNotEqualTo(0L);
                      Assertions.assertThat(bigInteger).isNotEqualTo(BigInteger.ZERO);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger) {
                      Assertions.assertThat(bigInteger).isNotZero();
                      Assertions.assertThat(bigInteger).isNotZero();
                      Assertions.assertThat(bigInteger).isNotZero();
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
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger) {
                      Assertions.assertThat(bigInteger).isEqualTo(1);
                      Assertions.assertThat(bigInteger).isEqualTo(1L);
                      Assertions.assertThat(bigInteger).isEqualTo(BigInteger.ONE);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger) {
                      Assertions.assertThat(bigInteger).isOne();
                      Assertions.assertThat(bigInteger).isOne();
                      Assertions.assertThat(bigInteger).isOne();
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
              import java.math.BigInteger;

              class A {
                  public void test(BigInteger bigInteger, BigInteger compare) {
                      Assertions.assertThat(bigInteger).isOne();
                      Assertions.assertThat(bigInteger).isEqualTo(compare);
                      Assertions.assertThat(bigInteger).isNotEqualTo(compare);
                      Assertions.assertThat(bigInteger).isCloseTo(compare, Offset.offset(BigInteger.valueOf(1)));
                      Assertions.assertThat(bigInteger).isCloseTo(compare, Percentage.withPercentage(2));
                  }
              }
              """
          )
        );
    }
}
