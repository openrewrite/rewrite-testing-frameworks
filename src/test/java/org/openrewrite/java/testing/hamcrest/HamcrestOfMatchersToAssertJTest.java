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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class HamcrestOfMatchersToAssertJTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9",
            "hamcrest-2.2",
            "assertj-core-3.24"))
          .recipe(new HamcrestOfMatchersToAssertJ());
    }

    @Test
    @DocumentExample
    void allOfMigrate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.allOf;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
                            
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world", allOf(equalTo("hello world"), hasLength(12)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;

              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world")
                              .satisfies(
                                      arg -> assertThat(arg, equalTo("hello world")),
                                      arg -> assertThat(arg, hasLength(12))
                              );
                  }
              }
              """
          )
        );
    }

    @Test
    void allOfMigrateHasReason() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.allOf;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
                            
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("reason", "hello world", allOf(equalTo("hello world"), hasLength(12)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;

              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world")
                              .as("reason")
                              .satisfies(
                                      arg -> assertThat(arg, equalTo("hello world")),
                                      arg -> assertThat(arg, hasLength(12))
                              );
                  }
              }
              """
          )
        );
    }

    @Test
    void allOfArgumentIsIterable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import java.util.Arrays;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasLength;
              import static org.hamcrest.Matchers.allOf;

              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world", allOf(Arrays.asList(hasLength(11), hasLength(11))));
                  }
              }
              """
          )
        );
    }

    @Test
    void anyOfMigrate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.anyOf;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
                            
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world", anyOf(equalTo("hello world"), hasLength(12)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;

              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world")
                              .satisfiesAnyOf(
                                      arg -> assertThat(arg, equalTo("hello world")),
                                      arg -> assertThat(arg, hasLength(12))
                              );
                  }
              }
              """
          )
        );
    }

    @Test
    void anyOfHasALotOfArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.anyOf;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
                            
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world", anyOf(equalTo("hello world"), hasLength(12), hasLength(12), hasLength(12), hasLength(12)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;

              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world")
                              .satisfiesAnyOf(
                                      arg -> assertThat(arg, equalTo("hello world")),
                                      arg -> assertThat(arg, hasLength(12)),
                                      arg -> assertThat(arg, hasLength(12)),
                                      arg -> assertThat(arg, hasLength(12)),
                                      arg -> assertThat(arg, hasLength(12))
                              );
                  }
              }
              """
          )
        );
    }

    @Test
    void anyOfArgumentIsIterable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              import java.util.Arrays;

              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.hasLength;
              import static org.hamcrest.Matchers.anyOf;

              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world", anyOf(Arrays.asList(hasLength(11), hasLength(11))));
                  }
              }
              """
          )
        );
    }

    @Test
    void assertThatHasReasonArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
                            
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.anyOf;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
                            
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("reason", "hello world", anyOf(equalTo("hello world"), hasLength(12)));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.assertj.core.api.Assertions.assertThat;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.equalTo;
              import static org.hamcrest.Matchers.hasLength;
                            
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat("hello world")
                              .as("reason")
                              .satisfiesAnyOf(
                                      arg -> assertThat(arg, equalTo("hello world")),
                                      arg -> assertThat(arg, hasLength(12))
                              );
                  }
              }
              """
          )
        );
    }
}
