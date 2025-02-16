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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyChainedAssertJAssertionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"));
    }

    @DocumentExample
    @Test
    void stringIsEmpty() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isTrue", "isEmpty", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat("hello world".isEmpty()).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat("hello world").isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    @Disabled(".as(reason) is not yet supported")
    void stringIsEmptyDescribedAs() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isTrue", "isEmpty", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(String actual) {
                      assertThat(actual.isEmpty()).as("Reason").isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(String actual) {
                      assertThat(actual).as("Reason").isEmpty();
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedRecipes() {
        rewriteRun(
          spec -> spec.recipes(
            new SimplifyChainedAssertJAssertion("isEmpty", "isTrue", "isEmpty", "java.lang.String"),
            new SimplifyChainedAssertJAssertion("trim", "isEmpty", "isBlank", "java.lang.String")
          ),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat(getString().isEmpty()).isTrue();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat(getString()).isEmpty();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """
          )
        );
    }

    @Test
    void chainedRecipesOfDifferingTypes() {
        rewriteRun(
          spec -> spec.recipes(
            new SimplifyChainedAssertJAssertion("startsWith", "isTrue", "startsWith", "java.lang.String"),
            new SimplifyChainedAssertJAssertion("startsWith", "isTrue", "startsWithRaw", "java.nio.file.Path")
          ),
          //language=java
          java(
            """
              import java.nio.file.Path;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void string(String actual) {
                      assertThat(actual.startsWith("prefix")).isTrue();
                  }

                  void path(Path actual) {
                      assertThat(actual.startsWith("prefix")).isTrue();
                  }
              }
              """,
            """
              import java.nio.file.Path;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void string(String actual) {
                      assertThat(actual).startsWith("prefix");
                  }

                  void path(Path actual) {
                      assertThat(actual).startsWithRaw(Path.of("prefix"));
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThatArgHasArgument() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("equalsIgnoreCase", "isTrue", "isEqualToIgnoringCase", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      String expected = "hello world";
                      assertThat(getString().equalsIgnoreCase(expected)).isTrue();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      String expected = "hello world";
                      assertThat(getString()).isEqualToIgnoringCase(expected);
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """
          )
        );
    }

    @Test
    void replacementHasArgument() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("length", "isEqualTo", "hasSize", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      int length = 5;
                      assertThat(getString().length()).isEqualTo(length);
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      int length = 5;
                      assertThat(getString()).hasSize(length);
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """
          )
        );

    }

    @Test
    void normalCase() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("trim", "isEmpty", "isBlank", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat(getString().trim()).isEmpty();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat(getString()).isBlank();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """
          )
        );
    }

    @Test
    void stringContains() {
        rewriteRun(
          spec -> spec.recipes(
            new SimplifyChainedAssertJAssertion("contains", "isTrue", "contains", "java.lang.String"),
            new SimplifyChainedAssertJAssertion("contains", "isFalse", "doesNotContain", "java.lang.String")
          ),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat("hello world".contains("lo wo")).isTrue();
                      assertThat("hello world".contains("lll")).isFalse();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat("hello world").contains("lo wo");
                      assertThat("hello world").doesNotContain("lll");
                  }
              }
              """
          )
        );
    }

    @Test
    void stringContainsObjectMethod() {
        rewriteRun(
          spec -> spec.recipes(
            new SimplifyChainedAssertJAssertion("contains", "isTrue", "contains", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Pojo {
                public String getString() {
                    return "lo wo";
                }
              }

              class MyTest {
                  void testMethod() {
                      var pojo = new Pojo();
                      assertThat("hello world".contains(pojo.getString())).isTrue();
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Pojo {
                public String getString() {
                    return "lo wo";
                }
              }

              class MyTest {
                  void testMethod() {
                      var pojo = new Pojo();
                      assertThat("hello world").contains(pojo.getString());
                  }
              }
              """
          )
        );
    }

    @Test
    void mapMethodDealsWithTwoArguments() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("get", "isEqualTo", "containsEntry", "java.util.Map")),
          //language=java
          java(
            """
              import java.util.Collections;
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      String key = "key";
                      String value = "value";
                      assertThat(getMap().get(key)).isEqualTo(value);
                  }

                  Map<String, String> getMap() {
                      return Collections.emptyMap();
                  }
              }
              """,
            """
              import java.util.Collections;
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      String key = "key";
                      String value = "value";
                      assertThat(getMap()).containsEntry(key, value);
                  }

                  Map<String, String> getMap() {
                      return Collections.emptyMap();
                  }
              }
              """
          )
        );
    }

    @Test
    void keySetContainsWithMultipleArguments() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("keySet", "contains", "containsKey", "java.util.Map")),
          //language=java
          java(
            """
              import java.util.Map;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod(Map<String, String> map) {
                      // we don't yet support `containsKeys`
                      assertThat(map.keySet()).contains("a", "b", "c");
                  }
              }
              """
          )
        );
    }

    @Test
    void isNotEmptyTest() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat(getString().isEmpty()).isFalse();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat(getString()).isNotEmpty();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNoRunOnWrongCombination() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty", "java.lang.String")),
          //language=java
          java(
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  void testMethod() {
                      assertThat(getString().isBlank()).isFalse();
                  }

                  String getString() {
                      return "hello world";
                  }
              }
              """
          )
        );
    }

    @Nested
    class OptionalAssertions {

        @Test
        void simplifyPresenceAssertion() {
            rewriteRun(
              spec -> spec.recipes(
                new SimplifyChainedAssertJAssertion("isPresent", "isTrue", "isPresent", "java.util.Optional"),
                new SimplifyChainedAssertJAssertion("isEmpty", "isTrue", "isEmpty", "java.util.Optional"),
                new SimplifyChainedAssertJAssertion("isPresent", "isFalse", "isNotPresent", "java.util.Optional"),
                new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty", "java.util.Optional")
              ),
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import java.util.Optional;

                  class Test {
                      void simpleTest(Optional<String> o) {
                          assertThat(o.isPresent()).isTrue();
                          assertThat(o.isEmpty()).isTrue();
                          assertThat(o.isPresent()).isFalse();
                          assertThat(o.isEmpty()).isFalse();
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import java.util.Optional;

                  class Test {
                      void simpleTest(Optional<String> o) {
                          assertThat(o).isPresent();
                          assertThat(o).isEmpty();
                          assertThat(o).isNotPresent();
                          assertThat(o).isNotEmpty();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void simplifiyEqualityAssertion() {
            rewriteRun(
              spec -> spec.recipes(
                new SimplifyChainedAssertJAssertion("get", "isEqualTo", "contains", "java.util.Optional"),
                new SimplifyChainedAssertJAssertion("get", "isSameAs", "containsSame", "java.util.Optional")
              ),
              //language=java
              java(
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import java.util.Optional;

                  class Test {
                      void simpleTest(Optional<String> o) {
                          assertThat(o.get()).isEqualTo("foo");
                          assertThat(o.get()).isSameAs("foo");
                      }
                  }
                  """,
                """
                  import static org.assertj.core.api.Assertions.assertThat;
                  import java.util.Optional;

                  class Test {
                      void simpleTest(Optional<String> o) {
                          assertThat(o).contains("foo");
                          assertThat(o).containsSame("foo");
                      }
                  }
                  """
              )
            );
        }
    }
}
