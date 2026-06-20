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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/387")
class DecomposeConjunctionAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new DecomposeConjunctionAssertion());
    }

    @DocumentExample
    @Test
    void decomposeConjunction() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(String name, int count) {
                      assertThat(name != null && count > 0).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(String name, int count) {
                      assertThat(name != null).isTrue();
                      assertThat(count > 0).isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void threeConjuncts() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b, boolean c) {
                      assertThat(a && b && c).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b, boolean c) {
                      assertThat(a).isTrue();
                      assertThat(b).isTrue();
                      assertThat(c).isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void nestedAndParenthesizedConjuncts() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b, boolean c) {
                      assertThat(a && (b && c)).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b, boolean c) {
                      assertThat(a).isTrue();
                      assertThat(b).isTrue();
                      assertThat(c).isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void disjunctConjunctStaysGrouped() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b, boolean c) {
                      assertThat(a && (b || c)).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b, boolean c) {
                      assertThat(a).isTrue();
                      assertThat(b || c).isTrue();
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
                  void foo(boolean a, boolean b) {
                      Assertions.assertThat(a && b).isTrue();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  void foo(boolean a, boolean b) {
                      Assertions.assertThat(a).isTrue();
                      Assertions.assertThat(b).isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotDecomposeIsFalse() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b) {
                      assertThat(a && b).isFalse();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotDecomposeWithDescription() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a, boolean b) {
                      assertThat(a && b).as("both hold").isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangePlainAssertion() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean a) {
                      assertThat(a).isTrue();
                  }
              }
              """
          )
        );
    }
}
