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

class SimplifySequencedCollectionAssertionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new SimplifySequencedCollectionAssertions());
    }

    @DocumentExample
    @Test
    void getLast() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> list = List.of("a", "b", "c");
                      assertThat(list.getLast()).isEqualTo("c");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> list = List.of("a", "b", "c");
                      assertThat(list).last().isEqualTo("c");
                  }
              }
              """
          )
        );
    }

    @Test
    void getFirst() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> list = List.of("a", "b", "c");
                      assertThat(list.getFirst()).isEqualTo("a");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> list = List.of("a", "b", "c");
                      assertThat(list).first().isEqualTo("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void withDifferentAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> list = List.of("a", "b", "c");
                      assertThat(list.getLast()).isNotNull();
                      assertThat(list.getFirst()).isNotEmpty();
                      assertThat(list.getLast()).contains("c");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      List<String> list = List.of("a", "b", "c");
                      assertThat(list).last().isNotNull();
                      assertThat(list.getFirst()).isNotEmpty();
                      assertThat(list.getLast()).contains("c");
                  }
              }
              """
          )
        );
    }

    @Test
    void simplifiesAssertionsInheritedByObjectAssert() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void verify(List<String> strings, List<Integer> numbers) {
                      assertThat(strings.getFirst()).usingComparator(String.CASE_INSENSITIVE_ORDER).isNull();
                      assertThat(strings.getFirst()).as("value").isNotEqualTo("b");
                      assertThat(numbers.getLast()).isEqualTo(1);
                      assertThat(numbers.getLast()).isNotNull().isInstanceOf(Integer.class);
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void verify(List<String> strings, List<Integer> numbers) {
                      assertThat(strings).first().usingComparator(String.CASE_INSENSITIVE_ORDER).isNull();
                      assertThat(strings).first().as("value").isNotEqualTo("b");
                      assertThat(numbers).last().isEqualTo(1);
                      assertThat(numbers).last().isNotNull().isInstanceOf(Integer.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsElementSpecificAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void verify(List<String> strings, List<StringBuilder> builders, List<Integer> numbers, List<Boolean> flags, List<List<String>> lists) {
                      assertThat(strings.getFirst()).endsWith("setsid");
                      assertThat(strings.getFirst()).as("value").startsWith("a");
                      assertThat(strings.getFirst()).matches("a.*");
                      assertThat(strings.getFirst()).isEqualTo("%s", "a");
                      assertThat(builders.getFirst()).startsWith("a");
                      assertThat(numbers.getFirst()).isGreaterThan(1);
                      assertThat(flags.getLast()).isTrue();
                      assertThat(lists.getLast()).hasSize(1);
                  }
              }
              """
          )
        );
    }
}
