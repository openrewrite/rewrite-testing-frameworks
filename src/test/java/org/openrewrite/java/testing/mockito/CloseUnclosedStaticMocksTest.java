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

class CloseUnclosedStaticMocksTest implements RewriteTest {

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

    public static final SourceSpecs CLASS_B = java(
      """
      public class B {
          public static Long getLong() {
              return 42L;
         }
      }
      """,
      SourceSpec::skip
    );

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CloseUnclosedStaticMocks())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "mockito-core-5"));
    }

    @DocumentExample
    @Test
    void shouldWrapMockStaticInTryWithResources() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockStatic;

              class Test {
                  void test() {
                      mockStatic(A.class);
                      assertEquals(A.getNumber(), 42);
                  }
              }
              """,
            """
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockStatic;

              class Test {
                  void test() {
                      try (MockedStatic<A> mockedStaticA = mockStatic(A.class)) {
                          assertEquals(A.getNumber(), 42);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldWrapMockStaticInTryWithResourcesVarDecl() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockStatic;

              class Test {
                  void test() {
                      MockedStatic<A> mocked = mockStatic(A.class);
                      assertEquals(A.getNumber(), 42);
                  }
              }
              """,
            """
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockStatic;

              class Test {
                  void test() {
                      try (MockedStatic<A> mocked = mockStatic(A.class)) {
                          assertEquals(A.getNumber(), 42);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldWrapMockStaticInTryWithResourcesMultipleMocks() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          CLASS_B,
          java(
            """
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockStatic;

              class Test {
                  void test() {
                      MockedStatic<A> mocked = mockStatic(A.class);
                      assertEquals(A.getNumber(), 42);
                      mockStatic(B.class);
                      assertEquals(B.getLong(), 42L);
                  }
              }
              """,
            """
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.mockStatic;

              class Test {
                  void test() {
                      try (MockedStatic<A> mocked = mockStatic(A.class))
                      {
                          assertEquals(A.getNumber(), 42);
                          try (MockedStatic<B> mockedStaticB = mockStatic(B.class)) {
                              assertEquals(B.getLong(), 42L);
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldAddClassVariableForBeforeEach() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  @BeforeEach
                  public void setUp() {
                      mockStatic(A.class);
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  private MockedStatic<A> mockedStaticA;
                  @BeforeEach
                  public void setUp() {
                      mockedStaticA = mockStatic(A.class);
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                  }

                  @AfterEach
                  public void tearDown() {
                      mockedStaticA.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldAddClassVariableForBeforeAll() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.junit.jupiter.api.BeforeAll;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  @BeforeAll
                  public static void setUp() {
                      mockStatic(A.class);
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeAll;
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  private static MockedStatic<A> mockedStaticA;
                  @BeforeAll
                  public static void setUp() {
                      mockedStaticA = mockStatic(A.class);
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                  }

                  @AfterAll
                  public static void tearDown() {
                      mockedStaticA.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldAddCloseToExistingAfterEach() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  @BeforeEach
                  public void setUp() {
                      mockStatic(A.class);
                  }

                  @AfterEach
                  public void tearDown() {
                      System.out.println("Cleaning up");
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockedStatic;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  private MockedStatic<A> mockedStaticA;
                  @BeforeEach
                  public void setUp() {
                      mockedStaticA = mockStatic(A.class);
                  }

                  @AfterEach
                  public void tearDown() {
                      System.out.println("Cleaning up");
                      mockedStaticA.close();
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                  }
              }
              """
          )
        );
    }

    @Test
    void shouldHandleExistingMockedStaticVariables() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          CLASS_B,
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockedStatic;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  private MockedStatic<A> mockA;

                  @BeforeEach
                  public void setUp() {
                      mockA = mockStatic(A.class);
                      MockedStatic<B> mockB = mockStatic(B.class);
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                      assertEquals(B.getLong(), 42L);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockedStatic;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  private MockedStatic<B> mockB;
                  private MockedStatic<A> mockA;

                  @BeforeEach
                  public void setUp() {
                      mockA = mockStatic(A.class);
                      mockB = mockStatic(B.class);
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                      assertEquals(B.getLong(), 42L);
                  }

                  @AfterEach
                  public void tearDown() {
                      mockA.close();
                      mockB.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void alreadyClosed() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          CLASS_A,
          CLASS_B,
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockedStatic;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              class Test {
                  private MockedStatic<A> mockA;
                  private MockedStatic<B> mockB;

                  @BeforeEach
                  public void setUp() {
                      mockA = mockStatic(A.class);
                      mockB = mockStatic(B.class);
                  }

                  void test() {
                      assertEquals(A.getNumber(), 42);
                      assertEquals(B.getLong(), 42L);
                  }

                  @AfterEach
                  public void tearDown() {
                      mockA.close();
                      mockB.close();
                  }
              }
              """
          )
        );
    }
}
