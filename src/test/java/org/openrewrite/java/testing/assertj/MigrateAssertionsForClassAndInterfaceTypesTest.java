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
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/664")
class MigrateAssertionsForClassAndInterfaceTypesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.assertj")
            .build()
            .activateRecipes("org.openrewrite.java.testing.assertj.MigrateAssertionsForClassAndInterfaceTypes"));
    }

    @DocumentExample
    @Test
    void collisionTypesUseAssertThatObject() {
        // An Iterable/Map argument relies on AssertionsForClassTypes returning an ObjectAssert; plain
        // Assertions.assertThat would re-bind to IterableAssert/MapAssert and `hasNoNullFieldsOrProperties()`
        // would no longer compile. Pin ObjectAssert via assertThatObject.
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForClassTypes;

              import java.util.Map;
              import java.util.Set;

              class Test {
                  void method(Set<String> set, Map<String, Integer> map) {
                      AssertionsForClassTypes.assertThat(set).hasNoNullFieldsOrProperties();
                      AssertionsForClassTypes.assertThat(map).isNotNull();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import java.util.Map;
              import java.util.Set;

              class Test {
                  void method(Set<String> set, Map<String, Integer> map) {
                      Assertions.assertThatObject(set).hasNoNullFieldsOrProperties();
                      Assertions.assertThatObject(map).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void plainObjectKeepsAssertThat() {
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForClassTypes;

              class Test {
                  void method(Object o) {
                      AssertionsForClassTypes.assertThat(o).isNotNull();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class Test {
                  void method(Object o) {
                      Assertions.assertThat(o).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void comparableArgumentUsesAssertThatObject() {
        // Assertions has a more specific assertThat(Comparable) overload; preserve the ObjectAssert binding.
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForClassTypes;

              class Test {
                  enum Color { RED, GREEN }
                  void method(Color color) {
                      AssertionsForClassTypes.assertThat(color).isNotNull();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class Test {
                  enum Color { RED, GREEN }
                  void method(Color color) {
                      Assertions.assertThatObject(color).isNotNull();
                  }
              }
              """
          )
        );
    }

    @Test
    void typedOverloadsOnlyRetarget() {
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForClassTypes;

              class Test {
                  void method() {
                      AssertionsForClassTypes.assertThat(true).isTrue();
                      AssertionsForClassTypes.assertThat("value").isNotEmpty();
                      AssertionsForClassTypes.assertThat(1).isPositive();
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class Test {
                  void method() {
                      Assertions.assertThat(true).isTrue();
                      Assertions.assertThat("value").isNotEmpty();
                      Assertions.assertThat(1).isPositive();
                  }
              }
              """
          )
        );
    }

    @Test
    void interfaceTypesAreAlwaysSafe() {
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForInterfaceTypes;

              import java.util.List;

              class Test {
                  void method(List<String> list) {
                      AssertionsForInterfaceTypes.assertThat(list).hasSize(0);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              import java.util.List;

              class Test {
                  void method(List<String> list) {
                      Assertions.assertThat(list).hasSize(0);
                  }
              }
              """
          )
        );
    }

    @Test
    void nonAssertThatHelpersRetarget() {
        //language=java
        rewriteRun(
          java(
            """
              import org.assertj.core.api.AssertionsForClassTypes;

              class Test {
                  void method() {
                      Throwable thrown = AssertionsForClassTypes.catchThrowable(() -> {
                          throw new IllegalStateException("boom");
                      });
                      AssertionsForClassTypes.assertThat(thrown).isInstanceOf(IllegalStateException.class);
                  }
              }
              """,
            """
              import org.assertj.core.api.Assertions;

              class Test {
                  void method() {
                      Throwable thrown = Assertions.catchThrowable(() -> {
                          throw new IllegalStateException("boom");
                      });
                      Assertions.assertThat(thrown).isInstanceOf(IllegalStateException.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void staticImportCollisionForm() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Set;

              import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

              class Test {
                  void method(Set<String> set) {
                      assertThat(set).hasNoNullFieldsOrProperties();
                  }
              }
              """,
            """
              import java.util.Set;

              import static org.assertj.core.api.Assertions.assertThatObject;

              class Test {
                  void method(Set<String> set) {
                      assertThatObject(set).hasNoNullFieldsOrProperties();
                  }
              }
              """
          )
        );
    }
}
