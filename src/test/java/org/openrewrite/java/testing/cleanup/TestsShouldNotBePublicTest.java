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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class TestsShouldNotBePublicTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5", "junit-jupiter-params-5"))
          .recipe(new TestsShouldNotBePublic(false));
    }

    @DocumentExample
    @Test
    void removePublicClassModifiers() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              public class ATest {

                  @Test
                  void testMethod() {
                  }

                  @Nested
                  public class NestedTestClass {

                      @Test
                      void anotherTestMethod() {
                      }
                  }

                  @Nested
                  public class AnotherNestedTestClass {

                      private static final String CONSTANT = "foo";

                      private void setup() {
                      }

                      @Test
                      void anotherTestMethod() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              class ATest {

                  @Test
                  void testMethod() {
                  }

                  @Nested
                  class NestedTestClass {

                      @Test
                      void anotherTestMethod() {
                      }
                  }

                  @Nested
                  class AnotherNestedTestClass {

                      private static final String CONSTANT = "foo";

                      private void setup() {
                      }

                      @Test
                      void anotherTestMethod() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ignorePublicAbstractClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public abstract class ATest {

                  @BeforeEach
                  public void beforeEach_public() {
                  }

                  @BeforeEach
                  protected void beforeEach_protected() {
                  }

                  @BeforeEach
                  void beforeEach_packagePrivate() {
                  }

                  @AfterEach
                  public void afterEach_public() {
                  }

                  @AfterEach
                  protected void afterEach_protected() {
                  }

                  @AfterEach
                  void afterEach_packagePrivate() {
                  }

                  @Test
                  void testMethod_packagePrivate() {
                  }

                  @Test
                  public void testMethod_public() {
                  }

                  @Test
                  protected void testMethod_protected() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreInterface() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              public interface ATest {

                  @BeforeEach
                  default void beforeEach_public() {
                  }

                  @Test
                  default void testMethod_packagePrivate() {
                  }

              }
              """
          )
        );
    }

    @Test
    void ignoreOverriddenMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              abstract class AbstractTest {
                  public abstract void testMethod();
              }

              class BTest extends AbstractTest {

                  @Test
                  @Override
                  public void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignorePublicClassWithPublicVariables() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              public class ATest {

                  public int foo;

                  @Test
                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignorePublicClassWithPublicMethodsThatAreNotRelatedToTests() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              public class ATest {

                  public int foo() {
                      return 0;
                  }

                  @Test
                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removePublicMethodModifiers() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.*;
              import org.junit.jupiter.api.*;
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {

                  @BeforeEach
                  public void beforeEachMethod() {
                  }

                  @AfterEach
                  public void afterEachMethod() {
                  }

                  @Test
                  public void testMethod() {
                  }

                  @RepeatedTest(2)
                  public void repeatedTestMethod() {
                  }

                  @ValueSource(strings = {"a", "b"})
                  @ParameterizedTest
                  public void parameterizedTestMethod(String input) {
                  }

                  @TestFactory
                  public Collection<DynamicTest> testFactoryMethod() {
                      return null;
                  }
              }
              """,
            """
              import java.util.*;
              import org.junit.jupiter.api.*;
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {

                  @BeforeEach
                  void beforeEachMethod() {
                  }

                  @AfterEach
                  void afterEachMethod() {
                  }

                  @Test
                  void testMethod() {
                  }

                  @RepeatedTest(2)
                  void repeatedTestMethod() {
                  }

                  @ValueSource(strings = {"a", "b"})
                  @ParameterizedTest
                  void parameterizedTestMethod(String input) {
                  }

                  @TestFactory
                  Collection<DynamicTest> testFactoryMethod() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    void ignorePrivateMethodModifiers() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Collection;
              import java.util.Collections;
              import org.junit.jupiter.api.*;
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {

                  @BeforeEach
                  private void beforeEachMethod() {
                  }
                  @AfterEach
                  private void afterEachMethod() {
                  }
                  @Test
                  private void testMethod() {
                  }
                  @RepeatedTest(2)
                  private void repeatedTestMethod() {
                  }
                  @ValueSource(strings = {"a", "b"})
                  @ParameterizedTest
                  private void parameterizedTestMethod(String input) {
                  }
                  @TestFactory
                  private Collection<DynamicTest> testFactoryMethod() {
                      return Collections.emptyList();
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreProtectedMethodModifiers() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Collection;
              import java.util.Collections;
              import org.junit.jupiter.api.*;
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {

                  @BeforeEach
                  protected void beforeEachMethod() {
                  }
                  @AfterEach
                  protected void afterEachMethod() {
                  }
                  @Test
                  protected void testMethod() {
                  }
                  @RepeatedTest(2)
                  protected void repeatedTestMethod() {
                  }
                  @ValueSource(strings = {"a", "b"})
                  @ParameterizedTest
                  protected void parameterizedTestMethod(String input) {
                  }
                  @TestFactory
                  protected Collection<DynamicTest> testFactoryMethod() {
                      return Collections.emptyList();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeProtectedMethodModifiers() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new TestsShouldNotBePublic(true)),
          java(
            """
              import java.util.*;
              import org.junit.jupiter.api.*;
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {

                  @BeforeEach
                  protected void beforeEachMethod() {
                  }

                  @AfterEach
                  protected void afterEachMethod() {
                  }

                  @Test
                  protected void testMethod() {
                  }

                  @RepeatedTest(2)
                  protected void repeatedTestMethod() {
                  }

                  @ValueSource(strings = {"a", "b"})
                  @ParameterizedTest
                  protected void parameterizedTestMethod(String input) {
                  }

                  @TestFactory
                  protected Collection<DynamicTest> testFactoryMethod() {
                      return null;
                  }
              }
              """,
            """
              import java.util.*;
              import org.junit.jupiter.api.*;
              import org.junit.jupiter.params.ParameterizedTest;
              import org.junit.jupiter.params.provider.ValueSource;

              class ATest {

                  @BeforeEach
                  void beforeEachMethod() {
                  }

                  @AfterEach
                  void afterEachMethod() {
                  }

                  @Test
                  void testMethod() {
                  }

                  @RepeatedTest(2)
                  void repeatedTestMethod() {
                  }

                  @ValueSource(strings = {"a", "b"})
                  @ParameterizedTest
                  void parameterizedTestMethod(String input) {
                  }

                  @TestFactory
                  Collection<DynamicTest> testFactoryMethod() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/309")
    void baseclassForTestsNeedsToStayPublic() {
        //language=java
        rewriteRun(
          spec -> spec.recipe(new TestsShouldNotBePublic(true)),
          java(
            // base class for tests should stay public
            """
              package com.hello;

              import org.junit.jupiter.api.BeforeEach;

              public class MyTestBase {
                @BeforeEach
                void setUp() {
                }
              }
              """
          ),
          java(
            // test class extends base class from another package
            """
              package com.world;

              import com.hello.MyTestBase;
              import org.junit.jupiter.api.Test;

              class MyTest extends MyTestBase {
                @Test
                void isWorking() {
                }
              }
              """
          )
        );
    }
}
