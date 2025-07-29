/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveInitMocksIfRunnersSpecifiedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "mockito-core", "mockito-junit-jupiter", "junit-4", "junit-jupiter-api-5"))
          .recipe(new RemoveInitMocksIfRunnersSpecified());
    }

    @DocumentExample
    @Test
    void removeInitMocksInJUnit5() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.MockitoAnnotations;

              @ExtendWith(MockitoExtension.class)
              class A {

                  @BeforeEach
                  public void setUp() {
                      MockitoAnnotations.initMocks(this);
                  }

                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {

                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeInitMocksWithStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.MockitoAnnotations;

              import static org.mockito.MockitoAnnotations.initMocks;

              @ExtendWith(MockitoExtension.class)
              class A {

                  public void setUp() {
                      initMocks(this);
                  }

                  public void test() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {

                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeInitMocksInJUnit4() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Before;
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;
              import org.mockito.MockitoAnnotations;

              @RunWith(MockitoJUnitRunner.class)
              class A {

                  @Before
                  public void setUp() {
                      MockitoAnnotations.initMocks(this);
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;

              @RunWith(MockitoJUnitRunner.class)
              class A {
              }
              """
          )
        );
    }

    @Test
    void leaveEnclosingMethodIfNotEmpty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;
              import org.mockito.MockitoAnnotations;

              @RunWith(MockitoJUnitRunner.class)
              class A {

                  public void setUp() {
                      MockitoAnnotations.initMocks(this);
                      System.out.println("log");
                  }
              }
              """,
            """
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;

              @RunWith(MockitoJUnitRunner.class)
              class A {

                  public void setUp() {
                      System.out.println("log");
                  }
              }
              """
          )
        );
    }

    @Test
    void notRemoveInitMocksWithoutRunners() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.MockitoAnnotations;

              class A {

                  public void setUp() {
                      MockitoAnnotations.initMocks(this);
                  }

                  public void test() {
                  }
              }
              """
          )
        );
    }
}
