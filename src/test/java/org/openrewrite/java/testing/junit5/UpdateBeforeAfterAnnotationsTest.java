/*
 * Copyright 2021 the original author or authors.
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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("JUnitMalformedDeclaration")
class UpdateBeforeAfterAnnotationsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4.13"))
          .recipe(new UpdateBeforeAfterAnnotations());
    }

    @DocumentExample
    @Test
    void beforeToBeforeEach() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;

              class Test {
                  @Before
                  void before() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;

              class Test {
                  @BeforeEach
                  void before() {
                  }
              }
              """
          ),
          //language=kotlin
          kotlin(
            """
              import org.junit.Before

              class Test {

                  @Before
                  fun before() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach

              class Test {

                  @BeforeEach
                  fun before() {
                  }
              }
              """
          )
        );
    }

    @Test
    void afterToAfterEach() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.After;
              
              class Test {
              
                  @After
                  void after() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              
              class Test {
              
                  @AfterEach
                  void after() {
                  }
              }
              """
          )
        );
    }

    @Test
    void beforeClassToBeforeAll() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.BeforeClass;
              
              class Test {
              
                  @BeforeClass
                  void beforeClass() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeAll;
              
              class Test {
              
                  @BeforeAll
                  void beforeClass() {
                  }
              }
              """
          )
        );
    }

    @Test
    void afterClassToAfterAll() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.AfterClass;
              
              class Test {
                  @AfterClass
                  void afterClass() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll;
              
              class Test {
                  @AfterAll
                  void afterClass() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/150")
    @Disabled
    @Test
    void convertsToPackageVisibility() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Before;
              
              class Test {
              
                  @Before // comments
                  public void before() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              
              class Test {
              
                  // comments
                  @BeforeEach
                  void before() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/59")
    void retainPublicModifierOnOverriddenMethod() {
        //language=java
        rewriteRun(

          java(
            """
              public class AbstractTest {
                  abstract public void setup();
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              
              public class A extends AbstractTest {
              
                  @Before
                  public void setup() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              
              public class A extends AbstractTest {
              
                  @BeforeEach
                  public void setup() {
                  }
              }
              """
          )
        );
    }
}
