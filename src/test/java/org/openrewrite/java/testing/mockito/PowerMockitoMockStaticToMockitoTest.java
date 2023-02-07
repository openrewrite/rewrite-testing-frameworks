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

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class PowerMockitoMockStaticToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12.4", "junit-jupiter-api-5.9.2", "junit-4.13.2", "powermock-core-1.7.4", "powermock-api-mockito-1.7.4"))
          .recipe(new PowerMockitoMockStaticToMockito());
    }

    @Test
    void testThatPrepareForTestAnnotationIsReplacedBySingleField() {
        //language=java
        rewriteRun(
          java(
            """
              package mockito.example;
              
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
              package mockito.example;
              
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
    void testThatPrepareForTestAnnotationIsReplacedByFields() {
        //language=java
        rewriteRun(
          java(
            """
              package mockito.example;
              
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              import java.util.Currency;
              
              import org.junit.jupiter.api.Test;
              import org.powermock.core.classloader.annotations.PrepareForTest;
              
              @PrepareForTest({Calendar.class, Currency.class})
              public class MyTest {
              
                  @Test
                  void testStaticMethod() {
                      mockStatic(Calendar.class);
                      mockStatic(Currency.class);
                  }
              }
              """,
            """
              package mockito.example;
              
              import static org.mockito.Mockito.mockStatic;
              
              import java.util.Calendar;
              import java.util.Currency;
              
              import org.junit.jupiter.api.AfterEach;
              import org.junit.jupiter.api.Test;
              import org.mockito.MockedStatic;
              
              public class MyTest {
              
                  private MockedStatic<Currency> mockedCurrency = mockStatic(Currency.class);
              
                  private MockedStatic<Calendar> mockedCalendar = mockStatic(Calendar.class);
                  
                  @AfterEach
                  void tearDown() {
                      mockedCalendar.close();
                  }
              
                  @Test
                  void testStaticMethod() {
                  }
              }
              """
          )
        );
    }
}
