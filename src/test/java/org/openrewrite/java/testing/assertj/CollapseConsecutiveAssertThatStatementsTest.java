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
import org.openrewrite.test.SourceSpec;

import static org.openrewrite.java.Assertions.java;

class CollapseConsecutiveAssertThatStatementsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new CollapseConsecutiveAssertThatStatements());
    }

    @DocumentExample
    @Test
    void collapseIfConsecutiveAssertThatPresent() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      assertThat(listA).isNotNull();
                      assertThat(listA).hasSize(3);
                      assertThat(listA).containsExactly("a", "b", "c");
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      assertThat(listA)
                              .isNotNull()
                              .hasSize(3)
                              .containsExactly("a", "b", "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void collapseIfMultipleConsecutiveAssertThatPresent() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      // Comment nor whitespace below duplicated
                      assertThat(listA).isNotNull();
                      assertThat(listA).hasSize(3);
                      assertThat(listA).containsExactly("a", "b", "c");

                      List<String> listB = Arrays.asList("a", "b", "c");

                      assertThat(listB).isNotNull();
                      assertThat(listB).hasSize(3);
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      // Comment nor whitespace below duplicated
                      assertThat(listA)
                              .isNotNull()
                              .hasSize(3)
                              .containsExactly("a", "b", "c");

                      List<String> listB = Arrays.asList("a", "b", "c");

                      assertThat(listB)
                              .isNotNull()
                              .hasSize(3);
                  }
              }
              """
          )
        );
    }

    @Test
    void collapseIfMultipleConsecutiveAssertThatPresent2() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest2 {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      assertThat(listA).isNotNull();
                      assertThat(listA).hasSize(3);
                      List<String> listB = Arrays.asList("a", "b", "c");
                      assertThat(listA).containsExactly("a", "b", "c");
                      assertThat(listB).isNotNull();
                      assertThat(listB).hasSize(3);
                  }
              }
              """,
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest2 {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      assertThat(listA)
                              .isNotNull()
                              .hasSize(3);
                      List<String> listB = Arrays.asList("a", "b", "c");
                      assertThat(listA).containsExactly("a", "b", "c");
                      assertThat(listB)
                              .isNotNull()
                              .hasSize(3);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/605")
    @Test
    void collapseAssertThatsOnInteger() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Integer i) {
                      assertThat(i).isNotNull();
                      assertThat(i).isEqualTo(2);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(Integer i) {
                      assertThat(i)
                              .isNotNull()
                              .isEqualTo(2);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/605")
    @Test
    void collapseAssertThatsOnOtherPrimitiveTypes() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      String s = "hello";
                      assertThat(s).isNotNull();
                      assertThat(s).isEqualTo("hello");
                      assertThat(s).hasSize(5);

                      Long l = 100L;
                      assertThat(l).isNotNull();
                      assertThat(l).isGreaterThan(50L);

                      Boolean b = true;
                      assertThat(b).isNotNull();
                      assertThat(b).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      String s = "hello";
                      assertThat(s)
                              .isNotNull()
                              .isEqualTo("hello")
                              .hasSize(5);

                      Long l = 100L;
                      assertThat(l)
                              .isNotNull()
                              .isGreaterThan(50L);

                      Boolean b = true;
                      assertThat(b)
                              .isNotNull()
                              .isTrue();
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreIfAssertThatOnDifferentVariables() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      List<String> listB = Arrays.asList("a", "b", "c");
                      assertThat(listA).isNotNull();
                      assertThat(listB).containsExactly("a", "b", "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreIfAssertThatOnMethodInvocation() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      assertThat(notification()).isNotNull();
                      assertThat(notification()).isTrue();
                  }
                  private boolean notification() {
                      return true;
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreIfAssertThatChainExists() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      assertThat(listA).containsExactly("a", "b", "c");
                      assertThat(listA)
                          .isNotNull()
                          .hasSize(3);
                      assertThat(listA).containsExactly("a", "b", "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreIfStatementPresentBetweenTwoAssertThat() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import java.util.List;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test() {
                      List<String> listA = Arrays.asList("a", "b", "c");
                      assertThat(listA).isNotNull();
                      int x=3;
                      assertThat(listA).hasSize(x);
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreIncorrectUseOfExtracting() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Node { Node parent; Node getParent() { return parent; } }

              class MyTest {
                  // Should not collapse these two, even if `extracting` is used incorrectly
                  void b(Node node) {
                      assertThat(node).extracting(Node::getParent);
                      assertThat(node).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreDifferentFieldAccess() {
        //language=java
        rewriteRun(
          java(
            """
              class ABC {
                  Object a, b, c;
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(ABC abc) {
                      assertThat(abc.a).isNotNull();
                      assertThat(abc.b).isNotNull();
                      assertThat(abc.c).isNotNull();
                      assertThat(abc.c).isNotNull();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void test(ABC abc) {
                      assertThat(abc.a).isNotNull();
                      assertThat(abc.b).isNotNull();
                      assertThat(abc.c)
                              .isNotNull()
                              .isNotNull();
                  }
              }
              """
          )
        );
    }
}
