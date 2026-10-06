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
package org.openrewrite.java.testing.mockito;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

class ReplacePowerMockitoIntegrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .beforeRecipe(withToolingApi())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12",
              "junit-jupiter-api-5",
              "junit-4",
              "powermock-core-1",
              "powermock-api-mockito-1",
              "powermock-api-support-1",
              "powermock-module-junit4",
              "powermock-reflect-1",
              "testng-7"))
          .typeValidationOptions(TypeValidation.builder()
            .cursorAcyclic(false)
            // TODO Resolve the missing types in the replacement templates rather than ignore the errors here
            .identifiers(false)
            .methodInvocations(false)
            .build())
          .recipeFromResources("org.openrewrite.java.testing.mockito.ReplacePowerMockito");
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
              class StaticMethodTest {

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
              import org.testng.annotations.Test;

              class StaticMethodTest {
                  private MockedStatic<Calendar> mockedCalendar;
                  private MockedStatic<Currency> mockedCurrency;

                  private Calendar calendarMock;

                  @BeforeClass
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      if (mockedCalendar != null) {
                          mockedCalendar.closeOnDemand();
                      }
                      if (mockedCurrency != null) {
                          mockedCurrency.closeOnDemand();
                      }
                  }

                  @Test
                  void testWithCalendar() {
                      mockedCalendar = Mockito.mockStatic(Calendar.class);
                      mockedCurrency = Mockito.mockStatic(Currency.class);
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
    void thatPowerMockitoIsReplacedInJunitTests() {
        //language=java
        rewriteRun(
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

              class StaticMethodTest {

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
              class StaticMethodTest {

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
              import org.testng.annotations.Test;

              class StaticMethodTest {
                  private MockedStatic<Calendar> mockedCalendar;
                  private MockedStatic<Currency> mockedCurrency;

                  private Calendar calendarMock = mock(Calendar.class);

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      if (mockedCalendar != null) {
                          mockedCalendar.closeOnDemand();
                      }
                      if (mockedCurrency != null) {
                          mockedCurrency.closeOnDemand();
                      }
                  }

                  @Test
                  void testWithCalendar() {
                      mockedCalendar = mockStatic(Calendar.class);
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                  }

                  @Test
                  void testWithCurrency() {
                      mockedCurrency = mockStatic(Currency.class);
                      mockedCurrency.verify(Currency::getAvailableCurrencies, never());
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
              class StaticMethodTest {

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

              class StaticMethodTest {
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
              class StaticMethodTest {

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

              class StaticMethodTest {
                  private MockedStatic<Calendar> mockedCalendar;

                  private Calendar calendarMock;

                  @BeforeMethod
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      if (mockedCalendar != null) {
                          mockedCalendar.closeOnDemand();
                      }
                  }

                  @Test
                  void testWithCalendar() {
                      mockedCalendar = Mockito.mockStatic(Calendar.class, Mockito.CALLS_REAL_METHODS);
                      mockedCalendar.closeOnDemand();
                      mockedCalendar = Mockito.mockStatic(Calendar.class);
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
                   static String[] splitFilterStringValues(String filterValue) {
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
              class MyTest {
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
              import org.testng.annotations.Test;

              class MyTest {
                  private MockedStatic<StringFilter> mockedStringFilter;

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      if (mockedStringFilter != null) {
                          mockedStringFilter.closeOnDemand();
                      }
                  }

                  @Test
                  public void testStaticMock() {
                      mockedStringFilter = mockStatic(StringFilter.class);
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

              class MyTest {

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
              class MyTest {

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
              import org.testng.annotations.Test;

              class MyTest {
                  private MockedStatic<Calendar> mockedCalendar;

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      if (mockedCalendar != null) {
                          mockedCalendar.closeOnDemand();
                      }
                  }

                  @Test
                  public void testCalendarDynamic() throws Exception {
                      Calendar calendarMock = mock(Calendar.class);
                      mockedCalendar = mockStatic(Calendar.class);
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

              class MyTest {

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

              class MyTest {

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

    @Test
    void whenNew() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import static org.powermock.api.mockito.PowerMockito.*;

              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  static class Generator {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }
                  @Test
                  void testNumbers() throws Exception {
                      Generator mock = mock(Generator.class);
                      PowerMockito.whenNew(Generator.class).withNoArguments().thenReturn(mock);

                      Generator gen = new Generator();
                      when(gen.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen.getLuckyNumber());
                  }

                  public final String otherMethod() {
                    return "no change here";
                  }
              }
              """,
            """
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.mock;

              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  private MockedConstruction<Generator> mockedConstructionGenerator;

                  static class Generator {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }

                  @AfterEach
                  void tearDownStaticMocks() {
                      if (mockedConstructionGenerator != null) {
                          mockedConstructionGenerator.closeOnDemand();
                      }
                  }

                  @Test
                  void testNumbers() throws Exception {
                      Generator mock = mock(Generator.class);
                      mockedConstructionGenerator = Mockito.mockConstructionWithAnswer(Generator.class, AdditionalAnswers.delegatesTo(mock));

                      Generator gen = new Generator();
                      when(gen.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen.getLuckyNumber());
                  }

                  public final String otherMethod() {
                    return "no change here";
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNewTwoMocks() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import static org.powermock.api.mockito.PowerMockito.*;

              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  static class Generator1 {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }
                  static class Generator2 {
                      public int getLuckyNumber() {
                        return 136;
                      }
                  }

                  @Test
                  void testNumbers() throws Exception {
                      Generator1 mock1 = mock(Generator1.class);
                      PowerMockito.whenNew(Generator1.class).withNoArguments().thenReturn(mock1);

                      Generator1 gen1 = new Generator1();
                      when(gen1.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen1.getLuckyNumber());

                      Generator2 mock2 = mock(Generator2.class);
                      PowerMockito.whenNew(Generator2.class).withNoArguments().thenReturn(mock2);

                      Generator2 gen2 = new Generator2();
                      when(gen2.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen2.getLuckyNumber());
                  }
              }
              """,
            """
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.mock;

              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  private MockedConstruction<Generator1> mockedConstructionGenerator1;
                  private MockedConstruction<Generator2> mockedConstructionGenerator2;

                  static class Generator1 {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }
                  static class Generator2 {
                      public int getLuckyNumber() {
                        return 136;
                      }
                  }

                  @AfterEach
                  void tearDownStaticMocks() {
                      if (mockedConstructionGenerator1 != null) {
                          mockedConstructionGenerator1.closeOnDemand();
                      }
                      if (mockedConstructionGenerator2 != null) {
                          mockedConstructionGenerator2.closeOnDemand();
                      }
                  }

                  @Test
                  void testNumbers() throws Exception {
                      Generator1 mock1 = mock(Generator1.class);
                      mockedConstructionGenerator1 = Mockito.mockConstructionWithAnswer(Generator1.class, AdditionalAnswers.delegatesTo(mock1));

                      Generator1 gen1 = new Generator1();
                      when(gen1.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen1.getLuckyNumber());

                      Generator2 mock2 = mock(Generator2.class);
                      mockedConstructionGenerator2 = Mockito.mockConstructionWithAnswer(Generator2.class, AdditionalAnswers.delegatesTo(mock2));

                      Generator2 gen2 = new Generator2();
                      when(gen2.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen2.getLuckyNumber());
                  }
              }
              """
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"withArguments(\"Have a nice day!\")", "withAnyArguments()"})
    void whenNewWithArguments(String methodCall) {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import static org.powermock.api.mockito.PowerMockito.*;

              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest2 {
                  static class SomeTexts {
                      String text;
                      public SomeTexts(String text) { this.text = text; }
                      public String getText() { return text; }
                  }

                  @Test
                  void testWords() throws Exception {
                      SomeTexts mock = PowerMockito.mock(SomeTexts.class);
                      PowerMockito.whenNew(SomeTexts.class).METHODCALL.thenReturn(mock);

                      SomeTexts st = new SomeTexts("Have a nice day!");
                      when(st.getText()).thenReturn("overridden");

                      assertEquals("overridden", st.getText());
                  }
              }
              """.replaceAll("METHODCALL", methodCall),
            """
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;
              import static org.mockito.Mockito.when;

              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest2 {
                  private MockedConstruction<SomeTexts> mockedConstructionSomeTexts;

                  static class SomeTexts {
                      String text;
                      public SomeTexts(String text) { this.text = text; }
                      public String getText() { return text; }
                  }

                  @AfterEach
                  void tearDownStaticMocks() {
                      if (mockedConstructionSomeTexts != null) {
                          mockedConstructionSomeTexts.closeOnDemand();
                      }
                  }

                  @Test
                  void testWords() throws Exception {
                      SomeTexts mock = Mockito.mock(SomeTexts.class);
                      mockedConstructionSomeTexts = Mockito.mockConstructionWithAnswer(SomeTexts.class, AdditionalAnswers.delegatesTo(mock));

                      SomeTexts st = new SomeTexts("Have a nice day!");
                      when(st.getText()).thenReturn("overridden");

                      assertEquals("overridden", st.getText());
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/785")
    @Test
    void whenNewWithFieldAccess() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import static org.powermock.api.mockito.PowerMockito.*;

              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  static class Generator {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }
                  @Test
                  void testNumbers() throws Exception {
                      Generator mock = mock(MyTest.Generator.class);
                      PowerMockito.whenNew(MyTest.Generator.class).withAnyArguments().thenReturn(mock);

                      Generator gen = new Generator();
                      when(gen.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen.getLuckyNumber());
                  }
              }
              """,
            """
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.mock;

              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  private MockedConstruction<Generator> mockedConstructionGenerator;

                  static class Generator {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }

                  @AfterEach
                  void tearDownStaticMocks() {
                      if (mockedConstructionGenerator != null) {
                          mockedConstructionGenerator.closeOnDemand();
                      }
                  }

                  @Test
                  void testNumbers() throws Exception {
                      Generator mock = mock(MyTest.Generator.class);
                      mockedConstructionGenerator = Mockito.mockConstructionWithAnswer(MyTest.Generator.class, AdditionalAnswers.delegatesTo(mock));

                      Generator gen = new Generator();
                      when(gen.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen.getLuckyNumber());
                  }
              }
              """
          )
        );
    }


    @ParameterizedTest
    @ValueSource(strings = {
      "import org.powermock.api.mockito.PowerMockito;",
      "import static org.powermock.api.mockito.PowerMockito.whenNew;",
      "import static org.powermock.api.mockito.PowerMockito.*;",
      "import org.powermock.core.classloader.annotations.PrepareForTest;",
      "import org.powermock.modules.junit4.PowerMockRunner;"
    })
    void removeUnusedPowerMockImports(String importStatement) {
        //language=java
        rewriteRun(
          java(
            """
              %s

              class MyTest {}
              """.formatted(importStatement),
            """
              class MyTest {}
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/1926")
    @Test
    void addsMockitoCoreWhenOnlyTransitiveThroughPowerMock() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.powermock:powermock-api-mockito:1.6.5")
                  testImplementation("org.powermock:powermock-core:1.6.5")
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.mockito:mockito-inline:3.12.4")
              }
              """
          ),
          //language=java
          java(
            """
              import static org.testng.Assert.assertEquals;

              import java.util.Calendar;
              import java.util.Locale;

              import org.mockito.Mockito;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.Test;

              @PrepareForTest(value = {Calendar.class})
              class StaticMethodTest {

                  private Calendar calendarMock;

                  @BeforeClass
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }

                  @Test
                  void testWithCalendar() {
                      PowerMockito.mockStatic(Calendar.class);
                      Mockito.when(Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
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
              import org.testng.annotations.BeforeClass;
              import org.testng.annotations.Test;

              class StaticMethodTest {
                  private MockedStatic<Calendar> mockedCalendar;

                  private Calendar calendarMock;

                  @BeforeClass
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                  }

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      if (mockedCalendar != null) {
                          mockedCalendar.closeOnDemand();
                      }
                  }

                  @Test
                  void testWithCalendar() {
                      mockedCalendar = Mockito.mockStatic(Calendar.class);
                      mockedCalendar.when(() -> Calendar.getInstance(Locale.ENGLISH)).thenReturn(calendarMock);
                      assertEquals(Calendar.getInstance(Locale.ENGLISH), calendarMock);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesPowerMockDependencyWithMockitoCoreWhenNoInlineMockingNeeded() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.powermock:powermock-api-mockito:1.6.5")
                  testImplementation("org.powermock:powermock-core:1.6.5")
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.mockito:mockito-core:3.12.4")
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-api-mockito</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-core</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                </dependencies>
              </project>
              """
          ),
          //language=java
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarMock;

                  @BeforeEach
                  void setUp() {
                      calendarMock = PowerMockito.mock(Calendar.class);
                      PowerMockito.doCallRealMethod().when(calendarMock).getTime();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.mockito.Mockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarMock;

                  @BeforeEach
                  void setUp() {
                      calendarMock = Mockito.mock(Calendar.class);
                      Mockito.doCallRealMethod().when(calendarMock).getTime();
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesPowerMockDependencyWithMockitoInlineWhenMockStaticDetected() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.powermock:powermock-api-mockito:1.6.5")
                  testImplementation("org.powermock:powermock-core:1.6.5")
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.mockito:mockito-inline:3.12.4")
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-api-mockito</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-core</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-inline</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                </dependencies>
              </project>
              """
          ),
          //language=java
          java(
            """
              import static org.testng.Assert.assertNotNull;

              import java.util.Calendar;

              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;

              @PrepareForTest(value = {Calendar.class})
              class StaticMethodTest {

                  @BeforeMethod
                  void setUp() {
                      PowerMockito.mockStatic(Calendar.class);
                  }

                  @Test
                  void testWithCalendar() {
                      assertNotNull(Calendar.getInstance());
                  }
              }
              """,
            """
              import static org.testng.Assert.assertNotNull;

              import java.util.Calendar;

              import org.mockito.MockedStatic;
              import org.mockito.Mockito;
              import org.testng.annotations.AfterMethod;
              import org.testng.annotations.BeforeMethod;
              import org.testng.annotations.Test;

              class StaticMethodTest {
                  private MockedStatic<Calendar> mockedCalendar;

                  @BeforeMethod
                  void setUp() {
                      mockedCalendar = Mockito.mockStatic(Calendar.class);
                  }

                  @AfterMethod(alwaysRun = true)
                  void tearDownStaticMocks() {
                      mockedCalendar.closeOnDemand();
                  }

                  @Test
                  void testWithCalendar() {
                      assertNotNull(Calendar.getInstance());
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesPowerMockDependencyWithMockitoInlineWhenWhenNewDetected() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.powermock:powermock-api-mockito:1.6.5")
                  testImplementation("org.powermock:powermock-core:1.6.5")
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.mockito:mockito-inline:3.12.4")
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-api-mockito</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-core</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-inline</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                </dependencies>
              </project>
              """
          ),
          //language=java
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import static org.powermock.api.mockito.PowerMockito.*;

              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  static class Generator {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }
                  @Test
                  void testNumbers() throws Exception {
                      Generator mock = mock(Generator.class);
                      PowerMockito.whenNew(Generator.class).withNoArguments().thenReturn(mock);

                      Generator gen = new Generator();
                      when(gen.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen.getLuckyNumber());
                  }
              }
              """,
            """
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;
              import static org.mockito.Mockito.when;
              import static org.mockito.Mockito.mock;

              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertEquals;

              class MyTest {
                  private MockedConstruction<Generator> mockedConstructionGenerator;

                  static class Generator {
                      public int getLuckyNumber() {
                        return 436;
                      }
                  }

                  @AfterEach
                  void tearDownStaticMocks() {
                      if (mockedConstructionGenerator != null) {
                          mockedConstructionGenerator.closeOnDemand();
                      }
                  }

                  @Test
                  void testNumbers() throws Exception {
                      Generator mock = mock(Generator.class);
                      mockedConstructionGenerator = Mockito.mockConstructionWithAnswer(Generator.class, AdditionalAnswers.delegatesTo(mock));

                      Generator gen = new Generator();
                      when(gen.getLuckyNumber()).thenReturn(504);

                      assertEquals(504, gen.getLuckyNumber());
                  }
              }
              """
          )
        );
    }

    @Test
    void replacesPowerMockDependencyWithMockitoInlineWhenPrepareForTestDetected() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.powermock:powermock-api-mockito:1.6.5")
                  testImplementation("org.powermock:powermock-core:1.6.5")
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.mockito:mockito-inline:3.12.4")
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-api-mockito</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-core</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-inline</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                </dependencies>
              </project>
              """
          ),
          //language=java
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import java.util.Calendar;

              @PrepareForTest(Calendar.class)
              class MyTest {
                  Calendar mock;

                  @BeforeEach
                  void setUp() {
                      mock = PowerMockito.mock(Calendar.class);
                  }

                  @Test
                  void testFinalClass() {
                      assertNotNull(mock);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.Mockito;

              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import java.util.Calendar;

              class MyTest {
                  Calendar mock;

                  @BeforeEach
                  void setUp() {
                      mock = Mockito.mock(Calendar.class);
                  }

                  @Test
                  void testFinalClass() {
                      assertNotNull(mock);
                  }
              }
              """
          )
        );
    }

    @Test
    void removesAllPowerMockDependencies() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.powermock:powermock-api-mockito:1.6.5")
                  testImplementation("org.powermock:powermock-core:1.6.5")
                  testImplementation("org.powermock:powermock-api-support:1.6.5")
                  testImplementation("org.powermock:powermock-reflect:1.6.5")
                  testImplementation("org.powermock:powermock-module-junit4:1.6.5")
                  testImplementation("org.powermock:powermock-api-mockito-common:1.6.5")
                  testImplementation("org.powermock:powermock-classloading-xstream:1.6.5")
                  testImplementation("org.powermock:powermock:1.6.5")
                  testImplementation("org.powermock:powermock-modules:1.6.5")
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.mockito:mockito-core:3.12.4")
              }
              """
          ),
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-api-mockito</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-module-junit4</artifactId>
                        <version>1.6.5</version>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>3.12.4</version>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void multiModuleProjectGetsCorrectDependencyPerModule() {
        rewriteRun(
          spec -> spec.recipe(new ReplacePowerMockDependencies()),
          mavenProject("module-a",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>module-a</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.powermock</groupId>
                          <artifactId>powermock-api-mockito</artifactId>
                          <version>1.6.5</version>
                      </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>module-a</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-inline</artifactId>
                          <version>3.12.4</version>
                      </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """
              )
            )
          ),
          mavenProject("module-b",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>module-b</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.powermock</groupId>
                          <artifactId>powermock-api-mockito</artifactId>
                          <version>1.6.5</version>
                      </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>module-b</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-core</artifactId>
                          <version>3.12.4</version>
                      </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class RegularMockTest {
                      void test() {
                          Calendar mock = PowerMockito.mock(Calendar.class);
                      }
                  }
                  """
              )
            )
          ),
          mavenProject("module-c",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>module-c</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>junit</groupId>
                          <artifactId>junit</artifactId>
                          <version>4.13.2</version>
                      </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void removesManagedPowerMockDependencies() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.powermock</groupId>
                            <artifactId>powermock-core</artifactId>
                            <version>1.6.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.powermock</groupId>
                            <artifactId>powermock-module-junit4</artifactId>
                            <version>1.6.5</version>
                        </dependency>
                        <dependency>
                            <groupId>org.powermock</groupId>
                            <artifactId>powermock-api-mockito</artifactId>
                            <version>1.6.5</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-core</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-module-junit4</artifactId>
                    </dependency>
                    <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-api-mockito</artifactId>
                    </dependency>
                </dependencies>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>some-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>3.12.4</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                    </dependency>
                </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void thatSuppressStaticInitializationForIsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
              import org.testng.annotations.Test;

              @SuppressStaticInitializationFor("com.example.MyClass")
              class StaticInitTest {

                  @Test
                  void testSomething() {
                  }
              }
              """,
            """
              import org.testng.annotations.Test;

              class StaticInitTest {

                  @Test
                  void testSomething() {
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/moderneinc/customer-requests/issues/2358")
    @Test
    void migratableUsageStillMigratesAlongsideUnsupportedUsage() {
        //language=java
        rewriteRun(
          java(
            """
              class MyService {
                  private String name;
              }
              """
          ),
          java(
            """
              import org.powermock.reflect.Whitebox;

              class MyServiceTest {
                  void test() {
                      MyService service = new MyService();
                      Whitebox.setInternalState(service, "name", "value");
                      MyService other = Whitebox.newInstance(MyService.class);
                  }
              }
              """,
            """
              import org.powermock.reflect.Whitebox;

              import java.lang.reflect.Field;

              class MyServiceTest {
                  void test() throws Exception {
                      MyService service = new MyService();
                      Field nameField = MyService.class.getDeclaredField("name");
                      nameField.setAccessible(true);
                      nameField.set(service, "value");
                      /* TODO `Whitebox.newInstance` could not be migrated automatically; migrate it manually to replace PowerMock */
                      MyService other = Whitebox.newInstance(MyService.class);
                  }
              }
              """
          )
        );
    }

    @Test
    void remainingPowerMockCallsAndAnnotationsAreReplaced() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;

              import org.junit.Test;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;

              @PrepareForTest(fullyQualifiedNames = "com.example.*")
              public class MyTest {
                  @Test
                  public void test() {
                      List<?> list = PowerMockito.mock(List.class);
                      PowerMockito.verifyZeroInteractions(list);
                      PowerMockito.verifyNoMoreInteractions(list);
                  }
              }
              """,
            """
              import java.util.List;

              import org.junit.Test;
              import org.mockito.Mockito;

              public class MyTest {
                  @Test
                  public void test() {
                      List<?> list = Mockito.mock(List.class);
                      Mockito.verifyZeroInteractions(list);
                      Mockito.verifyNoMoreInteractions(list);
                  }
              }
              """
          )
        );
    }

    @Test
    void parentManagedPowerMockVersionsAreReplacedConsistently() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0</version>
                <packaging>pom</packaging>
                <modules>
                  <module>child</module>
                </modules>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-module-junit4</artifactId>
                      <version>1.6.5</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito</artifactId>
                      <version>1.6.5</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
              </project>
              """,
            """
              <project>
                <groupId>org.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0</version>
                <packaging>pom</packaging>
                <modules>
                  <module>child</module>
                </modules>
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-inline</artifactId>
                      <version>3.12.4</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
              </project>
              """
          ),
          mavenProject("child",
            //language=xml
            pomXml(
              """
                <project>
                  <parent>
                    <groupId>org.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                  </parent>
                  <artifactId>child</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-module-junit4</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("powermock")
                .contains("<artifactId>mockito-inline</artifactId>")
                .actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }

    @Test
    void unsupportedUsageIsCommentedOutSoPowerMockCanStillBeRemoved() {
        rewriteRun(
          mavenProject("unsupported",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>unsupported</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.powermock</groupId>
                          <artifactId>powermock-api-mockito</artifactId>
                          <version>1.6.5</version>
                      </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.jupiter.api.Test;
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      @Test
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                          PowerMockito.verifyNew(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          ),
          mavenProject("supported",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>supported</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.powermock</groupId>
                          <artifactId>powermock-api-mockito</artifactId>
                          <version>1.6.5</version>
                      </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;

                  class MockTest {
                      void test() {
                          PowerMockito.mock(Object.class);
                      }
                  }
                  """,
                """
                  import org.mockito.Mockito;

                  class MockTest {
                      void test() {
                          Mockito.mock(Object.class);
                      }
                  }
                  """
              )
            )
          )
        );
    }
    @Test
    void anOlderMockitoDeclarationIsBroughtUpToTheVersionMockitoInlineNeeds() {
        rewriteRun(
          mavenProject("legacy-mockito",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>legacy-mockito</artifactId>
                  <version>1.0</version>
                  <properties>
                    <powermock.version>1.6.5</powermock.version>
                    <mockito.version>1.10.19</mockito.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito</artifactId>
                      <version>${powermock.version}</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-all</artifactId>
                      <version>${mockito.version}</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              // `mockito-inline` only selects the mock maker; a stale `mockito-core` declaration would win
              // dependency mediation and leave `MockedStatic` off the compile classpath entirely. `mockito-all`
              // has no 3.x release at all, so it has to become `mockito-core` before it can be upgraded.
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("org.powermock")
                .doesNotContain("mockito-all")
                .contains("<artifactId>mockito-inline</artifactId>")
                .contains("<artifactId>mockito-core</artifactId>")
                .contains("<mockito.version>3.12.4</mockito.version>")
                .actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }
    @Test
    void mockitoAllBroughtInByAnotherDependencyIsExcluded() {
        rewriteRun(
          mavenProject("leaky-library",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>leaky-library</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.github.rlon008</groupId>
                      <artifactId>testamation-test-common</artifactId>
                      <version>1.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito</artifactId>
                      <version>1.6.5</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              // That library declares `mockito-all` 1.x in compile scope, and as an unshaded uber jar listed ahead
              // of `mockito-core` its `org.mockito.Mockito`, which has no `mockStatic`, is the one that resolves.
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("powermock")
                .containsPattern("<artifactId>testamation-test-common</artifactId>\\s*<version>1\\.0</version>\\s*<exclusions>\\s*<exclusion>\\s*<groupId>org\\.mockito</groupId>\\s*<artifactId>mockito-all</artifactId>")
                .contains("<artifactId>mockito-inline</artifactId>")
                .actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }

    @Test
    void anAlreadyDeclaredMockitoCoreIsNotDeclaredAgain() {
        rewriteRun(
          mavenProject("declares-mockito",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>declares-mockito</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito</artifactId>
                      <version>1.5.6</version>
                      <scope>provided</scope>
                    </dependency>
                    <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                      <version>4.13.2</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-core</artifactId>
                      <version>5.14.2</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              // Maven rejects a second `mockito-core` even in another scope, so the one already declared is kept
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>declares-mockito</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>junit</groupId>
                      <artifactId>junit</artifactId>
                      <version>4.13.2</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-core</artifactId>
                      <version>5.14.2</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Test;
                  import org.powermock.api.mockito.PowerMockito;

                  import java.util.List;

                  public class ListTest {
                      @Test
                      public void mocksAList() {
                          List<?> list = PowerMockito.mock(List.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }

    @Test
    void anAlreadyManagedMockitoCoreIsNotManagedAgain() {
        rewriteRun(
          mavenProject("manages-mockito",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>manages-mockito</artifactId>
                  <version>1.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.powermock</groupId>
                        <artifactId>powermock-api-mockito</artifactId>
                        <version>1.6.5</version>
                      </dependency>
                      <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>5.14.2</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>manages-mockito</artifactId>
                  <version>1.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.mockito</groupId>
                        <artifactId>mockito-core</artifactId>
                        <version>5.14.2</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-core</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Test;
                  import org.powermock.api.mockito.PowerMockito;

                  import java.util.List;

                  public class ListTest {
                      @Test
                      public void mocksAList() {
                          List<?> list = PowerMockito.mock(List.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }

    @Test
    void mockitoInlineIsNotAddedWhereMockito5IsAlreadyResolved() {
        rewriteRun(
          mavenProject("on-mockito-5",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>on-mockito-5</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito2</artifactId>
                      <version>2.0.9</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-junit-jupiter</artifactId>
                      <version>5.14.2</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              // The `mockito-core` 5 that `mockito-junit-jupiter` brings in mocks statics without `mockito-inline`
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>on-mockito-5</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-junit-jupiter</artifactId>
                      <version>5.14.2</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }

    @Test
    void mockitoInlineIsNotAddedWhereGradleAlreadyResolvesMockito5() {
        rewriteRun(
          //language=groovy
          buildGradle(
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
                  testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
              }
              """,
            """
              plugins {
                  id 'java-library'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
              }
              """
          ),
          //language=java
          java(
            """
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class StaticMockTest {
                  void test() {
                      PowerMockito.mockStatic(Calendar.class);
                  }
              }
              """,
            spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
          )
        );
    }

    @Test
    void anOlderMockitoInlineIsUpgradedToOneWithStaticMocking() {
        rewriteRun(
          mavenProject("old-inline",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>old-inline</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito2</artifactId>
                      <version>2.0.9</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-inline</artifactId>
                      <version>2.28.2</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>old-inline</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.mockito</groupId>
                      <artifactId>mockito-inline</artifactId>
                      <version>3.12.4</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }

    @Test
    void thePowerMockVersionPropertyIsRemovedOnceNothingReferencesIt() {
        rewriteRun(
          // A cycle scans before it edits, so the property only looks unreferenced on the cycle after the
          // dependencies that used it are gone; `ReplacePowerMockDependencies` asks for that cycle.
          spec -> spec.expectedCyclesThatMakeChanges(2),
          mavenProject("versioned-by-property",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>versioned-by-property</artifactId>
                  <version>1.0</version>
                  <properties>
                    <powermock.version>1.6.5</powermock.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-module-junit4</artifactId>
                      <version>${powermock.version}</version>
                      <scope>test</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.powermock</groupId>
                      <artifactId>powermock-api-mockito</artifactId>
                      <version>${powermock.version}</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("powermock")
                .contains("<artifactId>mockito-inline</artifactId>")
                .actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }


    @Test
    void explicitMockitoCoreIsUpgradedAlongWithMockitoInline() {
        rewriteRun(
          mavenProject("some-project",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>some-project</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                    <mockito.version>2.28.2</mockito.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-core</artifactId>
                          <version>${mockito.version}</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>org.powermock</groupId>
                          <artifactId>powermock-api-mockito2</artifactId>
                          <version>2.0.2</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
                </project>
                """,
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>some-project</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                    <mockito.version>3.12.4</mockito.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-core</artifactId>
                          <version>${mockito.version}</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-inline</artifactId>
                          <version>3.12.4</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.powermock.api.mockito.PowerMockito;
                  import java.util.Calendar;

                  class StaticMockTest {
                      void test() {
                          PowerMockito.mockStatic(Calendar.class);
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).contains("Mockito.mockStatic(Calendar.class)").actual())
              )
            )
          )
        );
    }

    @Test
    void mockitoCoreIsNotUpgradedWithoutPowerMock() {
        rewriteRun(
          mavenProject("some-project",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>some-project</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-core</artifactId>
                          <version>2.28.2</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
                </project>
                """
            )
          )
        );
    }

    @Test
    void staticMockMigratedFromPowerMockIsReusedForStubbingThatThrowsCheckedException() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.mockito.Mockito1to4Migration"),
          //language=java
          java(
            """
              package com.example;

              public class Keys {
                  public static String build(char[] key) throws Exception {
                      return new String(key);
                  }
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;

              import org.junit.Before;
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              import static org.powermock.api.mockito.PowerMockito.mockStatic;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Keys.class)
              public class SignerTest {
                  @Before
                  public void setUp() throws Exception {
                      mockStatic(Keys.class);
                      Mockito.when(Keys.build(Mockito.any(char[].class))).thenReturn("key");
                  }

                  @Test
                  public void signs() {
                  }
              }
              """,
            spec -> spec.after(actual -> assertThat(actual)
              .containsOnlyOnce("mockStatic(Keys.class)")
              .contains("Mockito.when(Keys.build(Mockito.any(char[].class))).thenReturn(\"key\");")
              .actual())
          )
        );
    }

    @Test
    void staticImportOfStubbedMethodIsKept() {
        //language=java
        rewriteRun(
          java(
            """
              package com.example;

              public class Requests {
                  public static String buildRequest(String path) {
                      return path;
                  }
              }
              """
          ),
          java(
            """
              package com.example;

              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              import static com.example.Requests.buildRequest;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Requests.class)
              public class RequestsTest {
                  @Test
                  public void stubsTheRequest() {
                      PowerMockito.mockStatic(Requests.class);
                      PowerMockito.when(buildRequest("users")).thenReturn("stubbed");
                  }
              }
              """,
            spec -> spec.after(actual -> assertThat(actual)
              .contains("mockedRequests.when(() -> buildRequest(\"users\"))")
              .contains("import static com.example.Requests.buildRequest;")
              .actual())
          )
        );
    }

    @Test
    void junit4IsKeptWhenOnlyPowerMockBroughtItIn() {
        rewriteRun(
          mavenProject("some-project",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>some-project</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.powermock</groupId>
                          <artifactId>powermock-module-junit4</artifactId>
                          <version>1.6.5</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>org.powermock</groupId>
                          <artifactId>powermock-api-mockito</artifactId>
                          <version>1.6.5</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> assertThat(actual)
                .doesNotContain("powermock")
                .containsPattern("<groupId>junit</groupId>\\s*<artifactId>junit</artifactId>\\s*<version>4\\.13\\.2</version>\\s*<scope>test</scope>")
                .actual())
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Test;
                  import org.junit.runner.RunWith;
                  import org.powermock.modules.junit4.PowerMockRunner;

                  @RunWith(PowerMockRunner.class)
                  public class MyTest {
                      @Test
                      public void test() {
                      }
                  }
                  """,
                spec -> spec.after(actual -> assertThat(actual).doesNotContain("powermock").actual())
              )
            )
          )
        );
    }

    @Test
    void junit4IsNotAddedWithoutPowerMock() {
        rewriteRun(
          mavenProject("some-project",
            //language=xml
            pomXml(
              """
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>some-project</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.mockito</groupId>
                          <artifactId>mockito-core</artifactId>
                          <version>3.12.4</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
                </project>
                """
            ),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.Test;

                  public class MyTest {
                      @Test
                      public void test() {
                      }
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void testMockingStaticMethodsOfSystemIsDisabled() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(System.class)
              public class MyTest {
                  @Test
                  public void readsTheEnvironment() {
                      PowerMockito.mockStatic(System.class);
                      PowerMockito.when(System.getenv("HOME")).thenReturn("/tmp");
                  }
              }
              """,
            """
              import org.junit.Ignore;
              import org.junit.Test;

              public class MyTest {
                  @Test
                  @Ignore("PowerMock test disabled by migration: rework it not to rely on private members")
                  public void readsTheEnvironment() {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // PowerMockito.mockStatic(System.class);
                      // PowerMockito.when(System.getenv("HOME")).thenReturn("/tmp");
                  }
              }
              """
          )
        );
    }

    @Test
    void testMockingInheritedStaticMethodIsDisabled() {
        //language=java
        rewriteRun(
          java(
            """
              import java.net.Inet4Address;

              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.powermock.api.mockito.PowerMockito;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              import org.powermock.modules.junit4.PowerMockRunner;

              @RunWith(PowerMockRunner.class)
              @PrepareForTest(Inet4Address.class)
              public class MyTest {
                  @Test
                  public void resolvesTheLocalHost() throws Exception {
                      PowerMockito.mockStatic(Inet4Address.class);
                      PowerMockito.when(Inet4Address.getLocalHost()).thenReturn(null);
                  }
              }
              """,
            """
              import org.junit.Ignore;
              import org.junit.Test;

              public class MyTest {
                  @Test
                  @Ignore("PowerMock test disabled by migration: rework it not to rely on private members")
                  public void resolvesTheLocalHost() throws Exception {
                      // The body of this test is kept for reference while it is migrated by hand:
                      // PowerMockito.mockStatic(Inet4Address.class);
                      // PowerMockito.when(Inet4Address.getLocalHost()).thenReturn(null);
                  }
              }
              """
          )
        );
    }
}
