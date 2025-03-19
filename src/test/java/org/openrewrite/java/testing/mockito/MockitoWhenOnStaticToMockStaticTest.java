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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.*;

import static org.openrewrite.java.Assertions.java;

class MockitoWhenOnStaticToMockStaticTest implements RewriteTest {

    //language=java
    public static final SourceSpecs CLASS_A = java(
      """
      public class A {
          public static Integer getNumber() {
              return 42;
         }
      }
      """,
      SourceSpec::skip
    );

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MockitoWhenOnStaticToMockStatic())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "mockito-core-3.12",
              "mockito-junit-jupiter-3.12"
            ));
    }

    @DocumentExample
    @Test
    void shouldRefactorMockito_When() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      System.out.println("some statement");
                      when(A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """,
            """
              import org.mockito.MockedStatic;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      System.out.println("some statement");
                      try (MockedStatic<A> mockA1 = mockStatic(A.class)) {
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleMultipleStaticMocksAndNestedStatements() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      when(A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);

                      when(A.getNumber()).thenReturn(-2);
                      assertEquals(A.getNumber(), -2);

                      if (true) {
                          when(A.getNumber()).thenReturn(-3);
                          assertEquals(A.getNumber(), -3);

                          when(A.getNumber()).thenReturn(-4);
                          assertEquals(A.getNumber(), -4);
                      }

                      assertEquals(A.getNumber(), -2);
                  }
              }
              """,
            """
              import org.mockito.MockedStatic;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      try (MockedStatic<A> mockA1 = mockStatic(A.class)) {
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);

                          try (MockedStatic<A> mockA2 = mockStatic(A.class)) {
                              mockA2.when(() -> A.getNumber()).thenReturn(-2);
                              assertEquals(A.getNumber(), -2);

                              if (true) {
                                  try (MockedStatic<A> mockA3 = mockStatic(A.class)) {
                                      mockA3.when(() -> A.getNumber()).thenReturn(-3);
                                      assertEquals(A.getNumber(), -3);

                                      try (MockedStatic<A> mockA4 = mockStatic(A.class)) {
                                          mockA4.when(() -> A.getNumber()).thenReturn(-4);
                                          assertEquals(A.getNumber(), -4);
                                      }
                                  }
                              }

                              assertEquals(A.getNumber(), -2);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleStaticMocksInBefore() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.junit.Before;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  @Before
                  public void setUp() {
                      when(A.getNumber()).thenReturn(-1);
                  }

                  void test1() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Before;
              import org.mockito.MockedStatic;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  private MockedStatic<A> mockA1;

                  @Before
                  public void setUp() {
                      mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                  }

                  @After
                  public void tearDown() {
                      mockA1.close();
                  }

                  void test1() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleStaticMocksInBeforeWithExistingAfter() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.junit.Before;
              import org.junit.After;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  @Before
                  public void setUp() {
                      when(A.getNumber()).thenReturn(-1);
                  }

                  @After
                  public void tearDown() {
                      System.out.println("some statement");
                  }

                  void test() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.mockito.MockedStatic;
              import org.junit.After;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  private MockedStatic<A> mockA1;

                  @Before
                  public void setUp() {
                      mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                  }

                  @After
                  public void tearDown() {
                      System.out.println("some statement");
                      mockA1.close();
                  }

                  void test() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleStaticMocksInBeforeClas() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.junit.BeforeClass;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  @BeforeClass
                  public static void setUp() {
                      when(A.getNumber()).thenReturn(-1);
                  }

                  void test1() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """,
            """
              import org.junit.AfterClass;
              import org.junit.BeforeClass;
              import org.mockito.MockedStatic;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  private static MockedStatic<A> mockA1;

                  @BeforeClass
                  public static void setUp() {
                      mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                  }

                  @AfterClass
                  public static void tearDown() {
                      mockA1.close();
                  }

                  void test1() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleStaticMocksInBeforeClasWithExistingAfterClass() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.junit.BeforeClass;
              import org.junit.AfterClass;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  @BeforeClass
                  public static void setUp() {
                      when(A.getNumber()).thenReturn(-1);
                  }

                  @AfterClass
                  public static void tearDown() {
                      System.out.println("some statement");
                  }

                  void test() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """,
            """
              import org.junit.BeforeClass;
              import org.mockito.MockedStatic;
              import org.junit.AfterClass;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  private static MockedStatic<A> mockA1;

                  @BeforeClass
                  public static void setUp() {
                      mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                  }

                  @AfterClass
                  public static void tearDown() {
                      System.out.println("some statement");
                      mockA1.close();
                  }

                  void test() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRefactorMockito_WhenMockIsAssigned() {
        //language=java
        rewriteRun(
          CLASS_A,
          java(
            """
              import org.junit.Before;
              import org.mockito.stubbing.OngoingStubbing;
              import static org.mockito.Mockito.*;

              class Test {
                  OngoingStubbing<Integer> x = null;

                  @Before
                  public void setUp() {
                      x = when(A.getNumber()).thenReturn(1);
                  }

                  void test1() { x.thenReturn(2); }
                  void test2() { x.thenReturn(3); }
              }
              """
          )
        );
    }
}
