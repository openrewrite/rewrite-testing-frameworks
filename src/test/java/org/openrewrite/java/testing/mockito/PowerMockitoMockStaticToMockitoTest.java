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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PowerMockitoMockStaticToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("apiguardian-api", "mockito-core", "powermock-api-mockito", "junit-jupiter-api", "junit", "powermock-core"))
          .recipe(new PowerMockitoMockStaticToMockito());
    }

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
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class MyTest {
                            
                  private MockedStatic<Calendar> mockedCalendar;
              
                  @AfterEach
                  void tearDown() {
                      mockedCalendar.close();
                  }
              
                  @Test
                  void testStaticMethod() {
                      mockedCalendar = mockStatic(Calendar.class);
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
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              class MyTest {
              
                  private MockedStatic<Currency> mockedCurrency;
              
                  private MockedStatic<Calendar> mockedCalendar;
              
                  @AfterEach
                  void tearDown() {
                      mockedCalendar.close();
                      mockedCurrency.close();
                  }
              
                  @Test
                  void testStaticMethod() {
                      mockedCalendar = mockStatic(Calendar.class);
                      mockedCurrency = mockStatic(Currency.class);
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
                  void tearDown() {
                      mockedCalendar.close();
                  }
              }
              """
          )
        );
    }
}
