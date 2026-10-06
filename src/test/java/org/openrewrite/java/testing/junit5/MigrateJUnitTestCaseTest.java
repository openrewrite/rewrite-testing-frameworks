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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

class MigrateJUnitTestCaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3"))
          .recipe(new MigrateJUnitTestCase());
    }

    @DocumentExample
    @Test
    void convertTestCase() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  protected long value1;
                  protected long value2;

                  @Override
                  protected void setUp() {
                      super.setUp();
                      value1 = 2;
                      value2 = 3;
                  }

                  public void testAdd() {
                      setName("primitive test");
                      long result = value1 + value2;
                      assertEquals(5, result);
                      fail("some Failure message");
                  }

                  @Override
                  protected void tearDown() {
                      super.tearDown();
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.*;

              public class MathTest {
                  protected long value1;
                  protected long value2;

                  @BeforeEach
                  public void setUp() {
                      value1 = 2;
                      value2 = 3;
                  }

                  @Test
                  public void testAdd() {
                      //setName("primitive test");
                      long result = value1 + value2;
                      assertEquals(5, result);
                      fail("some Failure message");
                  }

                  @AfterEach
                  public void tearDown() {
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertExtendedTestCase() {
        //language=java
        rewriteRun(
          java(
            """
              package com.abc;
              import junit.framework.TestCase;
              public abstract class CTest extends TestCase {
                  @Override
                  public void setUp() {}

                  @Override
                  public void tearDown() {}
              }
              """,
            """
              package com.abc;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              public abstract class CTest {
                  @BeforeEach
                  public void setUp() {}

                  @AfterEach
                  public void tearDown() {}
              }
              """
          ),
          java(
            """
              package com.abc;
              import com.abc.CTest;
              import static org.junit.Assert.assertEquals;
              public class MathTest extends CTest {
                  protected long value1;
                  protected long value2;

                  @Override
                  protected void setUp() {
                      value1 = 2;
                      value2 = 3;
                  }

                  public void testAdd() {
                      long result = value1 + value2;
                      assertEquals(5, result);
                  }

                  @Override
                  protected void tearDown() {
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """,
            """
              package com.abc;
              import com.abc.CTest;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MathTest extends CTest {
                  protected long value1;
                  protected long value2;

                  @BeforeEach
                  @Override
                  public void setUp() {
                      value1 = 2;
                      value2 = 3;
                  }

                  @Test
                  public void testAdd() {
                      long result = value1 + value2;
                      assertEquals(5, result);
                  }

                  @AfterEach
                  @Override
                  public void tearDown() {
                      value1 = 0;
                      value2 = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void notTestCaseHasTestCaseAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static junit.framework.TestCase.assertTrue;

              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue("assert message", isSameStuff("stuff"));
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue(isSameStuff("stuff"), "assert message");
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """
          )
        );
    }

    @Test
    void notTestCaseHasAssertAssertion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;

              import static junit.framework.Assert.assertTrue;

              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue("assert message", isSameStuff("stuff"));
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              class AaTest {
                  @Test
                  public void someTest() {
                      assertTrue(isSameStuff("stuff"), "assert message");
                  }
                  private boolean isSameStuff(String stuff) {
                      return "stuff".equals(stuff);
                  }
              }
              """
          )
        );
    }

    @Test
    void notTestCase() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              class AaTest {
                  @Test(expected = NumberFormatException.class)
                  public void testSomeNumberStuff() {
                      Double n = Double.valueOf("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void avoidDuplicateAnnotations(){
        rewriteRun(
          spec -> spec.recipes(
            new MigrateJUnitTestCase(),
            new UpdateBeforeAfterAnnotations()
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import org.junit.After;
              import org.junit.Before;

              public class MathTest extends TestCase {

                  @Before
                  public void setUp() {
                  }

                  @After
                  public void tearDown() {
                  }
              }
              """,
            """
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;

              public class MathTest {

                  @BeforeEach
                  public void setUp() {
                  }

                  @AfterEach
                  public void tearDown() {
                  }
              }
              """
          )
        );
    }

    @Test
    void caseWithConstructorCallingSuperTestName() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public class AppTest extends TestCase {
                  public AppTest(String testName) {
                      super(testName);
                  }
              }
              """,
            """
              public class AppTest {
              }
              """
          )
        );
    }

    @Test
    void constructorWithAdditionalStatementsIsKept() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public class AppTest extends TestCase {
                  private final String name;
                  public AppTest(String testName) {
                      super(testName);
                      this.name = testName;
                  }
              }
              """,
            """
              public class AppTest {
                  private final String name;
                  public AppTest(String testName) {
                      this.name = testName;
                  }
              }
              """
          )
        );
    }

    @Test
    void suiteMethodIsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class AppTest extends TestCase {
                  public AppTest(String testName) {
                      super(testName);
                  }
                  public static Test suite() {
                      return new TestSuite(AppTest.class);
                  }
                  public void testApp() {
                      assertTrue(true);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              public class AppTest {

                  @Test
                  public void testApp() {
                      assertTrue(true);
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void migrateTestSetupFixtures(boolean compositeMigration) {
        rewriteRun(
          spec -> {
              if (compositeMigration) {
                  spec.recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration");
              }
          },
          //language=java
          java(
            """
              package fixture0;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class MathTest extends TestCase {
                  private static String resource;

                  public static Test suite() {
                      return new TestSetup(new TestSuite(MathTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              setUpOnce();
                          }

                          @Override
                          protected void tearDown() throws Exception {
                              tearDownOnce();
                          }
                      };
                  }

                  static void setUpOnce() throws Exception {
                      resource = "open";
                  }

                  static void tearDownOnce() throws Exception {
                      resource = null;
                  }

                  public void testResource() {
                      assertEquals("open", resource);
                  }
              }
              """,
            """
              package fixture0;

              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeAll;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MathTest {
                  private static String resource;

                  @BeforeAll
                  public static void beforeAll() throws Exception {
                      setUpOnce();
                  }

                  @AfterAll
                  public static void afterAll() throws Exception {
                      tearDownOnce();
                  }

                  static void setUpOnce() throws Exception {
                      resource = "open";
                  }

                  static void tearDownOnce() throws Exception {
                      resource = null;
                  }

                  @Test
                  public void testResource() {
                      assertEquals("open", resource);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package fixture1;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class MathTest extends TestCase {
                  private static String resource;
                  private static int counter;

                  public static Test suite() {
                      return new TestSetup(new TestSuite(MathTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              resource = "open";
                              counter = 1;
                              System.setProperty("example.mode", "test");
                          }

                          @Override
                          protected void tearDown() throws Exception {
                              resource = null;
                              System.clearProperty("example.mode");
                          }
                      };
                  }

                  static void beforeAll() {}

                  static void afterAll() {}

                  @Override
                  protected void setUp() throws Exception {
                      super.setUp();
                      counter++;
                  }

                  @Override
                  protected void tearDown() throws Exception {
                      counter--;
                      super.tearDown();
                  }
              }
              """,
            """
              package fixture1;

              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeAll;
              import org.junit.jupiter.api.BeforeEach;

              class MathTest {
                  private static String resource;
                  private static int counter;

                  @BeforeAll
                  public static void beforeAll1() throws Exception {
                      resource = "open";
                      counter = 1;
                      System.setProperty("example.mode", "test");
                  }

                  @AfterAll
                  public static void afterAll1() throws Exception {
                      resource = null;
                      System.clearProperty("example.mode");
                  }

                  static void beforeAll() {}

                  static void afterAll() {}

                  @BeforeEach
                  public void setUp() throws Exception {
                      counter++;
                  }

                  @AfterEach
                  public void tearDown() throws Exception {
                      counter--;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package fixture2;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class BaseTest extends TestCase {
                  protected void beforeAll() {}

                  protected static void afterAll() {}
              }

              class MathTest extends BaseTest {
                  public static Test suite() {
                      return new TestSetup(new TestSuite(MathTest.class)) {
                          @Override
                          protected void setUp() {
                              System.setProperty("example.mode", "test");
                          }

                          @Override
                          protected void tearDown() {
                              System.clearProperty("example.mode");
                          }
                      };
                  }
              }
              """,
            """
              package fixture2;

              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeAll;

              class BaseTest {
                  protected void beforeAll() {}

                  protected static void afterAll() {}
              }

              class MathTest extends BaseTest {
                  @BeforeAll
                  public static void beforeAll1() {
                      System.setProperty("example.mode", "test");
                  }

                  @AfterAll
                  public static void afterAll1() {
                      System.clearProperty("example.mode");
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package fixture3;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class MathTest extends TestCase {
                  public static Test suite() {
                      return new TestSetup(new TestSuite(MathTest.class)) {
                          private String resource;

                          @Override
                          protected void setUp() {
                              resource = "open";
                          }
                      };
                  }
              }
              """,
            """
              package fixture3;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class MathTest extends TestCase {
                  /*~~(Migrate this TestSetup fixture manually to JUnit Jupiter lifecycle methods)~~>*/public static Test suite() {
                      return new TestSetup(new TestSuite(MathTest.class)) {
                          private String resource;

                          @Override
                          protected void setUp() {
                              resource = "open";
                          }
                      };
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package fixture4;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class MathTest extends TestCase {
                  public static Test suite() {
                      return new TestSetup(new TestSuite(MathTest.class) {
                          @Override
                          public int countTestCases() {
                              return 1;
                          }
                      }) {
                          @Override
                          protected void setUp() {
                              System.setProperty("example.mode", "test");
                          }
                      };
                  }
              }
              """,
            """
              package fixture4;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class MathTest extends TestCase {
                  /*~~(Migrate this TestSetup fixture manually to JUnit Jupiter lifecycle methods)~~>*/public static Test suite() {
                      return new TestSetup(new TestSuite(MathTest.class) {
                          @Override
                          public int countTestCases() {
                              return 1;
                          }
                      }) {
                          @Override
                          protected void setUp() {
                              System.setProperty("example.mode", "test");
                          }
                      };
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package references;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class InstanceReferenceTest extends TestCase {
                  public static Test suite() {
                      return new TestSetup(new TestSuite(InstanceReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              this.toString();
                          }
                      };
                  }
              }

              class InheritedMethodReferenceTest extends TestCase {
                  public static Test suite() {
                      return new TestSetup(new TestSuite(InheritedMethodReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              getTest();
                          }
                      };
                  }
              }

              class InheritedFieldReferenceTest extends TestCase {
                  public static Test suite() {
                      return new TestSetup(new TestSuite(InheritedFieldReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              fTest.countTestCases();
                          }
                      };
                  }
              }

              class SuperReferenceTest extends TestCase {
                  public static Test suite() {
                      return new TestSetup(new TestSuite(SuperReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              super.setUp();
                          }
                      };
                  }
              }
              """,
            """
              package references;

              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              class InstanceReferenceTest extends TestCase {
                  /*~~(Migrate this TestSetup fixture manually to JUnit Jupiter lifecycle methods)~~>*/public static Test suite() {
                      return new TestSetup(new TestSuite(InstanceReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              this.toString();
                          }
                      };
                  }
              }

              class InheritedMethodReferenceTest extends TestCase {
                  /*~~(Migrate this TestSetup fixture manually to JUnit Jupiter lifecycle methods)~~>*/public static Test suite() {
                      return new TestSetup(new TestSuite(InheritedMethodReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              getTest();
                          }
                      };
                  }
              }

              class InheritedFieldReferenceTest extends TestCase {
                  /*~~(Migrate this TestSetup fixture manually to JUnit Jupiter lifecycle methods)~~>*/public static Test suite() {
                      return new TestSetup(new TestSuite(InheritedFieldReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              fTest.countTestCases();
                          }
                      };
                  }
              }

              class SuperReferenceTest extends TestCase {
                  /*~~(Migrate this TestSetup fixture manually to JUnit Jupiter lifecycle methods)~~>*/public static Test suite() {
                      return new TestSetup(new TestSuite(SuperReferenceTest.class)) {
                          @Override
                          protected void setUp() throws Exception {
                              super.setUp();
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveKotlinTestCasesUnchanged() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "hamcrest-3")),
          //language=kotlin
          kotlin(
            """
              import junit.framework.TestCase

              class MathTest : TestCase() {
                  override fun setUp() {
                      super.setUp()
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOverrideWhenAlreadyAnnotatedWithBefore() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              import org.junit.Before;

              public class MathTest extends TestCase {
                  protected long value1;

                  @Override
                  @Before
                  public void setUp() {
                      value1 = 2;
                  }

                  public void testAdd() {
                      assertEquals(2, value1);
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MathTest {
                  protected long value1;

                  @Before
                  public void setUp() {
                      value1 = 2;
                  }

                  @Test
                  public void testAdd() {
                      assertEquals(2, value1);
                  }
              }
              """
          )
        );
    }

    @Test
    void removeOverrideFromOtherTestCaseMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  @Override
                  public String getName() {
                      return "math";
                  }

                  @Override
                  public int countTestCases() {
                      return 1;
                  }

                  public void testAdd() {
                      assertEquals(2, 2);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MathTest {
                  public String getName() {
                      return "math";
                  }

                  public int countTestCases() {
                      return 1;
                  }

                  @Test
                  public void testAdd() {
                      assertEquals(2, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainOverrideOfObjectMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  @Override
                  public String toString() {
                      return "math";
                  }

                  @Override
                  public boolean equals(Object other) {
                      return other instanceof MathTest;
                  }

                  @Override
                  public int hashCode() {
                      return 42;
                  }

                  public void testAdd() {
                      assertEquals(2, 2);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MathTest {
                  @Override
                  public String toString() {
                      return "math";
                  }

                  @Override
                  public boolean equals(Object other) {
                      return other instanceof MathTest;
                  }

                  @Override
                  public int hashCode() {
                      return 42;
                  }

                  @Test
                  public void testAdd() {
                      assertEquals(2, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainOverrideOfInterfaceMethod() {
        //language=java
        rewriteRun(
          java(
            """
              package com.abc;
              public interface Named {
                  String describe();
              }
              """
          ),
          java(
            """
              package com.abc;
              import junit.framework.TestCase;

              public class MathTest extends TestCase implements Named {
                  @Override
                  public String describe() {
                      return "math";
                  }

                  public void testAdd() {
                      assertEquals(2, 2);
                  }
              }
              """,
            """
              package com.abc;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class MathTest implements Named {
                  @Override
                  public String describe() {
                      return "math";
                  }

                  @Test
                  public void testAdd() {
                      assertEquals(2, 2);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainOverrideInAnonymousClass() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  @Override
                  public void setUp() {
                      Runnable runnable = new Runnable() {
                          @Override
                          public void run() {
                          }
                      };
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;

              public class MathTest {
                  @BeforeEach
                  public void setUp() {
                      Runnable runnable = new Runnable() {
                          @Override
                          public void run() {
                          }
                      };
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/1114")
    @Test
    void skipTestCaseOverridingRun() {
        //language=java
        rewriteRun(
          java(
            """
              import junit.framework.AssertionFailedError;
              import junit.framework.TestCase;
              import junit.framework.TestResult;

              public class MultiThreadedTest extends TestCase {

                  private TestResult testResult = null;

                  @Override
                  public void run(TestResult result) {
                      this.testResult = result;
                      super.run(result);
                  }

                  public void handleException(Throwable t) {
                      if (t instanceof AssertionFailedError) {
                          testResult.addFailure(this, (AssertionFailedError) t);
                      } else {
                          testResult.addError(this, t);
                      }
                  }

                  public void testSomething() {
                      assertEquals(2, 1 + 1);
                  }
              }
              """
          )
        );
    }
}
