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
package org.openrewrite.java.testing.awaitility;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.RewriteTest;

import java.util.function.Consumer;

import static org.openrewrite.java.Assertions.java;

class AwaitilityUntilAssertedTest implements RewriteTest {

    private static final Consumer<SourceSpec<J.CompilationUnit>> WITH_ASSERTJ = spec -> spec.markers(JavaSourceSet.build("main",
      JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), "awaitility-4", "hamcrest", "assertj-core-3")));
    private static final Consumer<SourceSpec<J.CompilationUnit>> WITHOUT_ASSERTJ = spec -> spec.markers(JavaSourceSet.build("main",
      JavaParser.dependenciesFromResources(new InMemoryExecutionContext(), "awaitility-4", "hamcrest")));

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "awaitility-4", "hamcrest", "assertj-core-3"))
          .recipe(new AwaitilityUntilAsserted());
    }

    @DocumentExample
    @Test
    void equalsAndSize() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test(List<String> list, String actual) {
                      await().until(() -> list.size() == 3);
                      await().until(() -> actual.equals("done"));
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test(List<String> list, String actual) {
                      await().untilAsserted(() -> assertThat(list).hasSize(3));
                      await().untilAsserted(() -> assertThat(actual).isEqualTo("done"));
                  }
              }
              """,
            WITH_ASSERTJ
          )
        );
    }

    @Test
    void emptinessNullAndPrimitives() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import java.util.concurrent.atomic.AtomicInteger;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test(List<String> list, Object o, AtomicInteger counter) {
                      await().until(() -> list.isEmpty());
                      await().until(() -> !list.isEmpty());
                      await().until(() -> list.contains("a"));
                      await().until(() -> o != null);
                      await().until(() -> o == null);
                      await().until(() -> counter.get() == 5);
                      await().until(() -> counter.get() != 5);
                  }
              }
              """,
            """
              import java.util.List;
              import java.util.concurrent.atomic.AtomicInteger;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test(List<String> list, Object o, AtomicInteger counter) {
                      await().untilAsserted(() -> assertThat(list).isEmpty());
                      await().untilAsserted(() -> assertThat(list).isNotEmpty());
                      await().untilAsserted(() -> assertThat(list).contains("a"));
                      await().untilAsserted(() -> assertThat(o).isNotNull());
                      await().untilAsserted(() -> assertThat(o).isNull());
                      await().untilAsserted(() -> assertThat(counter.get()).isEqualTo(5));
                      await().untilAsserted(() -> assertThat(counter.get()).isNotEqualTo(5));
                  }
              }
              """,
            WITH_ASSERTJ
          )
        );
    }

    @Test
    void leaveUnsupportedConditionsAlone() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.concurrent.atomic.AtomicBoolean;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test(AtomicBoolean flag, Integer boxed, Integer other) {
                      await().until(flag::get);
                      await().until(() -> flag.get() && boxed > 3);
                      await().until(() -> boxed == other);
                      await().until(() -> { return boxed != null; });
                  }
              }
              """,
            WITH_ASSERTJ
          )
        );
    }

    @Test
    void doNothingWithoutAssertJOnClasspath() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;
              import static org.awaitility.Awaitility.await;

              class A {
                  void test(List<String> list) {
                      await().until(() -> list.size() == 3);
                  }
              }
              """,
            WITHOUT_ASSERTJ
          )
        );
    }
}
