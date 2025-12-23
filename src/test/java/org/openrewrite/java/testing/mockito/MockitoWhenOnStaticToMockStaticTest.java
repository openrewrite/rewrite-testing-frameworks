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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class MockitoWhenOnStaticToMockStaticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MockitoWhenOnStaticToMockStatic())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "junit-jupiter-api-5",
              "mockito-core-3.12",
              "mockito-junit-jupiter-3.12",
              "testng"
            )
            //language=java
            .dependsOn(
              """
                package org.example;
                public class A {
                    public static Integer getNumber() {
                        return 42;
                    }
                }
                """,
              """
                package org.example;
                public class B {
                    public static String getString() {
                        return "";
                    }
                    public String getStringNonStatic() {
                        return "non-static";
                    }
                }
                """
            ))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5",
              "mockito-core-5"
            ))
          // Known limitation with: /*~~(Identifier type is missing or malformed)~~>*/A
          .afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build())
        ;
    }

    @DocumentExample
    @Test
    void shouldRefactorMockito_When() {
        rewriteRun(
          //language=java
          java(
            """
              import org.example.A;

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
              import org.example.A;
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
    void shouldOptForUsageOfMockedStatic_WhenAlreadyScopedInside() {
        rewriteRun(
          //language=java
          java(
            """
              import org.example.A;
              import org.example.B;
              import org.mockito.MockedStatic;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      try (MockedStatic<A> mockA = mockStatic(A.class)) {
                          try (MockedStatic<B> mockB = mockStatic(B.class)) {
                              when(A.getNumber()).thenReturn(-1);
                              when(B.getString()).thenReturn("hi there");
                              assertEquals(A.getNumber(), -1);
                              assertEquals(B.getString(), "hi there");
                          }
                      }
                  }
              }
              """,
            """
              import org.example.A;
              import org.example.B;
              import org.mockito.MockedStatic;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  void test() {
                      try (MockedStatic<A> mockA = mockStatic(A.class)) {
                          try (MockedStatic<B> mockB = mockStatic(B.class)) {
                              mockA.when(() -> A.getNumber()).thenReturn(-1);
                              mockB.when(() -> B.getString()).thenReturn("hi there");
                              assertEquals(A.getNumber(), -1);
                              assertEquals(B.getString(), "hi there");
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotConvertIfScopeOfChangeWouldHaveToBeBroadened() {
        rewriteRun(
          //language=java
          java(
            """
              import org.example.B;
              import org.mockito.MockedConstruction;
              import org.mockito.MockedStatic;

              import static org.mockito.Mockito.*;

              class Test {
                  void method() {
                      // Would have to be changed starting here
                      try (MockedConstruction<B> bMockConstruction = mockConstruction(B.class,
                          (mock, context) -> {
                              // Rather than just here
                              when(mock.getString()).thenReturn("first");
                          }
                      )) {
                          // Nothing here
                      }
                      // Doesn't require a change
                      try (MockedConstruction<B> bMockConstruction = mockConstruction(B.class,
                          (mock, context) -> {
                              when(mock.getStringNonStatic()).thenReturn("second");
                          }
                      )) {
                          // Nothing here
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleMultipleStaticMocksAndNestedStatements() {
        rewriteRun(
          //language=java
          java(
            """
              import org.example.A;

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
              import org.example.A;
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
    void handlesNamingAfterMethodSafely() {
        //language=java
        rewriteRun(
          java(
            """
              import org.example.A;
              import org.junit.Before;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  @Before
                  public void setUp() {
                      when(A.getNumber()).thenReturn(-1);
                  }

                  void tearDown() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """,
            """
              import org.example.A;
              import org.junit.After;
              import org.junit.Before;
              import org.mockito.MockedStatic;

              import static org.junit.Assert.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  private MockedStatic<A> mockA1;

                  @Before
                  public void setUp() {
                      mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                  }

                  @After
                  public void tearDown1() {
                      mockA1.close();
                  }

                  void tearDown() {
                      assertEquals(A.getNumber(), -1);
                  }
              }
              """
          )
        );
    }

    @Nested
    class UsingJunit4 {
        @Test
        void handlesStaticMocks_inBefore() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.example.A;
                  import org.junit.Before;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

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
                  import org.example.A;
                  import org.junit.After;
                  import org.junit.Before;
                  import org.mockito.MockedStatic;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

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
        void handlesStaticMocks_inBefore_withExistingAfter() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.example.A;
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
                  import org.example.A;
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
        void handlesStaticMocks_InBeforeClass() {
            //language=java
            rewriteRun(
              java(
                """
                  import org.example.A;
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
                  import org.example.A;
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
        void handlesStaticMocks_inBeforeClass_withExistingAfterClass() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.AfterClass;
                  import org.junit.BeforeClass;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

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
                  import org.example.A;
                  import org.junit.AfterClass;
                  import org.junit.BeforeClass;
                  import org.mockito.MockedStatic;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

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
        void usingBothBeforeClassAndAfter_attachesCloseToCorrectMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.After;
                  import org.junit.BeforeClass;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @After
                      public void blah() {
                          System.out.println("some statement");
                      }

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
                  import org.example.A;
                  import org.junit.After;
                  import org.junit.AfterClass;
                  import org.junit.BeforeClass;
                  import org.mockito.MockedStatic;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private static MockedStatic<A> mockA1;

                      @After
                      public void blah() {
                          System.out.println("some statement");
                      }

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
        void usingBothBeforeAndAfterClass_attachesCloseToCorrectMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.AfterClass;
                  import org.junit.Before;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @AfterClass
                      public static void blah() {
                          System.out.println("some statement");
                      }

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
                  import org.example.A;
                  import org.junit.After;
                  import org.junit.AfterClass;
                  import org.junit.Before;
                  import org.mockito.MockedStatic;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<A> mockA1;

                      @AfterClass
                      public static void blah() {
                          System.out.println("some statement");
                      }

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
        void usesLambda_whenStaticMockIsAssignedAlready() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.AfterClass;
                  import org.junit.BeforeClass;
                  import org.mockito.MockedStatic;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<String> mockedString;
                      private MockedStatic<A> mockA1;
                      private MockedStatic<Boolean> mockedBoolean;

                      @BeforeClass
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockedString = mockStatic(String.class);
                          mockedBoolean = mockStatic(Boolean.class);
                      }

                      @AfterClass
                      public static void tearDown() {
                          mockA1.close();
                          mockedString.close();
                          mockedBoolean.close();
                      }

                      void test() {
                          when(A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.AfterClass;
                  import org.junit.BeforeClass;
                  import org.mockito.MockedStatic;

                  import static org.junit.Assert.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<String> mockedString;
                      private MockedStatic<A> mockA1;
                      private MockedStatic<Boolean> mockedBoolean;

                      @BeforeClass
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockedString = mockStatic(String.class);
                          mockedBoolean = mockStatic(Boolean.class);
                      }

                      @AfterClass
                      public static void tearDown() {
                          mockA1.close();
                          mockedString.close();
                          mockedBoolean.close();
                      }

                      void test() {
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class UsingJunit5 {
        @Test
        void handlesStaticMocks_inBeforeEach() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.jupiter.api.BeforeEach;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @BeforeEach
                      public void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      void test1() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterEach;
                  import org.junit.jupiter.api.BeforeEach;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<A> mockA1;

                      @BeforeEach
                      public void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterEach
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
        void handlesStaticMocks_inBeforeEach_withExistingAfterEach() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterEach;
                  import org.junit.jupiter.api.BeforeEach;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @BeforeEach
                      public void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      @AfterEach
                      public void tearDown() {
                          System.out.println("some statement");
                      }

                      void test() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterEach;
                  import org.junit.jupiter.api.BeforeEach;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<A> mockA1;

                      @BeforeEach
                      public void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterEach
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
        void handlesStaticMocks_InBeforeAll() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.jupiter.api.BeforeAll;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @BeforeAll
                      public static void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      void test1() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.BeforeAll;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private static MockedStatic<A> mockA1;

                      @BeforeAll
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterAll
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
        void handlesStaticMocks_inBeforeAll_withExistingAfterAll() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.BeforeAll;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @BeforeAll
                      public static void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      @AfterAll
                      public static void tearDown() {
                          System.out.println("some statement");
                      }

                      void test() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.BeforeAll;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private static MockedStatic<A> mockA1;

                      @BeforeAll
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterAll
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
        void usingBothBeforeAllAndAfterEach_attachesCloseToCorrectMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterEach;
                  import org.junit.jupiter.api.BeforeAll;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @AfterEach
                      public void blah() {
                          System.out.println("some statement");
                      }

                      @BeforeAll
                      public static void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      void test1() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.AfterEach;
                  import org.junit.jupiter.api.BeforeAll;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private static MockedStatic<A> mockA1;

                      @AfterEach
                      public void blah() {
                          System.out.println("some statement");
                      }

                      @BeforeAll
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterAll
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
        void usingBothBeforeEachAndAfterAll_attachesCloseToCorrectMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.BeforeEach;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      @AfterAll
                      public static void blah() {
                          System.out.println("some statement");
                      }

                      @BeforeEach
                      public void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      void test1() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.AfterEach;
                  import org.junit.jupiter.api.BeforeEach;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<A> mockA1;

                      @AfterAll
                      public static void blah() {
                          System.out.println("some statement");
                      }

                      @BeforeEach
                      public void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterEach
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
        void usesLambda_whenStaticMockIsAssignedAlready() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.BeforeAll;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<String> mockedString;
                      private MockedStatic<A> mockA1;
                      private MockedStatic<Boolean> mockedBoolean;

                      @BeforeAll
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockedString = mockStatic(String.class);
                          mockedBoolean = mockStatic(Boolean.class);
                      }

                      @AfterAll
                      public static void tearDown() {
                          mockA1.close();
                          mockedString.close();
                          mockedBoolean.close();
                      }

                      void test() {
                          when(A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.junit.jupiter.api.AfterAll;
                  import org.junit.jupiter.api.BeforeAll;
                  import org.mockito.MockedStatic;

                  import static org.junit.jupiter.api.Assertions.assertEquals;
                  import static org.mockito.Mockito.*;

                  class Test {
                      private MockedStatic<String> mockedString;
                      private MockedStatic<A> mockA1;
                      private MockedStatic<Boolean> mockedBoolean;

                      @BeforeAll
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockedString = mockStatic(String.class);
                          mockedBoolean = mockStatic(Boolean.class);
                      }

                      @AfterAll
                      public static void tearDown() {
                          mockA1.close();
                          mockedString.close();
                          mockedBoolean.close();
                      }

                      void test() {
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """
              )
            );
        }
    }

    @Nested
    class UsingTestng {
        @Test
        void handlesStaticMocks_inBeforeMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.testng.annotations.BeforeMethod;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      @BeforeMethod
                      public void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      void test1() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterMethod;
                  import org.testng.annotations.BeforeMethod;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      private MockedStatic<A> mockA1;

                      @BeforeMethod
                      public void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterMethod
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
        void handlesStaticMocks_inBeforeMethod_withExistingAfter() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.testng.annotations.AfterMethod;
                  import org.testng.annotations.BeforeMethod;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      @BeforeMethod
                      public void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      @AfterMethod
                      public void tearDown() {
                          System.out.println("some statement");
                      }

                      void test() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterMethod;
                  import org.testng.annotations.BeforeMethod;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      private MockedStatic<A> mockA1;

                      @BeforeMethod
                      public void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterMethod
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
        void handlesStaticMocks_inBeforeClass() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

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
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

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

        // Similar situations:
        // Existing `@BeforeMethod` and `@AfterClass`
        // Possibly something including `@BeforeTest`/`@AfterTest`, `@BeforeSuite`/`@AfterSuite`, `@BeforeGroups`/`@AfterGroups`
        @Test
        void usingBothBeforeClassAndAfterMethod_attachesCloseToCorrectMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.testng.annotations.AfterMethod;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      @BeforeClass
                      public static void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      @AfterMethod
                      public void blah() {
                          System.out.println("some statement");
                      }

                      void test1() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.AfterMethod;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

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

                      @AfterMethod
                      public void blah() {
                          System.out.println("some statement");
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
        void usingBothBeforeMethodAndAfterClass_attachesCloseToCorrectMethod() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.BeforeMethod;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      @AfterClass
                      public static void blah() {
                          System.out.println("some statement");
                      }

                      @BeforeMethod
                      public void setUp() {
                          when(A.getNumber()).thenReturn(-1);
                      }

                      void test1() {
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.AfterMethod;
                  import org.testng.annotations.BeforeMethod;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      private MockedStatic<A> mockA1;

                      @AfterClass
                      public static void blah() {
                          System.out.println("some statement");
                      }

                      @BeforeMethod
                      public void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      }

                      @AfterMethod
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
        void handlesStaticMocks_inBeforeClass_withExistingAfterClass() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

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
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

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
        void usesLambda_whenStaticMockIsAssignedAlready() {
            rewriteRun(
              //language=java
              java(
                """
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      private MockedStatic<String> mockedString;
                      private MockedStatic<A> mockA1;
                      private MockedStatic<Boolean> mockedBoolean;

                      @BeforeClass
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockedString = mockStatic(String.class);
                          mockedBoolean = mockStatic(Boolean.class);
                      }

                      @AfterClass
                      public static void tearDown() {
                          mockA1.close();
                          mockedString.close();
                          mockedBoolean.close();
                      }

                      void test() {
                          when(A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """,
                """
                  import org.example.A;
                  import org.mockito.MockedStatic;
                  import org.testng.annotations.AfterClass;
                  import org.testng.annotations.BeforeClass;

                  import static org.mockito.Mockito.*;
                  import static org.testng.Assert.assertEquals;

                  class Test {
                      private MockedStatic<String> mockedString;
                      private MockedStatic<A> mockA1;
                      private MockedStatic<Boolean> mockedBoolean;

                      @BeforeClass
                      public static void setUp() {
                          mockA1 = mockStatic(A.class);
                          mockedString = mockStatic(String.class);
                          mockedBoolean = mockStatic(Boolean.class);
                      }

                      @AfterClass
                      public static void tearDown() {
                          mockA1.close();
                          mockedString.close();
                          mockedBoolean.close();
                      }

                      void test() {
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }
                  """
              )
            );
        }
    }

    @Test
    void usesLambda_whenStaticMockIsAssignedAlready_inSameBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.example.A;
              import org.mockito.MockedStatic;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  void test() {
                      MockedStatic<A> mockA1 = mockStatic(A.class);
                      when(A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);
                      mockA1.close();
                  }
              }
              """,
            """
              import org.example.A;
              import org.mockito.MockedStatic;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  void test() {
                      MockedStatic<A> mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);
                      mockA1.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void refactors_whenStaticMockIsAssignedInAnotherBlock() {
        rewriteRun(
          //language=java
          java(
            """
              import org.example.A;
              import org.mockito.MockedStatic;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  void test() {
                      when(A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);
                  }

                  void anotherTest() {
                      MockedStatic<A> mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);
                      mockA1.close();
                  }
              }
              """,
            """
              import org.example.A;
              import org.mockito.MockedStatic;

              import static org.mockito.Mockito.*;
              import static org.junit.Assert.assertEquals;

              class Test {
                  void test() {
                      try (MockedStatic<A> mockA1 = mockStatic(A.class)) {
                          mockA1.when(() -> A.getNumber()).thenReturn(-1);
                          assertEquals(A.getNumber(), -1);
                      }
                  }

                  void anotherTest() {
                      MockedStatic<A> mockA1 = mockStatic(A.class);
                      mockA1.when(() -> A.getNumber()).thenReturn(-1);
                      assertEquals(A.getNumber(), -1);
                      mockA1.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldNotRefactorMockito_WhenMockIsAssigned() {
        rewriteRun(
          //language=java
          java(
            """
              import org.example.A;
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

    @Test
    void shouldNotModifyKotlinFilesUsingMockitoWhen() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=kotlin
          kotlin(
            """
              import org.junit.jupiter.api.Test
              import org.mockito.Mockito.`when`
              import org.mockito.Mockito.mock

              class MyTest {
                  @Test
                  fun testSomething() {
                      val mockList = mock(MutableList::class.java)
                      `when`(mockList.size).thenReturn(100)
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldRefactorKotlinMockitoWhenOnStaticMethod() {
        rewriteRun(
          spec -> spec
            .typeValidationOptions(TypeValidation.all().methodInvocations(false)),
          //language=kotlin
          kotlin(
            """
              import org.junit.jupiter.api.Test
              import org.mockito.MockedStatic
              import org.mockito.Mockito.mock
              import org.mockito.Mockito.mockStatic
              import java.util.Calendar

              class MyTest {
                  @Test
                  fun testStaticMethod() {
                      val calendarMock: Calendar = mock(Calendar::class.java)
                      mockStatic(Calendar::class.java).use { mockA1 ->
                          mockA1.`when`<Calendar>(Calendar::getInstance).thenReturn(calendarMock)
                      }
                  }
              }
              """
          )
        );
    }
}
