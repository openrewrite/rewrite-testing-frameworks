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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveDuplicateTestTemplatesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new RemoveDuplicateTestTemplates());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/314")
    @Test
    @DocumentExample
    void removeDuplicate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @Test
                  @RepeatedTest(3)
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  void testMethod() {
                      System.out.println("foobar");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @RepeatedTest(3)
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  void testMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }

    @Test
    void removeDuplicateOnly() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @Test
                  @RepeatedTest(3)
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  void testMethodA() {
                      System.out.println("foobar");
                  }

                  @Test
                  void testMethodB() {
                      System.out.println("foobar");
                  }

                  @RepeatedTest(3)
                  void testMethodC() {
                      System.out.println("foobar");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @RepeatedTest(3)
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  void testMethodA() {
                      System.out.println("foobar");
                  }

                  @Test
                  void testMethodB() {
                      System.out.println("foobar");
                  }

                  @RepeatedTest(3)
                  void testMethodC() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesWhenOutOfOrder() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  @RepeatedTest(3)
                  @Test
                  void testMethod() {
                      System.out.println("foobar");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.DisplayName;
              
              class MyTest {
                  @DisplayName("When an entry does not exist, it should be created and initialized to 0")
                  @RepeatedTest(3)
                  void testMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWithOnlyTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotRemoveWithOnlyRepeatedTest() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.RepeatedTest;
              
              class MyTest {
                  @RepeatedTest(3)
                  void testMethod() {
                      System.out.println("foobar");
                  }
              }
              """
          )
        );
    }
}
