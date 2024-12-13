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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ReplacePowerMockitoIntegrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12",
              "junit-jupiter-api-5.9",
              "junit-4.13",
              "powermock-core-1.6",
              "powermock-api-mockito-1.6",
              "powermock-api-support-1.6",
              "testng-7.7"))
          .typeValidationOptions(TypeValidation.builder()
            .cursorAcyclic(false)
            // TODO Resolve the missing types in the replacement templates rather than ignore the errors here
            .identifiers(false)
            .methodInvocations(false)
            .build())
          .recipeFromResources("org.openrewrite.java.testing.mockito.ReplacePowerMockito");
    }

    @Test
    void thatPowerMockitoIsReplacedInJunitTests() {
        //language=java
        rewriteRun(
          java(
            """
              package org.powermock.modules.junit4;

              public class PowerMockRunner {}
              """
          ),
          java(
            """
              import static org.mockito.Mockito.*;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              import java.util.Calendar;
              import java.util.Currency;
              import java.util.Locale;
              
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class StaticMethodTest {
              
                  private MockedStatic<Currency> mockedCurrency;
                  
                  private MockedStatic<Calendar> mockedCalendar;
                  
                  private Calendar calendarMock = mock(Calendar.class);
              
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
                  void testWithCalendar() {
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                  }
                  
                  @Test
                  void testWithCurrency() {
                      mockedCurrency.verify(Currency::getAvailableCurrencies, never());
                  }
              
              }
              """
          )
        );
    }

    @Test
    void thatPowerMockitoIsReplacedInTestNGTests() {
        //language=java
        rewriteRun(
          java(
            """
              package org.powermock.modules.junit4;

              public class PowerMockRunner {}
              """
          ),
          java(
            """
              import static org.mockito.Mockito.*;
              import static org.powermock.api.mockito.PowerMockito.mockStatic;
              import static org.junit.jupiter.api.Assertions.assertEquals;
                            
              import java.util.Calendar;
              import java.util.Currency;
              import java.util.Locale;
                            
              import org.junit.runner.RunWith;
              import org.powermock.core.classloader.annotations.PowerMockIgnore;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;
              import org.testng.annotations.Test;
                            
              @RunWith(PowerMockRunner.class)
              @PowerMockIgnore({"org.apache.*", "com.sun.*", "javax.*"})
              @PrepareForTest(value = {Calendar.class, Currency.class})
              public class StaticMethodTest {
                            
                  private Calendar calendarMock = mock(Calendar.class);
                            
                  @Test
                  void testWithCalendar() {
                      mockStatic(Calendar.class);
                      when(Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                  }
                  
                  @Test
                  void testWithCurrency() {
                      mockStatic(Currency.class);
                      verify(Currency.getAvailableCurrencies(), never());
                  }
                            
              }
              """,
            """
              import static org.mockito.Mockito.*;
              import static org.junit.jupiter.api.Assertions.assertEquals;
                            
              import java.util.Calendar;
              import java.util.Currency;
              import java.util.Locale;
                            
              import org.mockito.MockedStatic;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                            
              public class StaticMethodTest {
                            
                  private MockedStatic<Currency> mockedCurrency;
                  
                  private MockedStatic<Calendar> mockedCalendar;
                  
                  private Calendar calendarMock = mock(Calendar.class);
                            
                  @BeforeMethod
                  void setUpStaticMocks() {
                      mockedCurrency = mockStatic(Currency.class);
                      mockedCalendar = mockStatic(Calendar.class);
                  }
                            
                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                      mockedCurrency.closeOnDemand();
                  }
                  
                  @Test
                  void testWithCalendar() {
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                  }
                  
                  @Test
                  void testWithCurrency() {
                      mockedCurrency.verify(Currency::getAvailableCurrencies, never());
                  }
                            
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void thatPowerMockitoMockStaticIsReplacedInTestMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.testng.Assert.assertEquals;
                            
              import java.util.Calendar;
              import java.util.Currency;
              import java.util.Locale;
                            
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.Test;
                            
              @PrepareForTest(value = {Calendar.class, Currency.class})
              public class StaticMethodTest {
                            
                  private Calendar calendarMock;
                  
                  @BeforeClass
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }
                  
                  @Test
                  void testWithCalendar() {
                      PowerMockito.mockStatic(Calendar.class);
                      PowerMockito.mockStatic(Currency.class);
                      Mockito.when(Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                      Mockito.verify(Currency.getAvailableCurrencies(), Mockito.never());
                  }
              }
              """,
            """
              import static org.testng.Assert.assertEquals;
                            
              import java.util.Calendar;
              import java.util.Currency;
              import java.util.Locale;
                            
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                            
              public class StaticMethodTest {
                            
                  private MockedStatic<Currency> mockedCurrency;
                            
                  private MockedStatic<Calendar> mockedCalendar;
                            
                  private Calendar calendarMock;
                  
                  @BeforeClass
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }
                  
                  @BeforeMethod
                  void setUpStaticMocks() {
                      mockedCurrency = Mockito.mockStatic(Currency.class);
                      mockedCalendar = Mockito.mockStatic(Calendar.class);
                  }
                
                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                      mockedCurrency.closeOnDemand();
                  }
                 
                  @Test
                  void testWithCalendar() {
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                      mockedCurrency.verify(Currency::getAvailableCurrencies, Mockito.never());
                  }
              }
              """
          )
        );
    }

    @Test
    void thatPowerMockitoMockStaticIsReplacedInSetUpMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.testng.Assert.assertEquals;
                            
              import java.util.Calendar;
                            
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                            
              @PrepareForTest(value = {Calendar.class})
              public class StaticMethodTest {
                            
                  private Calendar calendarMock;
                  
                  @BeforeMethod
                  void setUp() {
                      PowerMockito.mockStatic(Calendar.class);
                      calendarMock = Mockito.mock(Calendar.class);
                      Mockito.when(Calendar.getInstance()).thenReturn(calendarMock);
                  }
                  
                  @Test
                  void testWithCalendar() {
                      assertEquals(Calendar.getInstance(), calendarMock);
                      Mockito.verify(Calendar.getInstance());
                  }
              }
              """,
            """
              import static org.testng.Assert.assertEquals;
                            
              import java.util.Calendar;
                            
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                            
              public class StaticMethodTest {
                            
                  private MockedStatic<Calendar> mockedCalendar;
                            
                  private Calendar calendarMock;
                  
                  @BeforeMethod
                  void setUp() {
                      mockedCalendar = Mockito.mockStatic(Calendar.class);
                      calendarMock = Mockito.mock(Calendar.class);
                      mockedCalendar.when(Calendar::getInstance).thenReturn(calendarMock);
                  }
                  
                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                  }
                 
                  @Test
                  void testWithCalendar() {
                      assertEquals(Calendar.getInstance(), calendarMock);
                      mockedCalendar.verify(Calendar::getInstance);
                  }
              }
              """
          )
        );
    }

    @Test
    void thatPowerMockitoSpyIsReplaced() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.testng.Assert.assertEquals;
                            
              import java.util.Calendar;
              import java.util.Locale;
                            
              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                            
              @PrepareForTest(value = {Calendar.class})
              public class StaticMethodTest {
                            
                  private Calendar calendarMock;
                  
                  @BeforeMethod
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }
                  
                  @Test
                  void testWithCalendar() {
                      PowerMockito.spy(Calendar.class);
                      PowerMockito.mockStatic(Calendar.class);
                      PowerMockito.when(Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                  }
                            
              }
              """,
            """
              import static org.testng.Assert.assertEquals;
                            
              import java.util.Calendar;
              import java.util.Locale;
                            
              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                            
              public class StaticMethodTest {
                            
                  private MockedStatic<Calendar> mockedCalendar;
                            
                  private Calendar calendarMock;
                  
                  @BeforeMethod
                  void setUp() {
                      mockedCalendar = Mockito.mockStatic(Calendar.class);
                      calendarMock = Mockito.mock(Calendar.class);
                  }
                
                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                  }
                 
                  @Test
                  void testWithCalendar() {
                      Mockito.spy(Calendar.class);
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                  }
                            
              }
              """
          )
        );
    }

    @Test
    void staticMethodReturningArrayShallNotThrowAnIllegalArgumentException() {
        //language=java
        rewriteRun(
          java(
            """
              package foo;
              public class StringFilter {
                   public static String[] splitFilterStringValues(String filterValue) {
                     if (filterValue.equals("")) {
                       return new String[0];
                     } else {
                       return filterValue.split(".");
                     }
                   }
              }
              """
          ),
          java(
            """
              import static org.mockito.Mockito.*;

              import foo.StringFilter;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.Test;

              @PrepareForTest(value = {StringFilter.class})
              public class MyTest {
                  @Test
                  public void testStaticMock() {
                      mockStatic(StringFilter.class);
                      when(StringFilter.splitFilterStringValues(anyString())).thenReturn(new String[]{"Fee", "Faa", "Foo"});
                  }
              }
              """,
            """
              import static org.mockito.Mockito.*;
              
              import foo.StringFilter;
              import org.mockito.MockedStatic;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                          
              public class MyTest {
                          
                  private MockedStatic<StringFilter> mockedStringFilter;
                          
                  @BeforeMethod
                  void setUpStaticMocks() {
                      mockedStringFilter = mockStatic(StringFilter.class);
                  }
                          
                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      mockedStringFilter.closeOnDemand();
                  }
                  @Test
                  public void testStaticMock() {
                      mockedStringFilter.when(() -> StringFilter.splitFilterStringValues(anyString())).thenReturn(new String[]{"Fee", "Faa", "Foo"});
                  }
              }
              """
          )
        );
    }

    @Test
    void verifyOnMocksRemainsUntouched() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.spy;
              import static org.mockito.Mockito.verify;
              import static org.mockito.internal.verification.VerificationModeFactory.times;
              import java.util.Calendar;
              import java.util.Date;
                          
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                          
              public class MyTest {
                          
                private Calendar cut;
                          
                @BeforeMethod
                void setUp() {
                  cut = spy(Calendar.getInstance());
                }
                 @Test
                  public void testCalendar() {
                     cut.getTime();
                     verify(cut, times(1)).getTimeInMillis();
                  }
              }
              """
          )
        );
    }

    @Test
    void dynamicPowerMockitoWhenCallsGetReplaced() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.mockito.Mockito.any;
              import static org.mockito.Mockito.mock;
              import static org.powermock.api.mockito.PowerMockito.*;
                          
              import java.util.Calendar;
              import java.util.Locale;
                          
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.Test;
                          
              @PrepareForTest({Calendar.class})
              public class MyTest {
                          
                  @Test
                  public void testCalendarDynamic() throws Exception {
                      Calendar calendarMock = mock(Calendar.class);
                      mockStatic(Calendar.class);
                      when(Calendar.class, "getInstance", any(Locale.class)).thenReturn(calendarMock);
                  }
              }
              """,
            """
              import static org.mockito.Mockito.*;
              
              import java.util.Calendar;
              import java.util.Locale;
                          
              import org.mockito.MockedStatic;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;
                          
              public class MyTest {
                          
                  private MockedStatic<Calendar> mockedCalendar;
                          
                  @BeforeMethod
                  void setUpStaticMocks() {
                      mockedCalendar = mockStatic(Calendar.class);
                  }
                          
                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                  }
                          
                  @Test
                  public void testCalendarDynamic() throws Exception {
                      Calendar calendarMock = mock(Calendar.class);
                      mockedCalendar.when(() -> Calendar.getInstance(any(Locale.class))).thenReturn(calendarMock);
                  }
              }
              """
          )
        );
    }

    @Test
    void powerMockitoCallsAreReplacedByMockitoCalls() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;
                         
              public class MyTest {
                         
                  private Calendar calendarMock;
                         
                  @BeforeEach
                  void setUp() {
                      calendarMock = PowerMockito.mock(Calendar.class);
                      PowerMockito.doCallRealMethod().when(calendarMock).getTime();
                      PowerMockito.doNothing().when(calendarMock).clear();
                      PowerMockito.doThrow(new NullPointerException()).when(calendarMock.getCalendarType());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.Mockito;
              import java.util.Calendar;
                         
              public class MyTest {
                         
                  private Calendar calendarMock;
                         
                  @BeforeEach
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                      Mockito.doCallRealMethod().when(calendarMock).getTime();
                      Mockito.doNothing().when(calendarMock).clear();
                      Mockito.doThrow(new NullPointerException()).when(calendarMock.getCalendarType());
                  }
              }
              """
          )
        );
    }
}
