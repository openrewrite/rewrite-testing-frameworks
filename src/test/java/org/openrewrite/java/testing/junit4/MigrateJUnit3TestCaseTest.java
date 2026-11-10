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
package org.openrewrite.java.testing.junit4;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnit3TestCaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipe(new MigrateJUnit3TestCase());
    }

    @DocumentExample
    @Test
    void lifecycleTestsAndAssertions() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  protected long value1;

                  @Override
                  protected void setUp() throws Exception {
                      super.setUp();
                      value1 = 2;
                  }

                  public void testAdd() {
                      assertEquals(4, value1 + 2);
                      assertEquals(1.5, 3.0 / 2);
                  }

                  void testNotPublic() {
                  }

                  @Override
                  protected void tearDown() {
                      super.tearDown();
                      value1 = 0;
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;

              import static org.junit.Assert.assertEquals;

              public class MathTest {
                  protected long value1;

                  @Before
                  public void setUp() throws Exception {
                      value1 = 2;
                  }

                  @Test
                  public void testAdd() {
                      assertEquals(4, value1 + 2);
                      assertEquals(1.5, 3.0 / 2, 0.0);
                  }

                  void testNotPublic() {
                  }

                  @After
                  public void tearDown() {
                      value1 = 0;
                  }
              }
              """
          )
        );
    }

    @Test
    void subclassKeepsSuperCallsToMigratedBase() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public abstract class BaseTest extends TestCase {
                  @Override
                  protected void setUp() {
                  }
              }
              """,
            """
              import org.junit.Before;

              public abstract class BaseTest {
                  @Before
                  public void setUp() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              public class ChildTest extends BaseTest {
                  @Override
                  protected void setUp() {
                      super.setUp();
                  }

                  public void testIt() {
                      assertTrue(true);
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.junit.Test;

              import static org.junit.Assert.assertTrue;

              public class ChildTest extends BaseTest {
                  @Override
                  @Before
                  public void setUp() {
                      super.setUp();
                  }

                  @Test
                  public void testIt() {
                      assertTrue(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void getNameReadsTestNameRule() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class NamedTest extends TestCase {
                  public void testIt() {
                      assertEquals("testIt", getName());
                  }
              }
              """,
            """
              import org.junit.Rule;
              import org.junit.Test;
              import org.junit.rules.TestName;

              import static org.junit.Assert.assertEquals;

              public class NamedTest {
                  @Rule
                  public TestName testName = new TestName();

                  @Test
                  public void testIt() {
                      assertEquals("testIt", testName.getMethodName());
                  }
              }
              """
          )
        );
    }

    @Test
    void redundantNameConstructorIsRemovedWithItsCallers() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  public MathTest(String testName) {
                      super(testName);
                  }

                  public static void main(String[] args) {
                      new MathTest("FOO").testAdd();
                  }

                  public void testAdd() {
                      assertEquals(2, 1 + 1);
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.Assert.assertEquals;

              public class MathTest {
                  public static void main(String[] args) {
                      new MathTest().testAdd();
                  }

                  @Test
                  public void testAdd() {
                      assertEquals(2, 1 + 1);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class Other {
                  void run() {
                      new MathTest("BAR").testAdd();
                  }
              }
              """,
            """
              class Other {
                  void run() {
                      new MathTest().testAdd();
                  }
              }
              """
          )
        );
    }

    @Test
    void nameConstructorWithInitializationBecomesNoArgument() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  private final boolean initialized;

                  public MathTest(String testName) {
                      super(testName);
                      initialized = true;
                  }

                  public void testAdd() {
                      assertTrue(initialized);
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.Assert.assertTrue;

              public class MathTest {
                  private final boolean initialized;

                  public MathTest() {
                      initialized = true;
                  }

                  @Test
                  public void testAdd() {
                      assertTrue(initialized);
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorChainThroughSubclass() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public abstract class BaseTest extends TestCase {
                  public BaseTest(String name) {
                      super(name);
                  }
              }
              """,
            """
              public abstract class BaseTest {
              }
              """
          ),
          //language=java
          java(
            """
              public class ChildTest extends BaseTest {
                  public ChildTest(String name) {
                      super(name);
                  }

                  public void testIt() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class ChildTest extends BaseTest {
                  @Test
                  public void testIt() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveClassUsingTheTestNameUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  private final String label;

                  public MathTest(String testName) {
                      super(testName);
                      label = "test " + testName;
                  }

                  public void testAdd() {
                      assertEquals(2, 1 + 1);
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveClassWhoseInstancesAreUsedAsTests() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class MathTest extends TestCase {
                  public MathTest(String name) {
                      super(name);
                  }

                  public static Test suite() {
                      TestSuite suite = new TestSuite();
                      suite.addTest(new MathTest("testAdd"));
                      return suite;
                  }

                  public void testAdd() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveRunTestResultOverrideUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import junit.framework.TestResult;

              public class MultiThreadedTest extends TestCase {
                  private TestResult testResult;

                  @Override
                  public void run(TestResult result) {
                      this.testResult = result;
                      super.run(result);
                  }

                  public void testSomething() {
                      assertTrue(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveClassCallingSetNameUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public void testSomething() {
                      setName("other");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepNonAssertionTestCaseReferences() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  static boolean isTest(Class<?> c) {
                      return TestCase.class.isAssignableFrom(c);
                  }

                  static String nameOf(TestCase test) {
                      return test.getName();
                  }

                  public void testSomething() {
                      assertTrue(isTest(FooTest.class));
                  }
              }
              """,
            """
              import junit.framework.TestCase;
              import org.junit.Test;

              import static org.junit.Assert.assertTrue;

              public class FooTest {
                  static boolean isTest(Class<?> c) {
                      return TestCase.class.isAssignableFrom(c);
                  }

                  static String nameOf(TestCase test) {
                      return test.getName();
                  }

                  @Test
                  public void testSomething() {
                      assertTrue(isTest(FooTest.class));
                  }
              }
              """
          )
        );
    }

    @Test
    void selfSuiteIsRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class FooTest extends TestCase {
                  public static Test suite() {
                      return new TestSuite(FooTest.class);
                  }

                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class FooTest {
                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void selfSuiteInAVariableIsRemoved() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class FooTest extends TestCase {
                  public static Test suite() {
                      TestSuite suite = new TestSuite(FooTest.class);
                      return suite;
                  }

                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class FooTest {
                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveSuiteBuiltFromLoops() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class FooTest extends TestCase {
                  public static Test suite() {
                      TestSuite suite = new TestSuite();
                      for (String name : new String[] {"a", "b"}) {
                          suite.addTest(new FooTest(name));
                      }
                      return suite;
                  }

                  private final String name;

                  public FooTest(String name) {
                      super("testSomething");
                      this.name = name;
                  }

                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void testSetupBecomesBeforeAndAfterClass() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.extensions.TestSetup;
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class DbTest extends TestCase {
                  static String connection;

                  public static Test suite() {
                      return new TestSetup(new TestSuite(DbTest.class)) {
                          protected void setUp() {
                              connection = "open";
                          }

                          protected void tearDown() {
                              connection = null;
                          }
                      };
                  }

                  public void testQuery() {
                      assertEquals("open", connection);
                  }
              }
              """,
            """
              import org.junit.AfterClass;
              import org.junit.BeforeClass;
              import org.junit.Test;

              import static org.junit.Assert.assertEquals;

              public class DbTest {
                  static String connection;

                  @Test
                  public void testQuery() {
                      assertEquals("open", connection);
                  }

                  @BeforeClass
                  public static void oneTimeSetUp() {
                      connection = "open";
                  }

                  @AfterClass
                  public static void oneTimeTearDown() {
                      connection = null;
                  }
              }
              """
          )
        );
    }

    @Test
    void aggregateSuiteBecomesSuiteClasses() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class ATest extends TestCase {
                  public void testA() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class ATest {
                  @Test
                  public void testA() {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestSuite;

              public class AllTests {
                  public static Test suite() {
                      TestSuite suite = new TestSuite("all");
                      suite.addTestSuite(ATest.class);
                      suite.addTest(new TestSuite(BTest.class));
                      return suite;
                  }

                  public static void main(String[] args) {
                      junit.textui.TestRunner.run(suite());
                  }
              }
              """,
            """
              import org.junit.runner.JUnitCore;
              import org.junit.runner.RunWith;
              import org.junit.runners.Suite;

              @RunWith(Suite.class)
              @Suite.SuiteClasses({ATest.class, BTest.class})
              public class AllTests {
                  public static void main(String[] args) {
                      JUnitCore.runClasses(AllTests.class);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class BTest extends TestCase {
                  public void testB() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class BTest {
                  @Test
                  public void testB() {
                  }
              }
              """
          )
        );
    }

    @Test
    void testRunnerMainForClass() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public static void main(String[] args) {
                      junit.textui.TestRunner.run(FooTest.class);
                  }

                  public void testSomething() {
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.JUnitCore;

              public class FooTest {
                  public static void main(String[] args) {
                      JUnitCore.runClasses(FooTest.class);
                  }

                  @Test
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveClassWhoseInterfaceIsImplementedByTestCase() {
        rewriteRun(
          //language=java
          java(
            """
              public interface Named {
                  String getName();
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class NamedXaTest extends TestCase implements Named {
                  public void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Test
    void staticTestMethodBecomesAnInstanceMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public static void testCopy() {
                      assertTrue(true);
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.junit.Assert.assertTrue;

              public class FooTest {
                  @Test
                  public void testCopy() {
                      assertTrue(true);
                  }
              }
              """
          )
        );
    }

    @Test
    void qualifyTestAnnotationWhenAClassIsNamedTest() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class Test extends TestCase {
                  public void testIt() {
                  }
              }
              """,
            """
              public class Test {
                  @org.junit.Test
                  public void testIt() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepSuiteCalledByAnAggregateThatStays() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class AllTest extends TestCase {
                  public AllTest(String name) {
                      super(name);
                  }

                  public static Test suite() {
                      TestSuite suite = new TestSuite(AllTest.class);
                      suite.addTest(OneTest.suite());
                      return suite;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class OneTest extends TestCase {
                  public static TestSuite suite() {
                      return new TestSuite(OneTest.class);
                  }

                  public void testIt() {
                  }
              }
              """
          )
        );
    }

    @Test
    void suiteOfMemberClassesQualifiesThem() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class ParserTest {
                  public static Test suite() {
                      final TestSuite suite = new TestSuite();
                      suite.addTest(OneTest.suite());
                      return suite;
                  }

                  static class OneTest extends TestCase {
                      public static Test suite() {
                          return new TestSuite(OneTest.class);
                      }

                      public void testIt() {
                      }
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.junit.runners.Suite;

              @RunWith(Suite.class)
              @Suite.SuiteClasses({ParserTest.OneTest.class})
              public class ParserTest {
                  public static class OneTest {
                      @Test
                      public void testIt() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void annotatedStaticTestMethodBecomesAnInstanceMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import org.junit.Test;

              public class FooTest extends TestCase {
                  @Test
                  public static void testCopy() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class FooTest {
                  @Test
                  public void testCopy() {
                  }
              }
              """
          )
        );
    }

    @Test
    void memberClassWithSuiteMethodThatStaysBecomesPublic() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;

              public class ParserTest {
                  public static Test suite() {
                      final TestSuite suite = new TestSuite();
                      suite.addTest(OneTest.suite());
                      return suite;
                  }

                  static class OneTest extends TestCase {
                      public static Test suite() {
                          TestSuite s = new TestSuite(OneTest.class);
                          s.setName("one");
                          return s;
                      }

                      public void testIt() {
                      }
                  }
              }
              """,
            """
              import junit.framework.Test;
              import junit.framework.TestCase;
              import junit.framework.TestSuite;
              import org.junit.runner.RunWith;
              import org.junit.runners.Suite;

              @RunWith(Suite.class)
              @Suite.SuiteClasses({ParserTest.OneTest.class})
              public class ParserTest {
                  public static class OneTest extends TestCase {
                      public static Test suite() {
                          TestSuite s = new TestSuite(OneTest.class);
                          s.setName("one");
                          return s;
                      }

                      public void testIt() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void constructorThatThrowsStays() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public FooTest() throws Exception {
                      super();
                  }

                  public void testIt() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class FooTest {
                  public FooTest() throws Exception {
                  }

                  @Test
                  public void testIt() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveClassPassedAsAJUnit3Test() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FooTest extends TestCase {
                  public void testIt() {
                      Util.run(this);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              class Util {
                  static void run(TestCase test) {
                  }
              }
              """
          )
        );
    }

    @Test
    void annotatedNonPublicMethodsBecomePublic() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;

              public class FooTest extends TestCase {
                  @Before
                  protected void setUp() {
                  }

                  @After
                  protected void tearDown() {
                  }

                  @Test
                  void testIt() {
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;

              public class FooTest {
                  @Before
                  public void setUp() {
                  }

                  @After
                  public void tearDown() {
                  }

                  @Test
                  public void testIt() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveClassWithAnExplicitRunnerUntouched() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import org.junit.runner.RunWith;
              import org.junit.runners.JUnit4;

              @RunWith(JUnit4.class)
              public class FooTest extends TestCase {
                  public void testIt() {
                  }
              }
              """
          )
        );
    }

    @Test
    void beforeClassOnAnInstanceMethodIsDropped() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import org.junit.BeforeClass;

              public class FooTest extends TestCase {
                  @BeforeClass
                  public void setUp() {
                  }

                  public void testIt() {
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.junit.Test;

              public class FooTest {
                  @Before
                  public void setUp() {
                  }

                  @Test
                  public void testIt() {
                  }
              }
              """
          )
        );
    }

    @Test
    void testAnnotationOnAHelperIsDropped() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import org.junit.Test;

              public class FooTest extends TestCase {
                  @Test
                  public static int helper(String s) {
                      return 1;
                  }

                  public void testIt() {
                  }
              }
              """,
            """
              import org.junit.Test;

              public class FooTest {
                  public static int helper(String s) {
                      return 1;
                  }

                  @Test
                  public void testIt() {
                  }
              }
              """
          )
        );
    }
}
