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

class ReplaceInitMockToOpenMockTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "mockito-core", "mockito-junit-jupiter", "junit-4", "junit-jupiter-api-5"))
          .recipe(new ReplaceInitMockToOpenMock());
    }

    @DocumentExample
    @Test
    void replaceInitMocksToOpenMocks() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  @BeforeEach
                  public void setUp() {
                      test1();
                      MockitoAnnotations.initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }
              }
              """,
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  private AutoCloseable mocks;

                  @BeforeEach
                  public void setUp() {
                      test1();
                      mocks = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void tearDown() throws Exception {
                      mocks.close();
                  }
              }
              """
          )
        );
    }


    @Test
    void replaceInitMocksWithStaticImportToOpenMocks() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.BeforeEach;

              import static org.mockito.MockitoAnnotations.initMocks;

              class A {

                  @BeforeEach
                  public void setUp() {
                      test1();
                      initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }
              }
              """,
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  private AutoCloseable mocks;

                  @BeforeEach
                  public void setUp() {
                      test1();
                      mocks = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void tearDown() throws Exception {
                      mocks.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void mocksVariableIsAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  String mocks = "mocks";

                  @BeforeEach
                  public void setUp() {
                      test1();
                      MockitoAnnotations.initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }
              }
              """,
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  private AutoCloseable mocks1;

                  String mocks = "mocks";

                  @BeforeEach
                  public void setUp() {
                      test1();
                      mocks1 = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void tearDown() throws Exception {
                      mocks1.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void annotatedAfterEachMethodIsAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  @BeforeEach
                  public void setUp() {
                      test1();
                      MockitoAnnotations.initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void tearDown() throws Exception {
                  }
              }
              """,
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  private AutoCloseable mocks;

                  @BeforeEach
                  public void setUp() {
                      test1();
                      mocks = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void tearDown() throws Exception {
                      mocks.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void annotatedAfterEachMethodIsAlreadyPresentAndNonEmpty() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  @BeforeEach
                  public void init() {
                      test1();
                      MockitoAnnotations.initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void close() throws Exception {
                      test2();
                  }
              }
              """,
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  private AutoCloseable mocks;

                  @BeforeEach
                  public void init() {
                      test1();
                      mocks = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void close() throws Exception {
                      test2();
                      mocks.close();
                  }
              }
              """
          )
        );
    }
    @Test
    void annotatedAfterEachMethodIsPresentButMissingThrowsException() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.mockito.MockitoAnnotations.initMocks;

              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  @BeforeEach
                  public void init() {
                      test1();
                      initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void close() {
                      test2();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockitoAnnotations;

              class A {

                  private AutoCloseable mocks;

                  @BeforeEach
                  public void init() {
                      test1();
                      mocks = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void close() throws Exception {
                      test2();
                      mocks.close();
                  }
              }
              """
          )
        );
    }
    @Test
    void helperInnerClassIsPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  @BeforeEach
                  public void init() {
                      test1();
                      MockitoAnnotations.initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void close() throws Exception {
                      test2();
                  }

                  static class Helper {
                  }
              }
              """,
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              class A {

                  private AutoCloseable mocks;

                  @BeforeEach
                  public void init() {
                      test1();
                      mocks = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterEach
                  void close() throws Exception {
                      test2();
                      mocks.close();
                  }

                  static class Helper {
                  }
              }
              """
          )
        );
    }

    @Test
    void methodWithTearDownIsPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockitoAnnotations;

              import static org.mockito.MockitoAnnotations.initMocks;

              class A {

                  @BeforeEach
                  public void setUp() {
                      test1();
                      initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterAll
                  public static void tearDown() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockitoAnnotations;

              class A {

                  private AutoCloseable mocks;

                  @BeforeEach
                  public void setUp() {
                      test1();
                      mocks = MockitoAnnotations.openMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }

                  @AfterAll
                  public static void tearDown() {
                  }

                  @AfterEach
                  void tearDown0() throws Exception {
                      mocks.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangesWithJunit4() {
        rewriteRun(
          //language=java
          java(
            """
              import org.mockito.MockitoAnnotations;
              import org.junit.Before;

              class A {

                  @Before
                  public void setUp() {
                      test1();
                      MockitoAnnotations.initMocks(this);
                      test2();
                  }

                  public void test1() {
                  }

                  public void test2() {
                  }
              }
              """
          )
        );
    }

}
