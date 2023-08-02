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

class FlattenAllOfTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new FlattenAllOf())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "hamcrest-2.2",
              "assertj-core-3.24"));
    }

    @DocumentExample
    @Test
    void flattenAllOfStringMatchers() {
        rewriteRun(
          //language=java
          java("""
            import org.junit.jupiter.api.Test;
            
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.allOf;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.hasLength;
                            
            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1, allOf(equalTo(str2), hasLength(12)));
                }
            }
            ""","""
            import org.junit.jupiter.api.Test;
            
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.hasLength;
                            
            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1, equalTo(str2));
                    assertThat(str1, hasLength(12));
                }
            }
            """));
    }

    @Test
    void flattenAllOfStringMatchersWithReason() {
        rewriteRun(
          //language=java
          java("""
            import org.junit.jupiter.api.Test;
            
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.allOf;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.hasLength;
                            
            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat("str1 and str2 should be equal", str1, allOf(equalTo(str2), hasLength(12)));
                }
            }
            ""","""
            import org.junit.jupiter.api.Test;
            
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.hasLength;
                            
            class ATest {
                @Test
                void test() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat("str1 and str2 should be equal", str1, equalTo(str2));
                    assertThat("str1 and str2 should be equal", str1, hasLength(12));
                }
            }
            """));
    }

    @Test
    void flattenAllOfIntMatchers() {
        rewriteRun(
          //language=java
          java("""
            import org.junit.jupiter.api.Test;
            
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.allOf;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.greaterThan;
                            
            class ATest {
                @Test
                void test() {
                    int i = 1;
                    assertThat(i, allOf(equalTo(1), greaterThan(0)));
                }
            }
            ""","""
            import org.junit.jupiter.api.Test;
            
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.equalTo;
            import static org.hamcrest.Matchers.greaterThan;
                            
            class ATest {
                @Test
                void test() {
                    int i = 1;
                    assertThat(i, equalTo(1));
                    assertThat(i, greaterThan(0));
                }
            }
            """));
    }

}