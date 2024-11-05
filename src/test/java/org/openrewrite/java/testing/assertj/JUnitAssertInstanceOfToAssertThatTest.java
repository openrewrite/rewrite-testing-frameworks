/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JUnitAssertInstanceOfToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new JUnitAssertInstanceOfToAssertThat());
    }

    @Test
    @DocumentExample
    void convertsIsInstanceOf() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class Test {
                  void test() {
                      assertInstanceOf(Integer.class, 4);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(4).isInstanceOf(Integer.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsIsInstanceOfWithMessage() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class Test {
                  void test() {
                      assertInstanceOf(Integer.class, 4, "error message");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(4).as("error message").isInstanceOf(Integer.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsIsInstanceOfWithMessageLambda() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class Test {
                  void test() {
                      assertInstanceOf(Integer.class, 4, () -> "error message");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(4).as(() -> "error message").isInstanceOf(Integer.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsIsInstanceOfWithMessageMethodReference() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class Test {
                  void test() {
                      assertInstanceOf(Integer.class, 4, this::getErrorMessage);
                  }

                  String getErrorMessage() {
                      return "error message";
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(4).as(this::getErrorMessage).isInstanceOf(Integer.class);
                  }

                  String getErrorMessage() {
                      return "error message";
                  }
              }
              """
          )
        );
    }

    @Test
    void canBeRerun() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3-*")),
          // language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(4).isInstanceOf(Integer.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotConvertAnyOtherMethods() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              import static org.junit.jupiter.api.Assertions.assertTrue;

              class Test {
                  void test() {
                      assertInstanceOf(Integer.class, 4);
                      assertTrue(1 == 1, "Message");
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.junit.jupiter.api.Assertions.assertTrue;

              class Test {
                  void test() {
                      assertThat(4).isInstanceOf(Integer.class);
                      assertTrue(1 == 1, "Message");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesConvertNestedMethodInvocations() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;
              import static org.junit.jupiter.api.Assertions.assertAll;

              class Test {
                  void test() {
                      assertAll(() -> assertInstanceOf(Integer.class, 4));
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.junit.jupiter.api.Assertions.assertAll;

              class Test {
                  void test() {
                      assertAll(() -> assertThat(4).isInstanceOf(Integer.class));
                  }
              }
              """
          )
        );
    }
}
