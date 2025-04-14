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

class AssertJLongRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertJLongRulesRecipes());
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
                  public void test(long l, long compare) {
                      Assertions.assertThat(l).isCloseTo(compare, offset(0L));
                      Assertions.assertThat(l).isCloseTo(compare, withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(long l, long compare) {
                      Assertions.assertThat(l).isEqualTo(compare);
                      Assertions.assertThat(l).isEqualTo(compare);
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
                  public void test(long l, long compare) {
                      Assertions.assertThat(l).isNotCloseTo(compare, offset(0L));
                      Assertions.assertThat(l).isNotCloseTo(compare, withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(long l, long compare) {
                      Assertions.assertThat(l).isNotEqualTo(compare);
                      Assertions.assertThat(l).isNotEqualTo(compare);
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
                  public void test(long l) {
                      Assertions.assertThat(l).isEqualTo(0L);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(long l) {
                      Assertions.assertThat(l).isZero();
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
                  public void test(long l) {
                      Assertions.assertThat(l).isNotEqualTo(0L);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(long l) {
                      Assertions.assertThat(l).isNotZero();
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
                  public void test(long l) {
                      Assertions.assertThat(l).isEqualTo(1L);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(long l) {
                      Assertions.assertThat(l).isOne();
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
                  public void test(long l, long compare) {
                      Assertions.assertThat(l).isOne();
                      Assertions.assertThat(l).isEqualTo(compare);
                      Assertions.assertThat(l).isNotEqualTo(compare);
                      Assertions.assertThat(l).isCloseTo(compare, offset(1L));
                      Assertions.assertThat(l).isCloseTo(compare, withPercentage(2));
                  }
              }
              """
          )
        );
    }
}
