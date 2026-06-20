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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1030")
class SimplifyAssertJInstanceOfAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifyAssertJInstanceOfAssertion());
    }

    @DocumentExample
    @Test
    void isTrueToIsInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(error instanceof RuntimeException).isTrue();
                      assertThat(error instanceof RuntimeException).isFalse();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(error).isInstanceOf(RuntimeException.class);
                      assertThat(error).isNotInstanceOf(RuntimeException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void parenthesizedInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat((error instanceof RuntimeException)).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(error).isInstanceOf(RuntimeException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void negatedInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(!(error instanceof RuntimeException)).isTrue();
                      assertThat(!(error instanceof RuntimeException)).isFalse();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(error).isNotInstanceOf(RuntimeException.class);
                      assertThat(error).isInstanceOf(RuntimeException.class);
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
                  void foo(Object error) {
                      Assertions.assertThat(error instanceof RuntimeException).isTrue();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class A {
                  void foo(Object error) {
                      Assertions.assertThat(error).isInstanceOf(RuntimeException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void arrayInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(error instanceof String[]).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(error).isInstanceOf(String[].class);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeNonInstanceOf() {
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

    @Test
    void doesNotChangePatternInstanceOf() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void foo(Object error) {
                      assertThat(error instanceof String s).isTrue();
                      String value = error instanceof String s ? s : "";
                  }
              }
              """
          )
        );
    }
}
