/*
 * Copyright 2026 the original author or authors.
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

class ReplaceMockitoTestExecutionListenerTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "spring-test-6.1",
                "spring-boot-test-3.2",
                "junit-jupiter-api-5",
                "mockito-junit-jupiter-5",
                "junit-4.13",
                "mockito-core-3",
                "testng-7"))
          .recipe(new ReplaceMockitoTestExecutionListener());
    }

    // --- JUnit 5 tests ---

    @DocumentExample
    @Test
    void junit5SoleListenerMergeWithDefaults() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS, listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit5SoleListenerReplaceDefaults() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS, listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              @TestExecutionListeners
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit5SoleListenerNoMergeMode() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @TestExecutionListeners(listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              @TestExecutionListeners
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit5SoleListenerPositionalArg() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @TestExecutionListeners(MockitoTestExecutionListener.class)
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              @TestExecutionListeners
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit5SoleListenerPositionalArray() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @TestExecutionListeners({MockitoTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              @TestExecutionListeners
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit5WithInheritListenersFalse() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @TestExecutionListeners(value = {MockitoTestExecutionListener.class}, inheritListeners = false)
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              @TestExecutionListeners(inheritListeners = false)
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit5MultipleListeners() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS, listeners = {MockitoTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              @TestExecutionListeners(listeners = {DirtiesContextTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit5ExtendWithAlreadyPresent() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.junit.jupiter.MockitoExtension;
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.jupiter.api.Test;

              @ExtendWith(MockitoExtension.class)
              @TestExecutionListeners(listeners = {MockitoTestExecutionListener.class}, inheritListeners = false)
              public class SampleTest {
                  @Test
                  void test() {}
              }
              """
          )
        );
    }

    // --- JUnit 4 tests ---

    @Test
    void junit4NoExistingRunWith() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.junit.Test;

              @TestExecutionListeners(listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  public void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.junit.runner.RunWith;
              import org.mockito.junit.MockitoJUnitRunner;
              import org.junit.Test;

              @RunWith(MockitoJUnitRunner.class)
              @TestExecutionListeners
              public class SampleTest {
                  @Test
                  public void test() {}
              }
              """
          )
        );
    }

    @Test
    void junit4ExistingRunWithSkips() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.springframework.test.context.junit4.SpringRunner;

              @RunWith(SpringRunner.class)
              @TestExecutionListeners(listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  public void test() {}
              }
              """
          )
        );
    }

    // --- TestNG tests ---

    @Test
    void testngClass() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.testng.annotations.Test;

              @TestExecutionListeners(listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  @Test
                  public void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.mockito.MockitoAnnotations;
              import org.testng.annotations.Test;

              @TestExecutionListeners
              public class SampleTest {
                  private AutoCloseable mockitoCloseable;

                  @BeforeMethod
                  public void initMocks() {
                      mockitoCloseable = MockitoAnnotations.openMocks(this);
                  }
                  @Test
                  public void test() {}

                  @AfterMethod
                  public void closeMocks() throws Exception {
                      mockitoCloseable.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void testngExtendsAbstractTestNGSpringContextTests() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

              @TestExecutionListeners(mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS, listeners = {MockitoTestExecutionListener.class})
              public abstract class BaseTest extends AbstractTestNGSpringContextTests {
              }
              """,
            """
              import org.mockito.MockitoAnnotations;
              import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;


              public abstract class BaseTest extends AbstractTestNGSpringContextTests {
                  private AutoCloseable mockitoCloseable;

                  @BeforeMethod
                  public void initMocks() {
                      mockitoCloseable = MockitoAnnotations.openMocks(this);
                  }

                  @AfterMethod
                  public void closeMocks() throws Exception {
                      mockitoCloseable.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void testngOpenMocksWithoutClose() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.testng.annotations.Test;
              import org.testng.annotations.BeforeMethod;
              import org.mockito.MockitoAnnotations;

              @TestExecutionListeners(listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  private AutoCloseable mockitoCloseable;

                  @BeforeMethod
                  public void setUp() {
                      mockitoCloseable = MockitoAnnotations.openMocks(this);
                  }

                  @Test
                  public void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.Test;
              import org.testng.annotations.BeforeMethod;
              import org.mockito.MockitoAnnotations;

              @TestExecutionListeners
              public class SampleTest {
                  private AutoCloseable mockitoCloseable;

                  @BeforeMethod
                  public void setUp() {
                      mockitoCloseable = MockitoAnnotations.openMocks(this);
                  }

                  @Test
                  public void test() {}

                  @AfterMethod
                  public void closeMocks() throws Exception {
                      mockitoCloseable.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void testngAlreadyHasOpenMocksAndClose() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
              import org.testng.annotations.Test;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.AfterMethod;
              import org.mockito.MockitoAnnotations;

              @TestExecutionListeners(listeners = {MockitoTestExecutionListener.class})
              public class SampleTest {
                  private AutoCloseable mocks;

                  @BeforeMethod
                  public void setUp() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  @AfterMethod
                  public void tearDown() throws Exception {
                      mocks.close();
                  }

                  @Test
                  public void test() {}
              }
              """,
            """
              import org.springframework.test.context.TestExecutionListeners;
              import org.testng.annotations.Test;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.AfterMethod;
              import org.mockito.MockitoAnnotations;

              @TestExecutionListeners
              public class SampleTest {
                  private AutoCloseable mocks;

                  @BeforeMethod
                  public void setUp() {
                      mocks = MockitoAnnotations.openMocks(this);
                  }

                  @AfterMethod
                  public void tearDown() throws Exception {
                      mocks.close();
                  }

                  @Test
                  public void test() {}
              }
              """
          )
        );
    }

    // --- Negative tests ---

    @Test
    void doNotTouchIfNoListenerPresent() {
        rewriteRun(
          //language=java
          java(
            """
              @Deprecated
              public class SampleTest {}
              """
          )
        );
    }
}
