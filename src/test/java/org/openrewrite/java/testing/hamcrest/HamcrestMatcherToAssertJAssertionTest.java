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
package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class HamcrestMatcherToAssertJAssertionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "hamcrest-2.2",
              "assertj-core-3.24"));
    }

    private static Stream<Arguments> replacements() {
        return Stream.of(
          Arguments.arguments("str1", "isEmptyString", "", "isEmpty"),
          Arguments.arguments("str1", "equalTo", "str2", "isEqualTo"),
          Arguments.arguments("bis1", "equalTo", "bis2", "isEqualTo")
        );
    }

    @ParameterizedTest
    @MethodSource("replacements")
    void replace(String actual, String hamcrestMatcher, String matcherArgs, String assertJAssertion) {
        //language=java
        String importsBefore = """
          import static org.hamcrest.MatcherAssert.assertThat;
          import static org.hamcrest.Matchers.%s;""".formatted(hamcrestMatcher);
        String importsAfter = "import static org.assertj.core.api.Assertions.assertThat;";
        //language=java
        String template = """
          import org.junit.jupiter.api.Test;
                    
          %s
                    
          class BiscuitTest {
              @Test
              void testEquals() {
                  String str1 = "Hello world!";
                  String str2 = "Hello world!";
                  Biscuit bis1 = new Biscuit("Ginger");
                  Biscuit bis2 = new Biscuit("Ginger");
                  %s
              }
          }
          """;
        rewriteRun(
          spec -> spec.recipe(new HamcrestMatcherToAssertJAssertion(hamcrestMatcher, assertJAssertion)),
          java("""
            class Biscuit {
                String name;
                Biscuit(String name) {
                    this.name = name;
                }
            }
            """),
          java(
            template.formatted(importsBefore, "assertThat(%s, %s(%s));".formatted(actual, hamcrestMatcher, matcherArgs)),
            template.formatted(importsAfter, "assertThat(%s).%s(%s);".formatted(actual, assertJAssertion, matcherArgs)))
        );
    }
}