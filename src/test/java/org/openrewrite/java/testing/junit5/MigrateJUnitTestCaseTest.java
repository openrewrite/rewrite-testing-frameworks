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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
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
                  /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/public AppTest(String testName) {
                      this.name = testName;
                  }
              }
              """
          )
        );
    }

    @Test
    void migratesRetainedConstructorsWithoutChangingInitializationOrCallers() {
        rewriteRun(
          spec -> spec.parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4")
            .dependsOn("class KotlinCalledTest(name: String) : junit.framework.TestCase(name)")),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class InitializedTest extends TestCase {
                  private boolean initialized;

                  public InitializedTest(String testName) {
                      super(testName);
                      initialized = true;
                  }

                  public void testAdd() {
                      assertTrue(initialized);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              public class InitializedTest {
                  private boolean initialized;

                  public InitializedTest() {
                      initialized = true;
                  }

                  @Test
                  public void testAdd() {
                      assertTrue(initialized);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class FinalFieldTest extends TestCase {
                  private final StringBuilder log;

                  public FinalFieldTest(String testName) {
                      super(testName);
                      log = new StringBuilder("constructed");
                      log.append(":initialized");
                  }

                  @Override
                  protected void setUp() {
                      log.append(":setUp");
                  }

                  public void testLog() {
                      assertEquals("constructed:initialized:setUp", log.toString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class FinalFieldTest {
                  private final StringBuilder log;

                  public FinalFieldTest() {
                      log = new StringBuilder("constructed");
                      log.append(":initialized");
                  }

                  @BeforeEach
                  public void setUp() {
                      log.append(":setUp");
                  }

                  @Test
                  public void testLog() {
                      assertEquals("constructed:initialized:setUp", log.toString());
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class NamedFinalFieldTest extends TestCase {
                  private final StringBuilder log;

                  public NamedFinalFieldTest(String name) {
                      super(name);
                      log = new StringBuilder("constructed:" + name);
                  }

                  public void testLog() {
                      assertTrue(log.length() > 0);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static org.junit.jupiter.api.Assertions.assertTrue;

              public class NamedFinalFieldTest {
                  private final StringBuilder log;

                  /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/public NamedFinalFieldTest(String name) {
                      log = new StringBuilder("constructed:" + name);
                  }

                  @Test
                  public void testLog() {
                      assertTrue(log.length() > 0);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class CalledTest extends TestCase {
                  private final boolean initialized;

                  public CalledTest(String name) {
                      super(name);
                      initialized = true;
                  }
              }
              """,
            """
              public class CalledTest {
                  private final boolean initialized;

                  public CalledTest() {
                      initialized = true;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class TestFactory {
                  CalledTest create() {
                      return new CalledTest("testAdd");
                  }
                  CalledTest create(String name) {
                      return new CalledTest(name);
                  }
              }
              """,
            """
              class TestFactory {
                  CalledTest create() {
                      return new CalledTest();
                  }
                  CalledTest create(String name) {
                      return new CalledTest();
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import java.util.function.Function;

              public class ReferencedTest extends TestCase {
                  private final boolean initialized;
                  static final Function<String, ReferencedTest> FACTORY = ReferencedTest::new;

                  public ReferencedTest(String name) {
                      super(name);
                      initialized = true;
                  }
              }
              """,
            """
              import java.util.function.Function;

              public class ReferencedTest {
                  private final boolean initialized;
                  static final Function<String, ReferencedTest> FACTORY = ReferencedTest::new;

                  /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/public ReferencedTest(String name) {
                      initialized = true;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class OverloadedTest extends TestCase {
                  private final boolean initialized;

                  public OverloadedTest() {
                      initialized = false;
                  }

                  public OverloadedTest(String name) {
                      super(name);
                      initialized = true;
                  }
              }
              """,
            """
              public class OverloadedTest {
                  private final boolean initialized;

                  public OverloadedTest() {
                      initialized = false;
                  }

                  /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/public OverloadedTest(String name) {
                      initialized = true;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class UnrelatedTest {
                  private final boolean initialized;

                  public UnrelatedTest(String name) {
                      initialized = true;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class ShadowedNameTest extends TestCase {
                  private final String name;

                  public ShadowedNameTest(String name) {
                      super(name);
                      this.name = "initialized";
                  }
              }
              """,
            """
              public class ShadowedNameTest {
                  private final String name;

                  public ShadowedNameTest() {
                      this.name = "initialized";
                  }
              }
              """
          ),
          mavenProject("standalone",
            //language=java
            java(
              """
                package example;

                import junit.framework.TestCase;

                public class SharedTest extends TestCase {
                    private final boolean initialized;

                    public SharedTest(String name) {
                        super(name);
                        initialized = true;
                    }
                }
                """,
              """
                package example;

                public class SharedTest {
                    private final boolean initialized;

                    public SharedTest() {
                        initialized = true;
                    }
                }
                """
            )
          ),
          mavenProject("unrelated",
            spec -> spec.mapBeforeRecipe(source -> (SourceFile) new ChangeType(
              "example.UnrelatedSharedTest", "example.SharedTest", false).getVisitor()
              .visitNonNull(source, new InMemoryExecutionContext())),
            //language=java
            java(
              """
                package example;

                public class UnrelatedSharedTest {
                    public UnrelatedSharedTest(String name) {
                    }
                }
                """
            ),
            //language=java
            java(
              """
                package example;

                class UnrelatedSharedTestFactory {
                    UnrelatedSharedTest create() {
                        return new UnrelatedSharedTest("testAdd");
                    }
                }
                """
            )
          ),
          mavenProject("base",
            //language=java
            java(
              """
                import junit.framework.TestCase;

                public class BaseTest extends TestCase {
                    private final boolean initialized;

                    public BaseTest(String name) {
                        super(name);
                        initialized = true;
                    }
                }
                """,
              """
                public class BaseTest {
                    private final boolean initialized;

                    public BaseTest() {
                        initialized = true;
                    }
                }
                """
            )
          ),
          mavenProject("dependent",
            //language=java
            java(
              """
                class DependentFactory {
                    BaseTest create() {
                        return new BaseTest("testAdd");
                    }
                }
                """,
              """
                class DependentFactory {
                    BaseTest create() {
                        return new BaseTest();
                    }
                }
                """
            )
          ),
          //language=kotlin
          kotlin(
            """
              import junit.framework.TestCase

              class KotlinTest(name: String) : TestCase(name) {
                  fun create() = KotlinCalledTest("testAdd")

                  fun testExample() {
                      assertTrue(true)
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class DerivedTest extends CalledTest {
                  DerivedTest(String name) {
                      super(name);
                  }
              }
              """,
            """
              class DerivedTest extends CalledTest {
                  /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/DerivedTest(String name) {
                      super();
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              class EffectTest extends TestCase {
                  private final boolean initialized;

                  EffectTest(String name) {
                      super(name);
                      initialized = true;
                  }
              }
              """,
            """
              class EffectTest {
                  private final boolean initialized;

                  /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/EffectTest(String name) {
                      initialized = true;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              class EffectFactory {
                  private int counter;

                  EffectTest create() {
                      return new EffectTest(nextName());
                  }

                  String nextName() {
                      return "test" + counter++;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class KotlinCalledTest extends TestCase {
                  private final boolean initialized;

                  public KotlinCalledTest(String name) {
                      super(name);
                      initialized = true;
                  }
              }
              """,
            """
              public class KotlinCalledTest {
                  private final boolean initialized;

                  /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/public KotlinCalledTest(String name) {
                      initialized = true;
                  }
              }
              """
          ),
          mavenProject("ambiguous-first",
            //language=java
            java(
              """
                import junit.framework.TestCase;

                public class AmbiguousTest extends TestCase {
                    public AmbiguousTest(String name) {
                        super(name);
                        System.out.println("initialized");
                    }
                }
                """,
              """
                public class AmbiguousTest {
                    /*~~(JUnit Jupiter cannot resolve this String constructor parameter; migrate the test name and constructor callers manually)~~>*/public AmbiguousTest(String name) {
                        System.out.println("initialized");
                    }
                }
                """
            )
          ),
          mavenProject("ambiguous-second",
            spec -> spec.mapBeforeRecipe(source -> (SourceFile) new ChangeType(
              "AlternativeAmbiguousTest", "AmbiguousTest", false).getVisitor()
              .visitNonNull(source, new InMemoryExecutionContext())),
            //language=java
            java(
              """
                public class AlternativeAmbiguousTest {
                    public AlternativeAmbiguousTest(String name) {
                    }
                }
                """
            )
          ),
          mavenProject("ambiguous-caller",
            //language=java
            java(
              """
                class AmbiguousFactory {
                    AmbiguousTest create() {
                        return new AmbiguousTest("testAdd");
                    }
                }
                """
            )
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
}
