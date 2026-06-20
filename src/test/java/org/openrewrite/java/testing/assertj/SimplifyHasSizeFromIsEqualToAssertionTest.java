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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyHasSizeFromIsEqualToAssertionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifyHasSizeFromIsEqualToAssertion());
    }

    @DocumentExample
    @Test
    void iterableSize() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> predecessors) {
                      assertThat(4).isEqualTo(predecessors.size());
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> predecessors) {
                      assertThat(predecessors).hasSize(4);
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedIterableSize() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(MyGraph g) {
                      assertThat(1).isEqualTo(g.getIndependentNodes().size());
                  }
                  interface MyGraph {
                      List<String> getIndependentNodes();
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(MyGraph g) {
                      assertThat(g.getIndependentNodes()).hasSize(1);
                  }
                  interface MyGraph {
                      List<String> getIndependentNodes();
                  }
              }
              """
          )
        );
    }

    @Test
    void stringLength() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(String s) {
                      assertThat(2).isEqualTo(s.length());
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(String s) {
                      assertThat(s).hasSize(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void mapSize() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(Map<String, String> map) {
                      assertThat(3).isEqualTo(map.size());
                  }
              }
              """,
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(Map<String, String> map) {
                      assertThat(map).hasSize(3);
                  }
              }
              """
          )
        );
    }

    @Test
    void arrayLength() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(Integer[] arr) {
                      assertThat(2).isEqualTo(arr.length);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(Integer[] arr) {
                      assertThat(arr).hasSize(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void zeroLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(0).isEqualTo(list.size());
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(list).hasSize(0);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeAlreadyDedicatedForm() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(list).hasSize(4);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeObjectEquality() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat("1").isEqualTo(list.get(0));
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeNonSizeMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(String s) {
                      assertThat(2).isEqualTo(s.indexOf('a'));
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotChangeNegativeLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(-1).isEqualTo(list.size());
                  }
              }
              """
          )
        );
    }
}
