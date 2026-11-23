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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class InlineSatisfiesOfAssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(new InlineSatisfiesOfAssertions());
    }

    @DocumentExample
    @Test
    void inlineOneAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assumptions.assumeThat;

              class A {
                  void test(String s) {
                      assertThat(s).satisfies(arg -> assertThat(arg).startsWith("a"));
                      assertThat(s).as("reason").satisfies(arg -> assertThat(arg).startsWith("a").endsWith("b"));
                      assumeThat(s).satisfies(arg -> assertThat(arg).isNotNull());
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assumptions.assumeThat;

              class A {
                  void test(String s) {
                      assertThat(s).startsWith("a");
                      assertThat(s).as("reason").startsWith("a").endsWith("b");
                      assumeThat(s).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepWhenTheParameterIsUsedAgain() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(String s) {
                      assertThat(s).satisfies(arg -> assertThat(arg).startsWith(arg.substring(0, 1)));
                      assertThat(s).satisfies(arg -> assertThat(arg.length()).isPositive());
                  }
              }
              """
          )
        );
    }

    @Test
    void chainAssertionsReturningTheirAssertion() {
        //language=java
        rewriteRun(
          // the lambdas' parameter is a `List<? extends String>`, whose assertion takes no `String`
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          java(
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(List<String> list) {
                      assertThat(list)
                        .satisfies(
                          arg -> assertThat(arg).contains("a"),
                          arg -> assertThat(arg).contains("b").hasSize(2)
                        );
                  }
              }
              """,
            """
              import java.util.List;

              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(List<String> list) {
                      assertThat(list).contains("a").contains("b").hasSize(2);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepWhenAnAssertionButTheLastReturnsAnotherAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class A {
                  void test(Object o) {
                      assertThat(o)
                        .satisfies(
                          arg -> assertThat(arg).asString().isEqualTo("a"),
                          arg -> assertThat(arg).isNotNull()
                        );
                  }
              }
              """
          )
        );
    }
}
