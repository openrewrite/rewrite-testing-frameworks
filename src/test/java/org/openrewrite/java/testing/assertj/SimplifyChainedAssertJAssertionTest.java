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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
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

    @DocumentExample
    @Test
    void stringIsEmpty() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isTrue", "isEmpty", "java.lang.String")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world".isEmpty()).isTrue();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
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
            new SimplifyChainedAssertJAssertion("startsWith", "isTrue", "startsWith", "java.lang.String"),
            new SimplifyChainedAssertJAssertion("startsWith", "isTrue", "startsWithRaw", "java.nio.file.Path")
          ),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
    
              import java.nio.file.Path;
    
              import static org.assertj.core.api.Assertions.assertThat;
    
              class MyTest {
                  @Test
                  void string(String actual) {
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
                  void string(String actual) {
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
    void assertThatArgHasArgument() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("equalsIgnoreCase", "isTrue", "isEqualToIgnoringCase", "java.lang.String")),
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("length", "isEqualTo", "hasSize", "java.lang.String")),
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("trim", "isEmpty", "isBlank", "java.lang.String")),
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("get", "isEqualTo", "containsEntry", "java.util.Map")),
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty", "java.lang.String")),
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
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("isEmpty", "isFalse", "isNotEmpty", "java.lang.String")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
    
              import static org.assertj.core.api.Assertions.assertThat;
    
              class MyTest {
                  @Test
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

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/398")
    void sizeIsEqualToZeroToIsEmpty() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertion("size", "isEqualTo", "hasSize", "java.util.List")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;
              
              import static org.assertj.core.api.Assertions.assertThat;
    
              class MyTest {
                  @Test
                  void testMethod() {
                      List<String> objectIdentifies = List.of();
                      assertThat(objectIdentifies.size()).isEqualTo(0);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import java.util.List;
              
              import static org.assertj.core.api.Assertions.assertThat;
    
              class MyTest {
                  @Test
                  void testMethod() {
                      List<String> objectIdentifies = List.of();
                      assertThat(objectIdentifies).isEmpty();
                  }
              }
              """
          )
        );
    }
}
