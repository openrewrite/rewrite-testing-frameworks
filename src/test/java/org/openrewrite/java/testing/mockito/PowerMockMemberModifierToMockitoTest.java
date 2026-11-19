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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class PowerMockMemberModifierToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12",
              "junit-4",
              "powermock-core-1",
              "powermock-api-mockito-1",
              "powermock-api-support-1",
              "powermock-module-junit4",
              "powermock-module-junit4-common",
              "powermock-reflect-1"))
          // TODO Resolve the missing types in the static `when` template of `PowerMockitoMockStaticToMockito`
          .typeValidationOptions(TypeValidation.builder()
            .identifiers(false)
            .methodInvocations(false)
            .build())
          .recipeFromResources("org.openrewrite.java.testing.mockito.ReplacePowerMockito");
    }

    //language=java
    private static final String UTILS = """
      import java.io.IOException;
      import java.io.InputStream;

      public class Utils {
          public static String root() {
              return "real";
          }

          public static byte[] toBytes(InputStream in) throws IOException {
              return new byte[0];
          }

          public static byte[] toBytes(String text) {
              return text.getBytes();
          }

          public static int parse(String text, int radix) {
              return Integer.parseInt(text, radix);
          }

          public static String join(String left, int times) {
              return left.repeat(times);
          }

          public static void reset() {
          }

          public String describe() {
              return "instance";
          }
      }
      """;

    @DocumentExample
    @Test
    void stubStaticMethodToReturn() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              import static org.junit.Assert.assertEquals;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test
                  public void stubsRoot() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "root")).toReturn("/var/task");
                      assertEquals("/var/task", Utils.root());
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Test;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              import static org.junit.Assert.assertEquals;

              public class UtilsTest {
                  private MockedStatic<Utils> mockedUtils;

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedUtils != null) {
                          mockedUtils.closeOnDemand();
                      }
                  }

                  @Test
                  public void stubsRoot() {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.root()).thenReturn("/var/task");
                      assertEquals("/var/task", Utils.root());
                  }
              }
              """
          )
        );
    }

    @Test
    void stubWithParametersThrowingAndSuppress() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import java.io.IOException;
              import java.io.InputStream;

              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              import static org.powermock.api.support.membermodification.MemberMatcher.method;
              import static org.powermock.api.support.membermodification.MemberModifier.stub;
              import static org.powermock.api.support.membermodification.MemberModifier.suppress;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test(expected = IOException.class)
                  public void throwsOnRead() throws IOException {
                      stub(method(Utils.class, "toBytes", InputStream.class)).toThrow(new IOException("boom"));
                      Utils.toBytes((InputStream) null);
                  }

                  @Test
                  public void picksTheOverloadByParameterType() {
                      stub(method(Utils.class, "toBytes", String.class)).toReturn(new byte[0]);
                  }

                  @Test
                  public void stubsPrimitives() {
                      stub(method(Utils.class, "parse", String.class, int.class)).toReturn(42);
                      suppress(method(Utils.class, "reset"));
                      Utils.reset();
                  }
              }
              """,
            """
              import java.io.IOException;
              import java.io.InputStream;

              import org.junit.After;
              import org.junit.Test;
              import org.mockito.ArgumentMatchers;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              public class UtilsTest {
                  private MockedStatic<Utils> mockedUtils;

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedUtils != null) {
                          mockedUtils.closeOnDemand();
                      }
                  }

                  @Test(expected = IOException.class)
                  public void throwsOnRead() throws IOException {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.toBytes(ArgumentMatchers.<InputStream>any())).thenThrow(new IOException("boom"));
                      Utils.toBytes((InputStream) null);
                  }

                  @Test
                  public void picksTheOverloadByParameterType() {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.toBytes(ArgumentMatchers.<String>any())).thenReturn(new byte[0]);
                  }

                  @Test
                  public void stubsPrimitives() {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.parse(ArgumentMatchers.any(), ArgumentMatchers.anyInt())).thenReturn(42);
                      mockedUtils.when(() -> Utils.reset()).thenAnswer(Mockito.RETURNS_DEFAULTS);
                      Utils.reset();
                  }
              }
              """
          )
        );
    }

    @Test
    void stubsOfOneTypeShareASingleSpy() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test
                  public void stubsTwo() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "root")).toReturn("a");
                      PowerMockito.stub(PowerMockito.method(Utils.class, "parse", String.class, int.class)).toReturn(1);
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Test;
              import org.mockito.ArgumentMatchers;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              public class UtilsTest {
                  private MockedStatic<Utils> mockedUtils;

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedUtils != null) {
                          mockedUtils.closeOnDemand();
                      }
                  }

                  @Test
                  public void stubsTwo() {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.root()).thenReturn("a");
                      mockedUtils.when(() -> Utils.parse(ArgumentMatchers.any(), ArgumentMatchers.anyInt())).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void stubInSetUpIsSharedWithTheTests() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Before;
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Before
                  public void setUp() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "root")).toReturn("a");
                  }

                  @Test
                  public void restubs() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "parse", String.class, int.class)).toReturn(1);
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;
              import org.mockito.ArgumentMatchers;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              public class UtilsTest {
                  private MockedStatic<Utils> mockedUtils;

                  @Before
                  public void setUp() {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.root()).thenReturn("a");
                  }

                  @After
                  public void tearDownStaticMocks() {
                      mockedUtils.closeOnDemand();
                  }

                  @Test
                  public void restubs() {
                      mockedUtils.when(() -> Utils.parse(ArgumentMatchers.any(), ArgumentMatchers.anyInt())).thenReturn(1);
                  }
              }
              """
          )
        );
    }

    @Test
    void instanceMethodsStayDisabled() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test
                  public void stubsInstance() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "describe")).toReturn("a");
                  }
              }
              """,
            """
              import org.junit.Ignore;
              import org.junit.Test;

              public class UtilsTest {
                  @Test
                  @Ignore("PowerMock test disabled by migration: rework it not to rely on private members")
                  public void stubsInstance() {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // PowerMockito.stub(PowerMockito.method(Utils.class, "describe")).toReturn("a");
                  }
              }
              """
          )
        );
    }

    @Test
    void keepsTheSpacingAroundTheStubbing() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test
                  public void stubsRoot() {
                      String before = Utils.root();

                      // The root of the lambda task
                      PowerMockito.stub(PowerMockito.method(Utils.class, "root")).toReturn("/var/task");

                      Utils.root();
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Test;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              public class UtilsTest {
                  private MockedStatic<Utils> mockedUtils;

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedUtils != null) {
                          mockedUtils.closeOnDemand();
                      }
                  }

                  @Test
                  public void stubsRoot() {
                      String before = Utils.root();

                      // The root of the lambda task
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.root()).thenReturn("/var/task");

                      Utils.root();
                  }
              }
              """
          )
        );
    }

    @Test
    void disablesATestThatAnotherTestCallsSinceItsStubsWouldClashWithTheCallersMock() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test
                  public void testCreate() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "root")).toReturn("a");
                  }

                  @Test
                  public void testUpdate() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "root")).toReturn("b");
                      testCreate();
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Ignore;
              import org.junit.Test;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              public class UtilsTest {
                  private MockedStatic<Utils> mockedUtils;

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedUtils != null) {
                          mockedUtils.closeOnDemand();
                      }
                  }

                  @Test
                  @Ignore("PowerMock test disabled by migration: rework it not to rely on private members")
                  public void testCreate() {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // PowerMockito.stub(PowerMockito.method(Utils.class, "root")).toReturn("a");
                  }

                  @Test
                  public void testUpdate() {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.root()).thenReturn("b");
                      testCreate();
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterTypesNeedNotBeSpelledOutForAMethodWithAUniqueName() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test
                  public void stubsJoin() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "join")).toReturn("joined");
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Test;
              import org.mockito.ArgumentMatchers;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;

              public class UtilsTest {
                  private MockedStatic<Utils> mockedUtils;

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedUtils != null) {
                          mockedUtils.closeOnDemand();
                      }
                  }

                  @Test
                  public void stubsJoin() {
                      mockedUtils = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
                      mockedUtils.when(() -> Utils.join(ArgumentMatchers.any(), ArgumentMatchers.anyInt())).thenReturn("joined");
                  }
              }
              """
          )
        );
    }

    @Test
    void anOverloadedNameWithoutParameterTypesStaysDisabled() {
        rewriteRun(
          java(UTILS),
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Utils.class)
              public class UtilsTest {
                  @Test
                  public void stubsToBytes() {
                      PowerMockito.stub(PowerMockito.method(Utils.class, "toBytes")).toReturn(new byte[0]);
                  }
              }
              """,
            """
              import org.junit.Ignore;
              import org.junit.Test;

              public class UtilsTest {
                  @Test
                  @Ignore("PowerMock test disabled by migration: rework it not to rely on private members")
                  public void stubsToBytes() {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // PowerMockito.stub(PowerMockito.method(Utils.class, "toBytes")).toReturn(new byte[0]);
                  }
              }
              """
          )
        );
    }

    @Test
    void stubStaticMethodInTestNgClass() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12",
              "powermock-core-1",
              "powermock-api-mockito-1",
              "powermock-api-support-1",
              "powermock-module-junit4",
              "powermock-module-junit4-common",
              "powermock-reflect-1",
              "testng-7")),
          java(
            """
              public class ApiUtils {
                  public static String service() {
                      return "real";
                  }
              }
              """),
          java(
            """
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.Assert;
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.Test;

              @PrepareForTest({ApiUtils.class})
              public class ApiTest {
                  private String service;

                  @BeforeClass
                  public void setup() {
                      this.service = "mocked";
                  }

                  @Test(description = "Testing the stubbed service.")
                  public void testService() throws Exception {
                      PowerMockito.stub(PowerMockito.method(ApiUtils.class, "service")).toReturn(this.service);
                      Assert.assertEquals(ApiUtils.service(), "mocked");
                  }
              }
              """,
            """
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.testng.Assert;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.Test;

              public class ApiTest {
                  private MockedStatic<ApiUtils> mockedApiUtils;
                  private String service;

                  @BeforeClass
                  public void setup() {
                      this.service = "mocked";
                  }

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      if (mockedApiUtils != null) {
                          mockedApiUtils.closeOnDemand();
                      }
                  }

                  @Test(description = "Testing the stubbed service.")
                  public void testService() throws Exception {
                      mockedApiUtils = Mockito.mockStatic(ApiUtils.class, Mockito.CALLS_REAL_METHODS);
                      mockedApiUtils.when(() -> ApiUtils.service()).thenReturn(this.service);
                      Assert.assertEquals(ApiUtils.service(), "mocked");
                  }
              }
              """
          )
        );
    }
}
