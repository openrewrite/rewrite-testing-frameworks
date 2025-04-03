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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyHasSizeAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifyHasSizeAssertion());
    }

    @DocumentExample
    @Test
    void stringHasSameSizeAs() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      String a = "ab";
                      String b = "ab";

                      assertThat(a).hasSize(b.length());
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      String a = "ab";
                      String b = "ab";

                      assertThat(a).hasSameSizeAs(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void stringCompare() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      String a = "ab";
                      String b = "ab";

                      assertThat(a).hasSize(b.compareTo("foo"));
                  }
              }
              """
          )
        );
    }

    @Test
    void iterableHasSameSizeAs() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> a = List.of("a", "b");
                      List<String> b = List.of("a", "b");

                      assertThat(a).hasSize(b.size());
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> a = List.of("a", "b");
                      List<String> b = List.of("a", "b");

                      assertThat(a).hasSameSizeAs(b);
                  }
              }
              """
          )
        );
    }

    @Test
    void mapHasSameSizeAs() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      Map<String, String> mapA = Map.of();
                      Map<String, String> mapB = Map.of();

                      assertThat(mapA).hasSize(mapB.size());
                  }
              }
              """,
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      Map<String, String> mapA = Map.of();
                      Map<String, String> mapB = Map.of();

                      assertThat(mapA).hasSameSizeAs(mapB);
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedHasSameSizeAs() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      Map<String, String> mapA = Map.of();
                      Map<String, String> mapB = Map.of();

                      assertThat(mapA.entrySet()).hasSize(mapB.entrySet().size());
                  }
              }
              """,
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      Map<String, String> mapA = Map.of();
                      Map<String, String> mapB = Map.of();

                      assertThat(mapA.entrySet()).hasSameSizeAs(mapB.entrySet());
                  }
              }
              """
          )
        );
    }

    @Test
    void arrayHasSameSizeAs() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      Integer[] arrA = {1, 2};
                      Integer[] arrB = {1, 2};

                      assertThat(arrA).hasSize(arrB.length);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      Integer[] arrA = {1, 2};
                      Integer[] arrB = {1, 2};

                      assertThat(arrA).hasSameSizeAs(arrB);
                  }
              }
              """
          )
        );
    }
}
