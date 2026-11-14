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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddMissingTestAnnotationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5",
              "assertj-core-3"))
          .recipe(new AddMissingTestAnnotation(true));
    }

    @DocumentExample
    @Test
    void addsTestAnnotationWhenNameStartsWithTestAndHasAssertions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertEquals(1, 1);
                  }

                  void testAddition() {
                      int result = 2 + 3;
                      Assertions.assertEquals(5, result);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertEquals(1, 1);
                  }

                  @Test
                  void testAddition() {
                      int result = 2 + 3;
                      Assertions.assertEquals(5, result);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyMethodWithoutTestPrefixWithAssertions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertTrue(true);
                  }

                  // A method without a "test" prefix should not be annotated even if it has assertions
                  void additionWorks() {
                      Assertions.assertEquals(5, 2 + 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyMethodWithTestPrefixWithoutAssertions() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertEquals(1, 1);
                  }

                  // A method starting with "test" should not be annotated if it has no assertions
                  void testDataSetup() {
                      setupDatabase();
                  }

                  void setupDatabase() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyMethodWithExistingTestAnnotation() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void testAlreadyAnnotated() {
                      Assertions.assertEquals(1, 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyHelperMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void testSomething() {
                      Assertions.assertEquals(1, 1);
                  }

                  void helperMethod() {
                      System.out.println("I am a helper");
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyPrivateMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void testSomething() {
                      Assertions.assertEquals(1, 1);
                  }

                  private void testPrivateHelper() {
                      Assertions.assertEquals(2, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyStaticMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void testSomething() {
                      Assertions.assertEquals(1, 1);
                  }

                  static void testStaticHelper() {
                      Assertions.assertEquals(2, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyMethodWithParameters() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void testSomething() {
                      Assertions.assertEquals(1, 1);
                  }

                  void testWithParam(String input) {
                      Assertions.assertNotNull(input);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyNonVoidMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class MyTest {
                  @Test
                  void testSomething() {
                  }

                  int testReturnsSomething() {
                      return 42;
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyLifecycleMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.BeforeEach;

              class MyTest {
                  @Test
                  void testSomething() {
                  }

                  @BeforeEach
                  void testSetUp() {
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyNonTestClass() {
        //language=java
        rewriteRun(
          java(
            """
              class NotATestClass {
                  void testSomething() {
                      System.out.println("not a test");
                  }
              }
              """
          )
        );
    }

    @Test
    void flagOnlyModeDoesNotAddAnnotation() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new AddMissingTestAnnotation(false)),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertEquals(1, 1);
                  }

                  void testAddition() {
                      Assertions.assertEquals(5, 2 + 3);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertEquals(1, 1);
                  }

                  /*~~(Missing @Test annotation)~~>*/void testAddition() {
                      Assertions.assertEquals(5, 2 + 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyEmptyMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class MyTest {
                  @Test
                  void existingTest() {
                  }

                  void testEmpty() {
                  }
              }
              """
          )
        );
    }

    @Test
    void addsJUnit4TestAnnotationWhenClassUsesJUnit4() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4")),
          java(
            """
              import org.junit.Assert;
              import org.junit.Test;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assert.assertEquals(1, 1);
                  }

                  void testAddition() {
                      Assert.assertEquals(5, 2 + 3);
                  }
              }
              """,
            """
              import org.junit.Assert;
              import org.junit.Test;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assert.assertEquals(1, 1);
                  }

                  @Test
                  void testAddition() {
                      Assert.assertEquals(5, 2 + 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyClassWithBothJUnit4AndJUnit5Tests() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "junit-jupiter-api-5")),
          java(
            """
              import org.junit.Assert;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @org.junit.Test
                  void junit4Test() {
                      Assert.assertEquals(1, 1);
                  }

                  @org.junit.jupiter.api.Test
                  void junit5Test() {
                      Assertions.assertEquals(1, 1);
                  }

                  // Not annotated because class uses both JUnit 4 and JUnit 5, making target framework ambiguous
                  void testAddition() {
                      Assertions.assertEquals(5, 2 + 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsTestAnnotationForAssertJAssertions() {
        // Unannotated method in a JUnit 5 class using AssertJ assertThat() gets @Test
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void existingTest() {
                      assertThat(1).isEqualTo(1);
                  }

                  void testAssertJ() {
                      assertThat(2 + 3).isEqualTo(5);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assertions.assertThat;

              class MyTest {
                  @Test
                  void existingTest() {
                      assertThat(1).isEqualTo(1);
                  }

                  @Test
                  void testAssertJ() {
                      assertThat(2 + 3).isEqualTo(5);
                  }
              }
              """
          )
        );
    }

    @Test
    void addsTestAnnotationForMockitoVerify() {
        // Unannotated method in a JUnit 5 class whose check is Mockito.verify() gets @Test
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "mockito-core")),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import java.util.List;

              class MyTest {
                  @Test
                  void existingTest() {
                      org.junit.jupiter.api.Assertions.assertTrue(true);
                  }

                  void testMockito() {
                      List<?> list = Mockito.mock(List.class);
                      Mockito.verify(list).clear();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;
              import java.util.List;

              class MyTest {
                  @Test
                  void existingTest() {
                      org.junit.jupiter.api.Assertions.assertTrue(true);
                  }

                  @Test
                  void testMockito() {
                      List<?> list = Mockito.mock(List.class);
                      Mockito.verify(list).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void addsTestAnnotationForHamcrestAssertThat() {
        // Unannotated method in a JUnit 5 class using MatcherAssert.assertThat gets @Test
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "hamcrest-3")),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.hamcrest.MatcherAssert;
              import org.hamcrest.Matchers;

              class MyTest {
                  @Test
                  void existingTest() {
                      org.junit.jupiter.api.Assertions.assertTrue(true);
                  }

                  void testHamcrest() {
                      MatcherAssert.assertThat("abc", Matchers.notNullValue());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.hamcrest.MatcherAssert;
              import org.hamcrest.Matchers;

              class MyTest {
                  @Test
                  void existingTest() {
                      org.junit.jupiter.api.Assertions.assertTrue(true);
                  }

                  @Test
                  void testHamcrest() {
                      MatcherAssert.assertThat("abc", Matchers.notNullValue());
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyJUnit5LifecycleMethods() {
        // Methods named test... with assertions annotated with @AfterEach, @Disabled, or @RepeatedTest are not modified
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.Disabled;
              import org.junit.jupiter.api.RepeatedTest;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertTrue(true);
                  }

                  @AfterEach
                  void testAfterEach() {
                      Assertions.assertEquals(1, 1);
                  }

                  @Disabled
                  void testDisabled() {
                      Assertions.assertEquals(1, 1);
                  }

                  @RepeatedTest(2)
                  void testRepeated() {
                      Assertions.assertEquals(1, 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyJUnit4LifecycleMethods() {
        // Methods named test... with assertions annotated with @Before, @After, or @Ignore are not modified
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4")),
          java(
            """
              import org.junit.Test;
              import org.junit.Before;
              import org.junit.After;
              import org.junit.Ignore;
              import org.junit.Assert;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assert.assertTrue(true);
                  }

                  @Before
                  void testBefore() {
                      Assert.assertEquals(1, 1);
                  }

                  @After
                  void testAfter() {
                      Assert.assertEquals(1, 1);
                  }

                  @Ignore
                  void testIgnore() {
                      Assert.assertEquals(1, 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void defaultOptionBehavesFlagOnly() {
        // AddMissingTestAnnotation(null) only flags the method with a search result marker and does not add @Test
        //language=java
        rewriteRun(
          spec -> spec.recipe(new AddMissingTestAnnotation(null)),
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertEquals(1, 1);
                  }

                  void testAddition() {
                      Assertions.assertEquals(5, 2 + 3);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertEquals(1, 1);
                  }

                  /*~~(Missing @Test annotation)~~>*/void testAddition() {
                      Assertions.assertEquals(5, 2 + 3);
                  }
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyAbstractMethodWithoutBody() {
        // Abstract method without a body in an abstract test class is not modified
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              abstract class MyTest {
                  @Test
                  void existingTest() {
                      Assertions.assertTrue(true);
                  }

                  abstract void testSomething();
              }
              """
          )
        );
    }

    @Test
    void doesNotModifyNestedClassWithoutOwnTestMethods() {
        // Nested class without its own @Test method is not modified even if outer class has @Test
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class OuterTest {
                  @Test
                  void outerTest() {
                      Assertions.assertTrue(true);
                  }

                  class InnerHelper {
                      void testInner() {
                          Assertions.assertEquals(1, 1);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addsTestAnnotationToNestedClassWithOwnTestMethods() {
        // Nested class with its own @Test method gets @Test on unannotated test methods with assertions
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class OuterTest {
                  @Test
                  void outerTest() {
                      Assertions.assertTrue(true);
                  }

                  class InnerTest {
                      @Test
                      void existingInnerTest() {
                          Assertions.assertTrue(true);
                      }

                      void testInner() {
                          Assertions.assertEquals(1, 1);
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Assertions;

              class OuterTest {
                  @Test
                  void outerTest() {
                      Assertions.assertTrue(true);
                  }

                  class InnerTest {
                      @Test
                      void existingInnerTest() {
                          Assertions.assertTrue(true);
                      }

                      @Test
                      void testInner() {
                          Assertions.assertEquals(1, 1);
                      }
                  }
              }
              """
          )
        );
    }
}
