/*
 * Copyright 2025 the original author or authors.
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

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/868")
class SimplifyAssertJNullRelatedAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifyAssertJNullRelatedAssertion());
    }

    @DocumentExample
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
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object a) {
                      assertThat(a).isNull();
                      assertThat(a).isNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void notEqualAndIsFalseVariants() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object a) {
                      assertThat(a != null).isTrue();
                      assertThat(a == null).isFalse();
                      assertThat(a != null).isFalse();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object a) {
                      assertThat(a).isNotNull();
                      assertThat(a).isNotNull();
                      assertThat(a).isNull();
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
                  void foo(Object a) {
                      assertThat(a == null).isEqualTo(true);
                      assertThat(a == null).isEqualTo(false);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object a) {
                      assertThat(a).isNull();
                      assertThat(a).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void parenthesizedAndNegated() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object a) {
                      assertThat((a == null)).isTrue();
                      assertThat(!(a == null)).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object a) {
                      assertThat(a).isNull();
                      assertThat(a).isNotNull();
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
                  void foo(Object a) {
                      Assertions.assertThat(null == a).isTrue();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  void foo(Object a) {
                      Assertions.assertThat(a).isNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeNonNullReferenceComparison() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object a, Object b) {
                      assertThat(a == b).isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangePlainBooleanAssertion() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(boolean condition) {
                      assertThat(condition).isTrue();
                  }
              }
              """
          )
        );
    }
}
