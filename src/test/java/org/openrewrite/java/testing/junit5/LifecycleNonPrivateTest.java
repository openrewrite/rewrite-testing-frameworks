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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("JUnitMalformedDeclaration")
class LifecycleNonPrivateTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new LifecycleNonPrivate());
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    void beforeEachPrivate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
                            
              class MyTest {
                  @BeforeEach
                  private void beforeEach() {
                  }
                  private void unaffected() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
                            
              class MyTest {
                  @BeforeEach
                  void beforeEach() {
                  }
                  private void unaffected() {
                  }
              }
              """)
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    void afterAllPrivate() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterAll;
                            
              class MyTest {
                  @AfterAll
                  private static void afterAll() {
                  }
                  private void unaffected() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll;
                            
              class MyTest {
                  @AfterAll
                  static void afterAll() {
                  }
                  private void unaffected() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/241")
    void beforeEachAfterAllUnchanged() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeEach;
                          
              class MyTest {
                  @BeforeEach
                  void beforeEach() {
                  }
                  @AfterAll
                  static void afterAll() {
                  }
                  private void unaffected() {
                  }
              }
              """
          )
        );
    }
}
