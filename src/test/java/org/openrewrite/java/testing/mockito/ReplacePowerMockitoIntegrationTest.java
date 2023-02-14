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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplacePowerMockitoIntegrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12.4",
              "junit-jupiter-api-5.9.2",
              "junit-4.13.2",
              "powermock-core-1.7.4",
              "powermock-api-mockito-1.7.4",
              "testng-7.7.1"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.mockito")
            .build()
            .activateRecipes("org.openrewrite.java.testing.mockito.ReplacePowerMockito"));
    }

    @Test
    void testThatPowerMockitoIsReplacedInJunitTests() {
        //language=java
        rewriteRun(java("""
          package org.powermock.modules.junit4;

          public class PowerMockRunner {}
          """
        ), java(
          """
              import static org.mockito.Mockito.*;
              import static org.powermock.api.mockito.PowerMockito.mockStatic;
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              import java.util.Calendar;
              import java.util.Currency;
              import java.util.Locale;
              
              import org.junit.runner.RunWith;
              import org.junit.jupiter.api.Test;
              import org.powermock.core.classloader.annotations.PowerMockIgnore;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;
              
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
              
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class StaticMethodTest {
              
                  private MockedStatic<Currency> mockedCurrency;
                  
                  private MockedStatic<Calendar> mockedCalendar;
                  
                  private Calendar calendarMock = mock(Calendar.class);
              
                  @BeforeEach
                  void setUp() {
                      mockedCalendar = mockStatic(Calendar.class);
                      mockedCurrency = mockStatic(Currency.class);
                  }
              
                  @AfterEach
                  void tearDown() {
                      mockedCalendar.close();
                      mockedCurrency.close();
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
        ));
    }

    @Test
    void testThatPowerMockitoIsReplacedInTestNGTests() {
        //language=java
        rewriteRun(java("""
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
                    void setUp() {
                        mockedCalendar = mockStatic(Calendar.class);
                        mockedCurrency = mockStatic(Currency.class);
                    }
                
                    @AfterMethod
                    void tearDown() {
                        mockedCalendar.close();
                        mockedCurrency.close();
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
          ));
    }

    @Test
    void testThatPowerMockitoMockStaticIsReplaced() {
        //language=java
        rewriteRun(java(
          """
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              import static org.testng.Assert.assertEquals;
              
              import java.util.Calendar;
              import java.util.Currency;
              import java.util.Locale;
              
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
              
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.Test;
              
              @PrepareForTest(value = {Calendar.class, Currency.class})
              public class StaticMethodTest {
              
                  private MockedStatic<Currency> mockedCurrency;
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  private Calendar calendarMock;
                  
                  @BeforeClass
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }
                  
                  @BeforeMethod
                  void setUp() {
                        mockedCalendar = Mockito.mockStatic(Calendar.class);
                        mockedCurrency = Mockito.mockStatic(Currency.class);
                  }
                
                  @AfterMethod
                  void tearDown() {
                      mockedCalendar.close();
                      mockedCurrency.close();
                  }
                 
                  @Test
                  void testWithCalendar() {
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                      mockedCurrency.verify(Currency::getAvailableCurrencies, never());
                  }
              
              }
            """
        ));
    }
}
