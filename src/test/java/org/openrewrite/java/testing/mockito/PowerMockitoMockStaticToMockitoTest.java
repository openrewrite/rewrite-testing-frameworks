/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.groovy.Assertions.groovy;
import static org.openrewrite.java.Assertions.java;

class PowerMockitoMockStaticToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "apiguardian-api-1.1",
              "junit-4.13",
              "junit-jupiter-api-5.9",
              "mockito-core-3.12",
              "powermock-api-mockito-1.6",
              "powermock-core-1.6",
              "testng-7.7"
            ))
          .recipe(new PowerMockitoMockStaticToMockito());
    }

    @DocumentExample
    @Test
    void prepareForTestAnnotationIsReplacedBySingleField() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              
              import org.junit.jupiter.api.Test;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              
              @PrepareForTest({Calendar.class})
              public class MyTest {
              
                  @Test
                  void testStaticMethod() {
                      mockStatic(Calendar.class);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class MyTest {
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  @BeforeEach
                  void setUpStaticMocks() {
                      mockedCalendar = mockStatic(Calendar.class);
                  }
              
                  @AfterEach
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                  }
              
                  @Test
                  void testStaticMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void prepareForTestAnnotationIsReplacedByFields() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              import java.util.Currency;
              
              import org.junit.jupiter.api.Test;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              
              @PrepareForTest({Calendar.class, Currency.class})
              class MyTest {
              
                  @Test
                  void testStaticMethod() {
                      mockStatic(Calendar.class);
                      mockStatic(Currency.class);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              import java.util.Currency;
              
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              class MyTest {
              
                  private MockedStatic<Currency> mockedCurrency;
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  @BeforeEach
                  void setUpStaticMocks() {
                      mockedCurrency = mockStatic(Currency.class);
                      mockedCalendar = mockStatic(Calendar.class);
                  }
              
                  @AfterEach
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                      mockedCurrency.closeOnDemand();
                  }
              
                  @Test
                  void testStaticMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void tearDownMethodHasCorrectPositionIfNoTestMethodIsPresent() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              
              @PrepareForTest({Calendar.class})
              class MyTest {
              
                  @BeforeEach
                  void testStaticMethod() {
                      mockStatic(Calendar.class);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockedStatic;
              
              class MyTest {
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  @BeforeEach
                  void testStaticMethod() {
                      mockedCalendar = mockStatic(Calendar.class);
                  }
              
                  @AfterEach
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                  }
              }
              """
          )
        );
    }

    @Test
    void tearDownMethodOfTestNGHasAnnotationWithArgument() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Calendar;
              
              import org.testng.annotations.Test;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              
              @PrepareForTest({Calendar.class})
              public class MyTest {
              
                  @Test
                  void testSomething() { }
              
              }
              """,
            """
              import java.util.Calendar;
              
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
              
              public class MyTest {
              
                  @BeforeMethod
                  void setUpStaticMocks() {
                  }
              
                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                  }
              
                  @Test
                  void testSomething() { }
              
              }
              """
          )
        );
    }

    @Test
    void tearDownMethodOfTestNGWithAnnotationRemainsUntouched() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Calendar;
              
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.Test;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              
              @PrepareForTest({Calendar.class})
              public class MyTest {
              
                  @AfterMethod(groups = "irrelevant")
                  void tearDown() {}
              
                  @Test
                  void testSomething() { }
              
              }
              """,
            """
              import java.util.Calendar;
              
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
              
              public class MyTest {
              
                  @AfterMethod(groups = "irrelevant")
                  void tearDown() {}
              
                  @BeforeMethod
                  void setUpStaticMocks() {
                  }
              
                  @Test
                  void testSomething() { }
              
              }
              """
          )
        );
    }

    @Test
    void tearDownMethodOfTestNGHasAnnotationWithSameArgumentsAsTheTestThatCallsMockStatic() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Calendar;
              
              import static org.mockito.Mockito.*;
              
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              
              @PrepareForTest({Calendar.class})
              public class MyTest {
              
                  private Calendar calendarMock;
              
                  @Test(groups = "irrelevant")
                  void testSomethingIrrelevantForCheckin() { }
              
                  @Test(groups = "checkin")
                  void testStaticMethod() {
                      calendarMock = mock(Calendar.class);
                      mockStatic(Calendar.class);
                      when(Calendar.getInstance()).thenReturn(calendarMock);
                  }
              }
              """,
            """
              import java.util.Calendar;
              
              import static org.mockito.Mockito.*;
              
              import org.mockito.MockedStatic;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
              
              public class MyTest {
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  private Calendar calendarMock;
              
                  @BeforeMethod(groups = "checkin")
                  void setUpStaticMocks() {
                      mockedCalendar = mockStatic(Calendar.class);
                  }
              
                  @AfterMethod(groups = "checkin")
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                  }
              
                  @Test(groups = "irrelevant")
                  void testSomethingIrrelevantForCheckin() { }
              
                  @Test(groups = "checkin")
                  void testStaticMethod() {
                      calendarMock = mock(Calendar.class);
                      mockedCalendar.when(Calendar::getInstance).thenReturn(calendarMock);
                  }
              }
              """
          )
        );
    }

    @Test
    void argumentOfWhenOnStaticMethodWithParametersIsReplacedByLambda() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.*;
              
              import java.util.Calendar;
              import java.util.Locale;
              
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class MyTest {
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  private Calendar calendarMock = mock(Calendar.class);
              
                  @Test
                  void testStaticMethod() {
                      when(Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.*;
              
              import java.util.Calendar;
              import java.util.Locale;
              
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class MyTest {
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  private Calendar calendarMock = mock(Calendar.class);
              
                  @Test
                  void testStaticMethod() {
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                  }
              }
              """
          )
        );
    }

    @Test
    void argumentOfWhenOnInstanceMethodsAreNotReplaced() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.*;
              
              import java.util.Calendar;
              import java.util.Locale;
              
              import org.junit.jupiter.api.Test;
              
              public class MyTest {
              
                  private Calendar calendarMock = mock(Calendar.class);
              
                  @Test
                  void testStaticMethod() {
                    when(calendarMock.toString()).thenReturn(null);
                  }
              }
              """
          )
        );
    }

    @Test
    void argumentOfVerifyOnParameterlessStaticMethodIsReplacedBySimpleLambda() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.*;
              
              import java.util.Currency;
              import java.util.Locale;
              
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class MyTest {
              
                  private MockedStatic<Currency> mockedCurrency;
              
                  private Currency currencyMock = mock(Currency.class);
              
                  @Test
                  void testStaticMethod() {
                      verify(Currency.getInstance(Locale.ENGLISH), never());
                      verify(Currency.getAvailableCurrencies(), atLeastOnce());
                  }
              }
              """,
            """
              import static org.mockito.Mockito.*;
              
              import java.util.Currency;
              import java.util.Locale;
              
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class MyTest {
              
                  private MockedStatic<Currency> mockedCurrency;
              
                  private Currency currencyMock = mock(Currency.class);
              
                  @Test
                  void testStaticMethod() {
                      mockedCurrency.verify(() -> Currency.getInstance(Locale.ENGLISH), never());
                      mockedCurrency.verify(Currency::getAvailableCurrencies, atLeastOnce());
                  }
              }
              """
          )
        );
    }

    @Test
    void interfacesAndAbstractClassesWithEmptyMethodBodiesRemainsUntouched() {
        //language=java
        rewriteRun(java(
            """
              public interface MyInterface {
              
                  void checkThis();
              
              }
              """)
          , java(
            """
              public abstract class MyAbstractClass {
              
                  public boolean isItTrue() { return true; }
              
                  public abstract boolean isItImplemented();
              
              }
              """
          ));
    }

    @Test
    void extensionOfPowerMockTestCaseGetsRemoved() {
        //language=java
        rewriteRun(java(
            """
              package org.powermock.modules.testng;
              
              public class PowerMockTestCase {}
              """
          ),
          java(
            """
              import org.powermock.modules.testng.PowerMockTestCase;
              
              public class MyPowerMockTestCase extends PowerMockTestCase {}
              """,
            """
              public class MyPowerMockTestCase {}
              """)
        );
    }

    @Test
    void extensionOfPowerMockConfigurationGetsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              package org.powermock.configuration;
              
              public class PowerMockConfiguration {}
              """
          ),
          java(
            """
              import org.powermock.configuration.PowerMockConfiguration;
              
              public class MyPowerMockConfiguration extends PowerMockConfiguration {}
              """,
            """
              public class MyPowerMockConfiguration {}
              """
          ));
    }

    @Test
    void mockStaticInTryWithResources() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              import java.nio.file.Files;
              
              import static org.mockito.Mockito.mockStatic;
              
              class A {
                @Test
                void testTryWithResource() {
                  try (MockedStatic<Files> mocked = mockStatic(Files.class)) {
                    // test logic that uses mocked
                  }
                }
              }
              """
          )
        );
    }

    @Test
    void shouldNotDuplicateVarsAndMethods() {
        //language=java
        rewriteRun(
          java(
            """
              package test;
              
              public class A {
                public static class B {
                    public static String helloWorld() {
                        return "Hello World";
                    }
                }
              }
              """
          ),
          java(
            """
              import org.junit.Before;
              import org.junit.Test;
              import org.mockito.Mockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import test.A;
              
              @PrepareForTest({ A.B.class })
              public class MyTest {
              
                  private static final String TEST_MESSAGE = "this is a test message";
              
                  @Before
                  void setUp() {
                      Mockito.mockStatic(A.B.class);
                  }
              
                  @Test
                  public void testStaticMethod() {
                  }
              }
              """,

            """
              import org.junit.Before;
              import org.junit.Test;
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import test.A;

              public class MyTest {

                  private MockedStatic<A.B> mockedA_B;

                  private static final String TEST_MESSAGE = "this is a test message";

                  @Before
                  void setUp() {
                  }

                  @BeforeEach
                  void setUpStaticMocks() {
                      mockedA_B = Mockito.mockStatic(A.B.class);
                  }

                  @AfterEach
                  void tearDownStaticMocks() {
                      mockedA_B.closeOnDemand();
                  }

                  @Test
                  public void testStaticMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/611")
    void existingMockitoMockStaticShouldNotBeTouched() {
        //language=java
        rewriteRun(
          java(
            """
            import static org.mockito.Mockito.mockStatic;

            import org.mockito.MockedStatic;

            class TestClass {
                MockedStatic<String> mocked = mockStatic(String.class);
            }
            """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/358")
    void doesNotExplodeOnTopLevelMethodDeclaration() {
        rewriteRun(
          groovy(
            "def myFun() { }"
          )
        );
    }
}
