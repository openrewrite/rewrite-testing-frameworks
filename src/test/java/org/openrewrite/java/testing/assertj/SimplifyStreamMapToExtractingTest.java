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

class SimplifyStreamMapToExtractingTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3")
            .dependsOn("""
              record Row(String value) {
                  String getValue() { return value; }
              }
              """))
          .recipe(new SimplifyStreamMapToExtracting());
    }

    @DocumentExample
    @Test
    void streamMapWithMethodReference() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<Row> rows) {
                      assertThat(rows.stream().map(Row::getValue)).contains("a");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<Row> rows) {
                      assertThat(rows).extracting(Row::getValue).contains("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void streamMapWithLambda() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<Row> rows) {
                      assertThat(rows.stream().map(r -> r.getValue())).contains("a");
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<Row> rows) {
                      assertThat(rows).extracting(r -> r.getValue()).contains("a");
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
                  void testMethod(List<Row> rows) {
                      assertThat(rows.stream().map(Row::getValue)).containsExactly("a", "b");
                      assertThat(rows.stream().map(Row::getValue)).hasSize(2);
                      assertThat(rows.stream().map(Row::getValue)).isEmpty();
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<Row> rows) {
                      assertThat(rows).extracting(Row::getValue).containsExactly("a", "b");
                      assertThat(rows).extracting(Row::getValue).hasSize(2);
                      assertThat(rows).extracting(Row::getValue).isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithoutStreamMap() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<String> list) {
                      assertThat(list).contains("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithIntermediateOperations() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(List<Row> rows) {
                      assertThat(rows.stream().filter(r -> r.getValue() != null).map(Row::getValue)).contains("a");
                  }
              }
              """
          )
        );
    }
}
