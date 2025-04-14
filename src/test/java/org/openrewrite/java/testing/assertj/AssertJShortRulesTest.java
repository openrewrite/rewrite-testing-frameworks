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

class AssertJShortRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AssertJShortRulesRecipes());
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
                  public void test(short s, short compare) {
                      Assertions.assertThat(s).isCloseTo(compare, Offset.offset((short)0));
                      Assertions.assertThat(s).isCloseTo(compare, Percentage.withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;

              class A {
                  public void test(short s, short compare) {
                      Assertions.assertThat(s).isEqualTo(compare);
                      Assertions.assertThat(s).isEqualTo(compare);
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
                  public void test(short s, short compare) {
                      Assertions.assertThat(s).isNotCloseTo(compare, Offset.offset((short)0));
                      Assertions.assertThat(s).isNotCloseTo(compare, Percentage.withPercentage(0));
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;
              import org.assertj.core.data.Offset;
              import org.assertj.core.data.Percentage;

              class A {
                  public void test(short s, short compare) {
                      Assertions.assertThat(s).isNotEqualTo(compare);
                      Assertions.assertThat(s).isNotEqualTo(compare);
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
                  public void test(short s) {
                      Assertions.assertThat(s).isEqualTo((short)0);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(short s) {
                      Assertions.assertThat(s).isZero();
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
                  public void test(short s) {
                      Assertions.assertThat(s).isNotEqualTo((short)0);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(short s) {
                      Assertions.assertThat(s).isNotZero();
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
                  public void test(short s) {
                      Assertions.assertThat(s).isEqualTo((short)1);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  public void test(short s) {
                      Assertions.assertThat(s).isOne();
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
                  public void test(short s, short compare) {
                      Assertions.assertThat(s).isOne();
                      Assertions.assertThat(s).isEqualTo(compare);
                      Assertions.assertThat(s).isNotEqualTo(compare);
                      Assertions.assertThat(s).isCloseTo(compare, Offset.offset((short)1));
                      Assertions.assertThat(s).isCloseTo(compare, Percentage.withPercentage(2));
                  }
              }
              """
          )
        );
    }
}
