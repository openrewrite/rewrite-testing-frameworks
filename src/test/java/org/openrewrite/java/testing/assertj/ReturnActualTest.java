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

class ReturnActualTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new ReturnActual());
    }

    @DocumentExample
    @Test
    void singleAssertionAndReturn() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  List<String> test(List<String> list) {
                      assertThat(list).isNotNull();
                      return list;
                  }
              }
              """,
            """
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  List<String> test(List<String> list) {
                      return assertThat(list).isNotNull().actual();
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedAssertionsAndReturn() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  List<String> test(List<String> list) {
                      assertThat(list).isNotNull().hasSize(3);
                      return list;
                  }
              }
              """,
            """
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  List<String> test(List<String> list) {
                      return assertThat(list).isNotNull().hasSize(3).actual();
                  }
              }
              """
          )
        );
    }

    @Test
    void stringTypeWithIsEqualTo() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  String test(String s) {
                      assertThat(s).isEqualTo("hello");
                      return s;
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  String test(String s) {
                      return assertThat(s).isEqualTo("hello").actual();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenDifferentVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  List<String> test(List<String> list, List<String> other) {
                      assertThat(list).isNotNull();
                      return other;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenStatementBetween() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  List<String> test(List<String> list) {
                      assertThat(list).isNotNull();
                      System.out.println("something");
                      return list;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenAssertThatArgumentIsMethodCall() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import java.util.ArrayList;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  List<String> getList() { return new ArrayList<>(); }
                  List<String> test() {
                      List<String> list = getList();
                      assertThat(getList()).isNotNull();
                      return list;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenChainChangesType() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Node { Node parent; Node getParent() { return parent; } }

              class MyTest {
                  Node test(Node node) {
                      assertThat(node).extracting(Node::getParent).isNotNull();
                      return node;
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenVoidReturn() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(List<String> list) {
                      assertThat(list).isNotNull();
                      return;
                  }
              }
              """
          )
        );
    }
}
