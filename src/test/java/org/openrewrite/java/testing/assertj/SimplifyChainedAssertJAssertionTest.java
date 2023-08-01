/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SimplifyChainedAssertJAssertionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9", "assertj-core-3.24"));
    }

    @Test
    void stringIsEmpty() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty(java.lang.String)", "isTrue", "isEmpty")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString().isEmpty()).isTrue();
                  }
                  
                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
    void chainedRecipes() {
        rewriteRun(
          spec -> spec.recipes(
            new SimplifyChainedAssertJAssertion("isEmpty", "isTrue", "isEmpty"),
            new SimplifyChainedAssertJAssertion("trim", "isEmpty", "isBlank")
          ),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString().isEmpty()).isTrue();
                  }
                  
                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
            new SimplifyChainedAssertJAssertion("startsWith", "isTrue", "startsWith"),
            new SimplifyChainedAssertJAssertion("startsWith", "isTrue", "startsWithRaw")
          ),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import java.nio.file.Path;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void String(String actual) {
                      assertThat(actual.startsWith("prefix")).isTrue();
                  }

                  @Test
                  void path(Path actual) {
                      assertThat(actual.startsWith("prefix")).isTrue();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import java.nio.file.Path;

              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void String(String actual) {
                      assertThat(actual).startsWith("prefix");
                  }

                  @Test
                  void path(Path actual) {
                      assertThat(actual).startsWithRaw(Path.of("prefix"));
                  }
              }
              """
          )
        );
    }

    @Test
    void replacementHasZeroArgument() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("getString", "hasSize", "isEmpty")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString()).hasSize(0);
                  }
                  
                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
    void assertThatArgHasArgument() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("equalsIgnoreCase", "isTrue", "isEqualToIgnoringCase")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("length", "isEqualTo", "hasSize")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("trim", "isEmpty", "isBlank")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString().trim()).isEmpty();
                  }
                  
                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
    void mapMethodDealsWithTwoArguments() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("get", "isEqualTo", "containsEntry")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.Collections;
              import java.util.Map;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
              import org.junit.jupiter.api.Test;
              import java.util.Collections;
              import java.util.Map;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
    void isNotEmptyTest() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString().isEmpty()).isFalse();
                  }
                  
                  String getString() {
                      return "hello world";
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString().isNotEmpty()).isFalse();
                  }
                  
                  String getString() {
                      return "hello world";
                  }
              }
              """
          )
        );
    }
}