/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"ExcessiveLambdaUsage", "RedundantArrayCreation"})
class JUnitAssertArrayEqualsToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new JUnitAssertArrayEqualsToAssertThat());
    }

    @Test
    void singleStaticMethodNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              public class MyTest {

                  @Test
                  public void test() {
                      Integer[] expected = new Integer[] {1, 2, 3};
                      assertArrayEquals(expected, notification());
                  }
                  private Integer[] notification() {
                      return new Integer[] {1, 2, 3};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {

                  @Test
                  public void test() {
                      Integer[] expected = new Integer[] {1, 2, 3};
                      assertThat(notification()).containsExactly(expected);
                  }
                  private Integer[] notification() {
                      return new Integer[] {1, 2, 3};
                  }
              }
              """
          )
        );
    }

    @Test
    void singleStaticMethodWithMessageLambda() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertArrayEquals(new int[]{1, 2, 3}, notification(), () -> "These arrays should be equal");
                  }
                  private int[] notification() {
                      return new int[]{1, 2, 3};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as(() -> "These arrays should be equal").containsExactly(new int[]{1, 2, 3});
                  }
                  private int[] notification() {
                      return new int[]{1, 2, 3};
                  }
              }
              """
          )
        );
    }

    @Test
    void doublesWithinNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      double eps = .2d;
                      assertArrayEquals(new double[]{1.0d, 2.0d, 3.0d}, notification(), eps);
                  }
                  private double[] notification() {
                      return new double[]{1.1d, 2.1d, 3.1d};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      double eps = .2d;
                      assertThat(notification()).containsExactly(new double[]{1.0d, 2.0d, 3.0d}, within(eps));
                  }
                  private double[] notification() {
                      return new double[]{1.1d, 2.1d, 3.1d};
                  }
              }
              """
          )
        );
    }

    @Test
    void doublesWithinAndWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertArrayEquals(new double[]{1.0d, 2.0d, 3.0d}, notification(), .2d, "These should be close");
                  }
                  private double[] notification() {
                      return new double[]{1.1d, 2.1d, 3.1d};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as("These should be close").containsExactly(new double[]{1.0d, 2.0d, 3.0d}, within(.2d));
                  }
                  private double[] notification() {
                      return new double[]{1.1d, 2.1d, 3.1d};
                  }
              }
              """
          )
        );
    }

    @Test
    void doublesObjectsWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertArrayEquals(new Double[]{1.0d, 2.0d, 3.0d}, notification(), "These arrays should be equal");
                  }
                  private Double[] notification() {
                      return new Double[]{1.0d, 2.0d, 3.0d};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as("These arrays should be equal").containsExactly(new Double[]{1.0d, 2.0d, 3.0d});
                  }
                  private Double[] notification() {
                      return new Double[]{1.0d, 2.0d, 3.0d};
                  }
              }
              """
          )
        );
    }

    @Test
    void floatCloseToWithNoMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f}, notification(), .2f);
                  }
                  private float[] notification() {
                      return new float[]{1.1f, 2.1f, 3.1f};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).containsExactly(new float[]{1.0f, 2.0f, 3.0f}, within(.2f));
                  }
                  private float[] notification() {
                      return new float[]{1.1f, 2.1f, 3.1f};
                  }
              }
              """
          )
        );
    }

    @Test
    void floatCloseToWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertArrayEquals;

              public class MyTest {
                  @Test
                  public void test() {
                      assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f}, notification(), .2f, () -> "These should be close");
                  }
                  private float[] notification() {
                      return new float[]{1.1f, 2.1f, 3.1f};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;

              public class MyTest {
                  @Test
                  public void test() {
                      assertThat(notification()).as(() -> "These should be close").containsExactly(new float[]{1.0f, 2.0f, 3.0f}, within(.2f));
                  }
                  private float[] notification() {
                      return new float[]{1.1f, 2.1f, 3.1f};
                  }
              }
              """
          )
        );
    }

    @Test
    void fullyQualifiedMethodWithMessage() {
        //language=java
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import org.junit.Test;

              public class MyTest {
                  @Test
                  public void test() {
                      String[] expected = new String[] {"Fred", "Alice", "Mary"};
                      org.junit.jupiter.api.Assertions.assertArrayEquals(expected, notification(), () -> "These should be close");
                  }
                  private String[] notification() {
                      return new String[] {"Fred", "Alice", "Mary"};
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.assertj.core.api.Assertions.assertThat;

              public class MyTest {
                  @Test
                  public void test() {
                      String[] expected = new String[] {"Fred", "Alice", "Mary"};
                      assertThat(notification()).as(() -> "These should be close").containsExactly(expected);
                  }
                  private String[] notification() {
                      return new String[] {"Fred", "Alice", "Mary"};
                  }
              }
              """
          )
        );
    }
}
