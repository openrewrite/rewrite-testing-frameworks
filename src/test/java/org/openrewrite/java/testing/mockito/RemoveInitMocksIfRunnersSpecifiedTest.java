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
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "mockito-core",
                "mockito-junit-jupiter",
                "junit-4",
                "junit-jupiter-api-5"))
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

                  public void setUp() {
                  }

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

    @Test
    void removeOpenMocksInJUnit5() {
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
                      MockitoAnnotations.openMocks(this);
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
    void removeOpenMocksWithStaticImport() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.MockitoAnnotations;

              import static org.mockito.MockitoAnnotations.openMocks;

              @ExtendWith(MockitoExtension.class)
              class A {

                  public void setUp() {
                      openMocks(this);
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

                  public void setUp() {
                  }

                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void notRemoveOpenMocksWithoutRunners() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.MockitoAnnotations;

              class A {

                  public void setUp() {
                      MockitoAnnotations.openMocks(this);
                  }

                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeEmptyIfBlockAfterRemovingCloseMocks() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.MockitoAnnotations;

              @ExtendWith(MockitoExtension.class)
              class A {
                  private AutoCloseable mocks;

                  @BeforeEach
                  public void setUp() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  @AfterEach
                  public void tearDown() throws Exception {
                      if (mocks != null) {
                          mocks.close();
                      }
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
    void removeOpenMocksWithFieldAndClose() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.mockito.MockitoAnnotations;

              @ExtendWith(MockitoExtension.class)
              class A {
                  private AutoCloseable mocks;

                  @BeforeEach
                  public void setUp() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  @AfterEach
                  public void tearDown() throws Exception {
                      mocks.close();
                  }

                  public void test() {
                  }

                  @Nested
                  class NestedTests {
                      @Test
                      public void nestedTestCase() {
                      }

                      @AfterEach
                      public void tearDown() throws Exception {
                          mocks.close();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {

                  public void test() {
                  }

                  @Nested
                  class NestedTests {
                      @Test
                      public void nestedTestCase() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOpenMocksTryWithResourcesInTestMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      try (AutoCloseable autoCloseable = MockitoAnnotations.openMocks(this);) {
                          runnable.run();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      runnable.run();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOpenMocksResourceButKeepOtherResources() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      try (AutoCloseable autoCloseable = MockitoAnnotations.openMocks(this);
                           MockedStatic<Thread> mocked = Mockito.mockStatic(Thread.class)) {
                          runnable.run();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      try (MockedStatic<Thread> mocked = Mockito.mockStatic(Thread.class)) {
                          runnable.run();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapTryIntoIfWithoutBraces() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      if (runnable != null) try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
                          runnable.run();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      if (runnable != null) {
                          runnable.run();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void unwrapTryIntoForLoopWithoutBraces() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      for (int i = 0; i < 3; i++) try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
                          runnable.run();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      for (int i = 0; i < 3; i++) {
                          runnable.run();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepTryWithCatchAfterResourceRemoval() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() {
                      try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
                          runnable.run();
                      } catch (Exception e) {
                          e.printStackTrace();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() {
                      try {
                          runnable.run();
                      } catch (Exception e) {
                          e.printStackTrace();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOpenMocksResourceInMiddlePosition() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      try (MockedStatic<Thread> t = Mockito.mockStatic(Thread.class);
                           AutoCloseable mocks = MockitoAnnotations.openMocks(this);
                           MockedStatic<Runtime> r = Mockito.mockStatic(Runtime.class)) {
                          runnable.run();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      try (MockedStatic<Thread> t = Mockito.mockStatic(Thread.class);
                           MockedStatic<Runtime> r = Mockito.mockStatic(Runtime.class)) {
                          runnable.run();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepBlockWhenInliningWouldCollideWithLaterLocal() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
                          int value = 1;
                          System.out.println(value);
                      }
                      int value = 2;
                      System.out.println(value);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      {
                          int value = 1;
                          System.out.println(value);
                      }
                      int value = 2;
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotRemoveResourceReferencedInBody() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
                          runnable.run();
                          mocks.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepCommentsFromTryAndFirstStatement() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      /* keep me */
                      try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
                          // inner
                          runnable.run();
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Mock
                  Runnable runnable;

                  @Test
                  void test() throws Exception {
                      /* keep me */
                      // inner
                      runnable.run();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveHelperInitializationAndClose() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              @ExtendWith(MockitoExtension.class)
              class HelperInitializationTest {
                  @Mock
                  Runnable managed;
                  final Helper helper = new Helper();
                  AutoCloseable helperMocks;

                  @BeforeEach
                  void setUp() {
                      helperMocks = MockitoAnnotations.openMocks(helper);
                  }

                  @AfterEach
                  void tearDown() throws Exception {
                      helperMocks.close();
                  }

                  @Test
                  void helperIsInitialized() {
                      assertNotNull(managed, "extension initializes the managed instance");
                      assertNotNull(helper.value, "manual initialization of helper must remain");
                  }

                  static class Helper {
                      @Mock
                      Runnable value;
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveInitializationInOrdinaryStaticFixtureConstructor() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              @ExtendWith(MockitoExtension.class)
              class FixtureInitializationTest {
                  @Test
                  void fixtureIsInitialized() {
                      assertNotNull(new Fixture().value);
                  }

                  static class Fixture {
                      @Mock
                      Runnable value;

                      Fixture() {
                          MockitoAnnotations.openMocks(this);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveForeignSameTypeNullComputedAndAmbiguousTargets() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.MockitoAnnotations.initMocks;
              import static org.mockito.MockitoAnnotations.openMocks;

              @ExtendWith(MockitoExtension.class)
              class A {
                  Object helper = new Object();
                  int evaluations;

                  Object nextTarget() {
                      evaluations++;
                      return helper;
                  }

                  void setup(A other) {
                      initMocks(other);
                      openMocks(other);
                      MockitoAnnotations.initMocks(helper);
                      MockitoAnnotations.openMocks(new A());
                      MockitoAnnotations.initMocks(null);
                      MockitoAnnotations.openMocks((A) null);
                      MockitoAnnotations.openMocks(nextTarget());
                      A alias = this;
                      MockitoAnnotations.openMocks(alias);
                      MockitoAnnotations.openMocks((A) this);
                      MockitoAnnotations.openMocks((this));
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveForeignTargetWithJUnit4Runner() {
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.MockitoJUnitRunner;

              @RunWith(MockitoJUnitRunner.class)
              class A {
                  void setup(A other) {
                      MockitoAnnotations.initMocks(other);
                      MockitoAnnotations.openMocks(other);
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveOrdinaryMemberLocalAndAnonymousInstances() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  class Fixture {
                      Fixture() {
                          MockitoAnnotations.initMocks(this);
                      }
                  }

                  void setup() {
                      class LocalFixture {
                          LocalFixture() {
                              MockitoAnnotations.openMocks(this);
                          }
                      }
                      new LocalFixture();
                      Runnable callback = new Runnable() {
                          @Override
                          public void run() {
                              MockitoAnnotations.openMocks(this);
                          }
                      };
                      callback.run();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveNestedBelowUnmanagedHelperAndJUnit4Runner() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  class Helper {
                      @Nested
                      class NestedFixture {
                          void setup() {
                              MockitoAnnotations.openMocks(this);
                          }
                      }
                  }
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.runner.RunWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.MockitoJUnitRunner;

              @RunWith(MockitoJUnitRunner.class)
              class B {
                  @Nested
                  class NestedFixture {
                      void setup() {
                          MockitoAnnotations.initMocks(this);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOnlyManagedTargetAndKeepLambdaLexicalThis() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  void setup(Object helper) {
                      MockitoAnnotations.openMocks(this);
                      MockitoAnnotations.openMocks(helper);
                      Runnable initialize = () -> {
                          MockitoAnnotations.initMocks(this);
                      };
                      initialize.run();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  void setup(Object helper) {
                      MockitoAnnotations.openMocks(helper);
                      Runnable initialize = () -> {
                      };
                      initialize.run();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeManagedNestedChainAndOwnAnnotatedStaticClass() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Nested
                  class Inner {
                      @Nested
                      class Deep {
                          void setup() {
                              MockitoAnnotations.openMocks(this);
                          }
                      }
                  }

                  @ExtendWith(MockitoExtension.class)
                  static class Independent {
                      void setup() {
                          MockitoAnnotations.initMocks(this);
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  @Nested
                  class Inner {
                      @Nested
                      class Deep {
                          void setup() {
                          }
                      }
                  }

                  @ExtendWith(MockitoExtension.class)
                  static class Independent {
                      void setup() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepForeignResourceAndRemoveOnlyManagedResource() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  void test(Object helper) throws Exception {
                      try (AutoCloseable managed = MockitoAnnotations.openMocks(this);
                           AutoCloseable foreign = MockitoAnnotations.openMocks(helper)) {
                          System.out.println("body");
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  void test(Object helper) throws Exception {
                      try (AutoCloseable foreign = MockitoAnnotations.openMocks(helper)) {
                          System.out.println("body");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveComputedAndNullResourceTargets() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  int evaluations;

                  Object nextTarget() {
                      evaluations++;
                      return null;
                  }

                  void test() throws Exception {
                      try (AutoCloseable mocks = MockitoAnnotations.openMocks(nextTarget())) {
                          System.out.println("body");
                      }
                      try (AutoCloseable mocks = MockitoAnnotations.openMocks(null)) {
                          System.out.println("body");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void keepForeignHandleInMixedDeclaration() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  Object helper = new Object();
                  AutoCloseable managed, foreign;

                  @BeforeEach
                  void setup() {
                      managed = MockitoAnnotations.openMocks(this);
                      foreign = MockitoAnnotations.openMocks(helper);
                  }

                  @AfterEach
                  void teardown() throws Exception {
                      managed.close();
                      foreign.close();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  Object helper = new Object();
                  AutoCloseable foreign;

                  @BeforeEach
                  void setup() {
                      foreign = MockitoAnnotations.openMocks(helper);
                  }

                  @AfterEach
                  void teardown() throws Exception {
                      foreign.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveSharedHandleAndForeignOwnerWithSameFieldName() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  AutoCloseable mocks;

                  void setup(Object other) throws Exception {
                      mocks = MockitoAnnotations.openMocks(this);
                      mocks.close();
                      mocks = MockitoAnnotations.openMocks(other);
                  }

                  void teardown() throws Exception {
                      mocks.close();
                  }

                  class Fixture {
                      AutoCloseable mocks;

                      void setup() {
                          mocks = MockitoAnnotations.openMocks(this);
                      }

                      void teardown() throws Exception {
                          mocks.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveEscapingManagedHandle() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class A {
                  AutoCloseable mocks;

                  void setup() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  AutoCloseable handle() {
                      return mocks;
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveFormalParameterWhenRemovingManagedInitialization() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class ParameterHandleTest {
                  void initialize(AutoCloseable mocks) throws Exception {
                      mocks = MockitoAnnotations.openMocks(this);
                      mocks.close();
                  }

                  @Test
                  void works() throws Exception {
                      initialize(null);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class ParameterHandleTest {
                  void initialize(AutoCloseable mocks) throws Exception {
                  }

                  @Test
                  void works() throws Exception {
                      initialize(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveManagedResourceUsedByAnotherResource() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class DependentResourceTest {
                  @Test
                  void works() throws Exception {
                      try (AutoCloseable managed = MockitoAnnotations.openMocks(this);
                           AutoCloseable dependent = managed) {
                          System.out.println("body");
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveHandleReadByAnotherTopLevelClass() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              @ExtendWith(MockitoExtension.class)
              class ExternalHandleTest {
                  AutoCloseable mocks;

                  @BeforeEach
                  void setup() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  @AfterEach
                  void teardown() throws Exception {
                      mocks.close();
                  }

                  @Test
                  void handleRemainsObservable() {
                      assertNotNull(new HandleReader().read(this));
                  }
              }

              class HandleReader {
                  AutoCloseable read(ExternalHandleTest owner) {
                      return owner.mocks;
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveHandleReadInAnotherSourceFile() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              @ExtendWith(MockitoExtension.class)
              class ExternalHandleTest {
                  AutoCloseable mocks;

                  @BeforeEach
                  void setup() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  @AfterEach
                  void teardown() throws Exception {
                      mocks.close();
                  }

                  @Test
                  void handleRemainsObservable() {
                      assertNotNull(new HandleReader().read(this));
                  }
              }
              """
          ),
          java(
            """
              class HandleReader {
                  AutoCloseable read(ExternalHandleTest owner) {
                      return owner.mocks;
                  }
              }
              """
          )
        );
    }

    @Test
    void preservePrivateNestedHandleReadByEnclosingClass() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              class Container {
                  @ExtendWith(MockitoExtension.class)
                  static class ManagedOwner {
                      private AutoCloseable mocks;

                      void setup() {
                          mocks = MockitoAnnotations.openMocks(this);
                      }

                      void teardown() throws Exception {
                          mocks.close();
                      }
                  }

                  AutoCloseable read(ManagedOwner owner) {
                      return owner.mocks;
                  }
              }
              """
          )
        );
    }

    @Test
    void preserveInheritedHandleDeclaredInAnotherSourceFile() {
        rewriteRun(
          java(
            """
              class Parent {
                  protected AutoCloseable mocks;
              }
              """
          ),
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class Child extends Parent {
                  void setup() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  void teardown() throws Exception {
                      mocks.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void unrelatedFieldWithSameNameDoesNotPreventCleanup() {
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.MockitoAnnotations;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class ManagedOwner {
                  private AutoCloseable mocks;

                  void setup() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  void teardown() throws Exception {
                      mocks.close();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class ManagedOwner {

                  void setup() {
                  }

                  void teardown() throws Exception {
                  }
              }
              """
          ),
          java(
            """
              class UnrelatedOwner {
                  AutoCloseable mocks;

                  AutoCloseable read() {
                      return mocks;
                  }
              }
              """
          )
        );
    }
}
