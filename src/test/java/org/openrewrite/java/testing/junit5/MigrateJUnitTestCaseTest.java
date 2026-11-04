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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
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

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.fail;

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
    void removeRedundantConstructorAndRewriteCaller() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class DirectTest extends TestCase {
                  public DirectTest(String name) {
                      super(name);
                  }

                  public static DirectTest create() {
                      return new DirectTest("math");
                  }
              }
              """,
            """
              public class DirectTest {

                  public static DirectTest create() {
                      return new DirectTest();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeNoArgumentConstructorDelegatingToRedundantOne() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class NoArgumentChainTest extends TestCase {
                  public NoArgumentChainTest() {
                      this("default");
                  }

                  public NoArgumentChainTest(String name) {
                      super(name);
                  }

                  public static NoArgumentChainTest create() {
                      return new NoArgumentChainTest("test");
                  }
              }
              """,
            """
              public class NoArgumentChainTest {

                  public static NoArgumentChainTest create() {
                      return new NoArgumentChainTest();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeRedundantConstructorsWithExtraParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class MultipleParametersTest extends TestCase {
                  public MultipleParametersTest(String name, int unused) {
                      super(name);
                  }

                  public MultipleParametersTest(String name, int unused, long alsoUnused) {
                      this(name, unused);
                  }

                  public static MultipleParametersTest create() {
                      return new MultipleParametersTest("test", 0, 0L);
                  }
              }
              """,
            """
              public class MultipleParametersTest {

                  public static MultipleParametersTest create() {
                      return new MultipleParametersTest();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepNoArgumentConstructorForRetainedOverload() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class RetainedOverloadTest extends TestCase {
                  public RetainedOverloadTest() {
                      this("default", 0);
                  }

                  public RetainedOverloadTest(String name, int unused) {
                      super(name);
                  }

                  public RetainedOverloadTest(int value) {
                      this("test", value);
                      System.out.println(value);
                  }
              }
              """,
            """
              public class RetainedOverloadTest {
                  public RetainedOverloadTest() {
                  }

                  public RetainedOverloadTest(int value) {
                      this();
                      System.out.println(value);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class OverloadedTest extends TestCase {
                  public OverloadedTest(String name) {
                      super(name);
                  }

                  public OverloadedTest(int value) {
                      this("math");
                      System.out.println(value);
                  }
              }
              """,
            """
              public class OverloadedTest {
                  public OverloadedTest() {
                  }

                  public OverloadedTest(int value) {
                      this();
                      System.out.println(value);
                  }
              }
              """
          )
        );
    }

    @Test
    void keepVisibilityAndAnnotationsOfLastRedundantConstructor() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class PrivateAnnotatedTest extends TestCase {
                  @Deprecated
                  private PrivateAnnotatedTest(String name, int unused) {
                      super(name);
                  }

                  public static PrivateAnnotatedTest create() {
                      return new PrivateAnnotatedTest("test", 0);
                  }
              }
              """,
            """
              public class PrivateAnnotatedTest {
                  @Deprecated
                  private PrivateAnnotatedTest() {
                  }

                  public static PrivateAnnotatedTest create() {
                      return new PrivateAnnotatedTest();
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class PrivateGenericNameTest extends TestCase {
                  private <T> PrivateGenericNameTest(String name) {
                      super(name);
                  }

                  public static PrivateGenericNameTest create() {
                      return new PrivateGenericNameTest("test");
                  }
              }
              """,
            """
              public class PrivateGenericNameTest {
                  private <T> PrivateGenericNameTest() {
                  }

                  public static PrivateGenericNameTest create() {
                      return new PrivateGenericNameTest();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeGenericConstructorWithoutGenericParameters() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class GenericNameTest extends TestCase {
                  public <T> GenericNameTest(String name) {
                      super(name);
                  }

                  public static GenericNameTest create() {
                      return new GenericNameTest("test");
                  }
              }
              """,
            """
              public class GenericNameTest {

                  public static GenericNameTest create() {
                      return new GenericNameTest();
                  }
              }
              """
          )
        );
    }

    @Test
    void removeConstructorDelegatingToNoArgumentBase() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class NoArgumentBase extends TestCase {
                  public NoArgumentBase() {
                      super();
                  }
              }
              """,
            """
              public class NoArgumentBase {
              }
              """
          ),
          //language=java
          java(
            """
              public class NoArgumentChild extends NoArgumentBase {
                  public NoArgumentChild(String name, int unused) {
                      super();
                  }

                  public static NoArgumentChild create() {
                      return new NoArgumentChild("test", 0);
                  }
              }
              """,
            """
              public class NoArgumentChild extends NoArgumentBase {

                  public static NoArgumentChild create() {
                      return new NoArgumentChild();
                  }
              }
              """
          )
        );
    }

    @Test
    void rewriteCallersInDependentModule() {
        rewriteRun(
          mavenProject("base-module",
            //language=java
            java(
              """
                package example;
                import junit.framework.TestCase;

                public abstract class BaseTest extends TestCase {
                    public BaseTest(String name) {
                        super(name);
                    }
                }
                """,
              """
                package example;

                public abstract class BaseTest {
                }
                """
            )
          ),
          mavenProject("dependent-module",
            //language=java
            java(
              """
                package example;

                public class MathTest extends BaseTest {
                    public MathTest(String name) {
                        super(name);
                    }
                }
                """,
              """
                package example;

                public class MathTest extends BaseTest {
                }
                """
            ),
            //language=java
            java(
              """
                package example;

                class Caller {
                    MathTest create() {
                        return new MathTest("math");
                    }
                }
                """,
              """
                package example;

                class Caller {
                    MathTest create() {
                        return new MathTest();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void migrateAnonymousSubclassCaller() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class MathTest extends TestCase {
                  public MathTest(String name) {
                      super(name);
                  }

                  static MathTest create() {
                      return new MathTest("math") {
                      };
                  }
              }
              """,
            """
              public class MathTest {

                  static MathTest create() {
                      return new MathTest() {
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void dropSuperCallToRemovedBaseConstructor() {
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
              public class MathTest extends BaseTest {
                  private boolean initialized;

                  public MathTest(String name) {
                      super(name);
                      initialized = true;
                  }
              }
              """,
            """
              public class MathTest extends BaseTest {
                  private boolean initialized;

                  public MathTest(String name) {
                      initialized = true;
                  }
              }
              """
          )
        );
    }

    @Nested
    class RetainConstructor {
        @Test
        void whenNoArgumentConstructorDoesMore() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class RetainedDefaultTest extends TestCase {
                      public RetainedDefaultTest() {
                          System.out.println("default");
                      }

                      public RetainedDefaultTest(String name, int unused) {
                          super(name);
                      }

                      public static RetainedDefaultTest create() {
                          return new RetainedDefaultTest("test", 0);
                      }
                  }
                  """,
                """
                  public class RetainedDefaultTest {
                      public RetainedDefaultTest() {
                          System.out.println("default");
                      }

                      public RetainedDefaultTest(String name, int unused) {
                      }

                      public static RetainedDefaultTest create() {
                          return new RetainedDefaultTest("test", 0);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class DefaultBehaviorTest extends TestCase {
                      public DefaultBehaviorTest(String name) {
                          super(name);
                      }

                      public DefaultBehaviorTest() {
                          System.out.println("default");
                      }

                      public static DefaultBehaviorTest create() {
                          return new DefaultBehaviorTest("math");
                      }
                  }
                  """,
                """
                  public class DefaultBehaviorTest {
                      public DefaultBehaviorTest(String name) {
                      }

                      public DefaultBehaviorTest() {
                          System.out.println("default");
                      }

                      public static DefaultBehaviorTest create() {
                          return new DefaultBehaviorTest("math");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenCallerPassesArgumentOtherThanLiteralOrLocal() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class UnsafeArgumentTest extends TestCase {
                      public UnsafeArgumentTest(String name, int unused) {
                          super(name);
                      }

                      public static UnsafeArgumentTest create() {
                          return new UnsafeArgumentTest("test", Integer.parseInt("0"));
                      }
                  }
                  """,
                """
                  public class UnsafeArgumentTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public UnsafeArgumentTest(String name, int unused) {
                      }

                      public static UnsafeArgumentTest create() {
                          return new UnsafeArgumentTest("test", Integer.parseInt("0"));
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class FieldArgumentTest extends TestCase {
                      static int value;

                      public FieldArgumentTest(String name, int unused) {
                          super(name);
                      }

                      public static FieldArgumentTest create() {
                          return new FieldArgumentTest("test", value);
                      }
                  }
                  """,
                """
                  public class FieldArgumentTest {
                      static int value;

                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public FieldArgumentTest(String name, int unused) {
                      }

                      public static FieldArgumentTest create() {
                          return new FieldArgumentTest("test", value);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class SideEffectTest extends TestCase {
                      public SideEffectTest(String name) {
                          super(name);
                      }

                      public static SideEffectTest create() {
                          return new SideEffectTest(System.getProperty("example.name"));
                      }
                  }
                  """,
                """
                  public class SideEffectTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public SideEffectTest(String name) {
                      }

                      public static SideEffectTest create() {
                          return new SideEffectTest(System.getProperty("example.name"));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenDelegationPassesArgumentOtherThanLiteralOrLocal() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class UnsafeDelegationTest extends TestCase {
                      public UnsafeDelegationTest(String name, int unused) {
                          super(name);
                      }

                      public UnsafeDelegationTest() {
                          this("test", Integer.parseInt("0"));
                      }
                  }
                  """,
                """
                  public class UnsafeDelegationTest {
                      public UnsafeDelegationTest(String name, int unused) {
                      }

                      public UnsafeDelegationTest() {
                          this("test", Integer.parseInt("0"));
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenConstructorDeclaresException() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class CheckedConstructorTest extends TestCase {
                      public CheckedConstructorTest(String name, int unused) throws java.io.IOException {
                          super(name);
                      }

                      public static CheckedConstructorTest create() throws java.io.IOException {
                          return new CheckedConstructorTest("test", 0);
                      }
                  }
                  """,
                """
                  public class CheckedConstructorTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public CheckedConstructorTest(String name, int unused) throws java.io.IOException {
                      }

                      public static CheckedConstructorTest create() throws java.io.IOException {
                          return new CheckedConstructorTest("test", 0);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenNoArgumentConstructorIsPrivateOrAnnotated() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class PrivateNoArgumentTest extends TestCase {
                      private PrivateNoArgumentTest() {
                          this("test", 0);
                      }

                      public PrivateNoArgumentTest(String name, int unused) {
                          super(name);
                      }

                      public static PrivateNoArgumentTest create() {
                          return new PrivateNoArgumentTest("test", 0);
                      }
                  }
                  """,
                """
                  public class PrivateNoArgumentTest {
                      private PrivateNoArgumentTest() {
                          this("test", 0);
                      }

                      public PrivateNoArgumentTest(String name, int unused) {
                      }

                      public static PrivateNoArgumentTest create() {
                          return new PrivateNoArgumentTest("test", 0);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class AnnotatedNoArgumentTest extends TestCase {
                      @Deprecated
                      public AnnotatedNoArgumentTest() {
                          this("test", 0);
                      }

                      public AnnotatedNoArgumentTest(String name, int unused) {
                          super(name);
                      }

                      public static AnnotatedNoArgumentTest create() {
                          return new AnnotatedNoArgumentTest("test", 0);
                      }
                  }
                  """,
                """
                  public class AnnotatedNoArgumentTest {
                      @Deprecated
                      public AnnotatedNoArgumentTest() {
                          this("test", 0);
                      }

                      public AnnotatedNoArgumentTest(String name, int unused) {
                      }

                      public static AnnotatedNoArgumentTest create() {
                          return new AnnotatedNoArgumentTest("test", 0);
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenSignatureIsVarargsOrGeneric() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class VarargsConstructorTest extends TestCase {
                      public VarargsConstructorTest(String name, Object... unused) {
                          super(name);
                      }

                      public static VarargsConstructorTest create() {
                          return new VarargsConstructorTest("test", 0);
                      }
                  }
                  """,
                """
                  public class VarargsConstructorTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public VarargsConstructorTest(String name, Object... unused) {
                      }

                      public static VarargsConstructorTest create() {
                          return new VarargsConstructorTest("test", 0);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class GenericConstructorTest extends TestCase {
                      private <T> GenericConstructorTest(String name, T unused) {
                          super(name);
                      }

                      public static GenericConstructorTest create() {
                          return new GenericConstructorTest("test", "unused");
                      }
                  }
                  """,
                """
                  public class GenericConstructorTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/private <T> GenericConstructorTest(String name, T unused) {
                      }

                      public static GenericConstructorTest create() {
                          return new GenericConstructorTest("test", "unused");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenReferencedAsMethodReference() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;
                  import java.util.function.Function;

                  public class FactoryTest extends TestCase {
                      public FactoryTest(String name) {
                          super(name);
                      }

                      public static Function<String, FactoryTest> factory() {
                          return FactoryTest::new;
                      }
                  }

                  class Other {
                      Other(String name) {
                      }

                      static Other create() {
                          return new Other("other");
                      }
                  }
                  """,
                """
                  import java.util.function.Function;

                  public class FactoryTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public FactoryTest(String name) {
                      }

                      public static Function<String, FactoryTest> factory() {
                          return FactoryTest::new;
                      }
                  }

                  class Other {
                      Other(String name) {
                      }

                      static Other create() {
                          return new Other("other");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenCalledFromKotlin() {
            rewriteRun(
              spec -> spec.parser(KotlinParser.builder()
                .classpathFromResources(new InMemoryExecutionContext(), "junit-4")
                .dependsOn("class MixedTest(name: String) : junit.framework.TestCase(name)")),
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class MixedTest extends TestCase {
                      public MixedTest(String name) {
                          super(name);
                      }
                  }
                  """,
                """
                  public class MixedTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public MixedTest(String name) {
                      }
                  }
                  """
              ),
              //language=kotlin
              kotlin(
                """
                  fun createMixedTest() = MixedTest("mixed")
                  """
              )
            );
        }

        @Test
        void whenDeclaredInMoreThanOneModule() {
            rewriteRun(
              mavenProject("first-module",
                //language=java
                java(
                  """
                    import junit.framework.TestCase;

                    public class SharedTest extends TestCase {
                        public SharedTest(String name) {
                            super(name);
                        }

                        public static SharedTest create() {
                            return new SharedTest("shared");
                        }
                    }
                    """,
                  """
                    public class SharedTest {
                        /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public SharedTest(String name) {
                        }

                        public static SharedTest create() {
                            return new SharedTest("shared");
                        }
                    }
                    """
                )
              ),
              mavenProject("second-module",
                //language=java
                java(
                  """
                    public class OtherSharedTest {
                        public OtherSharedTest(String name) {
                        }
                    }
                    """,
                  spec -> spec.mapBeforeRecipe(cu -> (J.CompilationUnit) new ChangeType("OtherSharedTest", "SharedTest", false)
                    .getVisitor().visitNonNull(cu, new InMemoryExecutionContext()))
                )
              )
            );
        }

        @Test
        void whenBaseConstructorIsRetained() {
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
                      public BaseTest(String name) {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MathTest extends BaseTest {
                      public MathTest(String name) {
                          super(name);
                      }
                  }
                  """,
                """
                  public class MathTest extends BaseTest {
                      /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public MathTest(String name) {
                          super(name);
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  class Caller {
                      BaseTest create() {
                          return new BaseTest(System.getProperty("example.name")) {
                          };
                      }
                  }
                  """
              )
            );
        }

        @Test
        void whenNestedInClassOtherThanTestCase() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class Outer {
                      public static class InnerTest extends TestCase {
                          public InnerTest(String name) {
                              super(name);
                          }
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).contains("public InnerTest(String name)").actual())
              ),
              //language=java
              java(
                """
                  class Caller {
                      Outer.InnerTest create() {
                          return new Outer.InnerTest("inner");
                      }
                  }
                  """
              )
            );
        }
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
    void retainConstructorCalledFromTestCaseOverridingRun() {
        rewriteRun(
          //language=java
          java(
            """
              import junit.framework.TestCase;

              public class RedundantTest extends TestCase {
                  public RedundantTest(String name) {
                      super(name);
                  }
              }
              """,
            """
              public class RedundantTest {
                  /*~~(JUnit Jupiter cannot instantiate a test class whose only constructor takes a String; migrate the remaining constructor callers manually)~~>*/public RedundantTest(String name) {
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import junit.framework.TestCase;
              import junit.framework.TestResult;

              public class CustomRunnerTest extends TestCase {
                  private final RedundantTest child = new RedundantTest("testExample");

                  @Override
                  public void run(TestResult result) {
                      super.run(result);
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

                  public MultiThreadedTest(String name) {
                      super(name);
                  }

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
          ),
          //language=java
          java(
            """
              class Caller {
                  MultiThreadedTest create() {
                      return new MultiThreadedTest("testSomething");
                  }
              }

              class RegularTest extends junit.framework.TestCase {
              }
              """,
            """
              class Caller {
                  MultiThreadedTest create() {
                      return new MultiThreadedTest("testSomething");
                  }
              }

              class RegularTest {
              }
              """
          )
        );
    }
}
