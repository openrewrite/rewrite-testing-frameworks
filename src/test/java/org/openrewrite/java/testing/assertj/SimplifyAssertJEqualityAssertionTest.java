/*
 * Copyright 2026 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class SimplifyAssertJEqualityAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifyAssertJEqualityAssertion());
    }

    @DocumentExample
    @Test
    void dispatchesOnWhatTheComparisonActuallyCompares() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Object x, Object y, long a, long b) {
                      assertThat(x == null).isTrue();
                      assertThat(a == b).isTrue();
                      assertThat(x == y).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Object x, Object y, long a, long b) {
                      assertThat(x).isNull();
                      assertThat(a).isEqualTo(b);
                      assertThat(x).isSameAs(y);
                  }
              }
              """
          )
        );
    }

    /// Every form the recipe accepts is applied uniformly to all three cases, rather than depending on the
    /// operand types; these tests pin that.
    @Nested
    class AssertionForms {

        @Test
        void isFalseInvertsTheAssertion() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x, Object y, int a, int b) {
                          assertThat(x == null).isFalse();
                          assertThat(a == b).isFalse();
                          assertThat(x == y).isFalse();
                          assertThat(x != y).isFalse();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x, Object y, int a, int b) {
                          assertThat(x).isNotNull();
                          assertThat(a).isNotEqualTo(b);
                          assertThat(x).isNotSameAs(y);
                          assertThat(x).isSameAs(y);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void parenthesesAndNegations() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x) {
                          assertThat((x == null)).isTrue();
                          assertThat(!(x == null)).isTrue();
                          assertThat(!!(x == null)).isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x) {
                          assertThat(x).isNull();
                          assertThat(x).isNotNull();
                          assertThat(x).isNull();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void isEqualToBooleanLiteral() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x) {
                          assertThat(x == null).isEqualTo(true);
                          assertThat(x == null).isEqualTo(false);
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x) {
                          assertThat(x).isNull();
                          assertThat(x).isNotNull();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void fullyQualifiedAssertThat() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.assertj.core.api.Assertions;

                  class A {
                      void test(Object x, Object y) {
                          Assertions.assertThat(null == x).isTrue();
                          Assertions.assertThat(x == y).isTrue();
                      }
                  }
                  """,
                """
                  import org.assertj.core.api.Assertions;

                  class A {
                      void test(Object x, Object y) {
                          Assertions.assertThat(x).isNull();
                          Assertions.assertThat(x).isSameAs(y);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/868")
    class NullComparisons {

        @Test
        void nullOnEitherSide() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void foo(Object a) {
                          assertThat(null == a).isTrue();
                          assertThat(a == null).isTrue();
                          assertThat(a != null).isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void foo(Object a) {
                          assertThat(a).isNull();
                          assertThat(a).isNull();
                          assertThat(a).isNotNull();
                      }
                  }
                  """
              )
            );
        }

        /// A primitive can never be `null`, so the null branch must not claim a boxed operand's comparison
        /// against the `null` literal for the value branch.
        @Test
        void boxedOperandComparedToNull() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void foo(Long a) {
                          assertThat(a == null).isTrue();
                          assertThat(null != a).isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void foo(Long a) {
                          assertThat(a).isNull();
                          assertThat(a).isNotNull();
                      }
                  }
                  """
              )
            );
        }
    }

    /// The linked TestNG pull request is the motivating case: `assertThat(a != b).isTrue()` on two `long`s had
    /// become `isNotSameAs`, which held vacuously and left the assertion verifying nothing.
    @Nested
    @Issue("https://github.com/testng-team/testng/pull/3278")
    class ValueComparisons {

        @Test
        void primitiveComparisonBecomesIsEqualTo() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(long a, int b, char c, short s, byte y, boolean p, boolean q) {
                          assertThat(a == 0).isTrue();
                          assertThat(a != b).isTrue();
                          assertThat(b == c).isTrue();
                          assertThat(s == y).isTrue();
                          assertThat(p == q).isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(long a, int b, char c, short s, byte y, boolean p, boolean q) {
                          assertThat(a).isEqualTo(0);
                          assertThat(a).isNotEqualTo(b);
                          assertThat(b).isEqualTo(c);
                          assertThat(s).isEqualTo(y);
                          assertThat(p).isEqualTo(q);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void boxedComparedToPrimitiveUnboxesToAValueComparison() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Long x, long y) {
                          assertThat(x == y).isTrue();
                          assertThat(y != x).isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Long x, long y) {
                          assertThat(x).isEqualTo(y);
                          assertThat(y).isNotEqualTo(x);
                      }
                  }
                  """
              )
            );
        }

        /// AssertJ only declares the `isEqualTo` overload matching the asserted type; anything wider falls through
        /// to `isEqualTo(Object)`, which compares a `Byte` to an `Integer` and is never equal. `isNotEqualTo` would
        /// then hold vacuously, so we leave these alone rather than assert something weaker.
        @Test
        void operandThatDoesNotLineUpWithTheAssertedTypeIsLeftAlone() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(byte y, short s, char c, int i, long l, Integer boxed) {
                          assertThat(y != 3).isTrue();
                          assertThat(s != 5).isTrue();
                          assertThat(c != i).isTrue();
                          assertThat(i != l).isTrue();
                          assertThat(l == boxed).isTrue();
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class ReferenceComparisons {

        @Test
        void referenceComparisonBecomesIsSameAs() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x, Object y, String s) {
                          assertThat(x == y).isTrue();
                          assertThat(x != y).isTrue();
                          assertThat(s == "a").isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Object x, Object y, String s) {
                          assertThat(x).isSameAs(y);
                          assertThat(x).isNotSameAs(y);
                          assertThat(s).isSameAs("a");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void boxedComparedToBoxedRemainsAReferenceComparison() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Long x, Long y) {
                          assertThat(x == y).isTrue();
                          assertThat(x != y).isTrue();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(Long x, Long y) {
                          assertThat(x).isSameAs(y);
                          assertThat(x).isNotSameAs(y);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class LeftAlone {

        @Test
        void floatingPointComparisons() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(double a, double b, Double c, Float e, Float f) {
                          assertThat(a == b).isTrue();
                          assertThat(c != b).isTrue();
                          assertThat(e == f).isTrue();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void unknownTypes() {
            rewriteRun(
              spec -> spec.typeValidationOptions(TypeValidation.none()),
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void test(com.example.Unknown x, com.example.Unknown y, long z) {
                          assertThat(x == y).isTrue();
                          assertThat(x == z).isTrue();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void plainBooleanAssertionAndOtherOperators() {
            rewriteRun(
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;

                  class A {
                      void foo(boolean condition, int a, int b) {
                          assertThat(condition).isTrue();
                          assertThat(a > b).isTrue();
                          assertThat(a == b).isEqualTo(1);
                      }
                  }
                  """
              )
            );
        }
    }
}
