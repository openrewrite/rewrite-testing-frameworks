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

class RemoveIsMatcherTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "hamcrest-2.2"))
          .recipe(new RemoveIsMatcher());
    }

    @Test
    @DocumentExample
    void isMatcher() {
        rewriteRun(
          //language=java
          java("""
            import org.junit.jupiter.api.Test;
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.is;
            import static org.hamcrest.Matchers.equalTo;
                            
            class ATest {
                @Test
                void testEquals() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1, is(equalTo(str2)));
                }
            }
            ""","""
            import org.junit.jupiter.api.Test;
            import static org.hamcrest.MatcherAssert.assertThat;
            import static org.hamcrest.Matchers.equalTo;
                            
            class ATest {
                @Test
                void testEquals() {
                    String str1 = "Hello world!";
                    String str2 = "Hello world!";
                    assertThat(str1, equalTo(str2));
                }
            }
            """));
    }

    @Test
    void isNotCalledInAssertThat() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.Matchers.is;
              import static org.hamcrest.Matchers.equalTo;
              
              class ATest {
                  @Test
                  void testMethod() {
                      String str1 = "Hello world!";
                      Matcher<String> x = is(equalTo(str1));
                  }
              }
              """
          )
        );
    }

    @Test
    void isNotDirectlyInAssertThat() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.*;
              
              class ATest {
                  @Test
                  void testMethod() {
                      String str1 = "Hello world!";
                      String str2 = "Hello world!";
                      assertThat(str1, not(is(equalTo(str2))));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.not;
              import static org.hamcrest.Matchers.equalTo;
              
              class ATest {
                  @Test
                  void testMethod() {
                      String str1 = "Hello world!";
                      String str2 = "Hello world!";
                      assertThat(str1, not(equalTo(str2)));
                  }
              }
              """
          )
        );
    }

    @Test
    void noReplacementForOtherMethodInvocations() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matcher;
              import static org.hamcrest.Matchers.*;
              
              class ATest {
                  @Test
                  void testMethod() {
                      String str1 = "Hello world!";
                      String str2 = "Hello world!";
                      foo(is(equalTo(str2)));
                  }
                  
                  void foo(Matcher<String> matcher) {
                  }
              }
              """
          )
        );
    }
}
