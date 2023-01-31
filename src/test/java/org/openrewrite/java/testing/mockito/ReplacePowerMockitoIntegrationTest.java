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
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ReplacePowerMockitoIntegrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "mockito-all-1.10.19", "junit-4.13.2", "hamcrest-2.2", "junit-jupiter-api-5.9.2"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.mockito")
            .build()
            .activateRecipes("org.openrewrite.java.testing.mockito.ReplacePowerMockito"));
    }

    @Test
    void testThatMockStaticGetsReplaced() {
        //language=java
        rewriteRun(java(
          """
            package mockito.example;

            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            import static org.powermock.api.mockito.PowerMockito.mockStatic;
            import static org.testng.Assert.assertEquals;
            import java.util.Calendar;
            
            import org.powermock.core.classloader.annotations.PrepareForTest;
            import org.powermockito.configuration.PowerMockTestCaseConfig;
            import org.testng.annotations.Test;
            
            @PrepareForTest(Calendar.class)
            public class StaticMethodTest extends PowerMockTestCaseConfig {
            
                private Calendar calendarMock = mock(Calendar.class);
            
                @Test
                void testWithCalendar() {
                    mockStatic(Calendar.class);
                    when(Calendar.getInstance()).thenReturn(calendarMock);
                    assertEquals(Calendar.getInstance(), calendarMock);
                }
            
            }
          """,
          """
            package mockito.example;

            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.mockStatic;
            import static org.testng.Assert.assertEquals;
            import static org.testng.Assert.assertNotEquals;
            import java.util.Calendar;
            
            import org.mockito.MockedStatic;
            import org.testng.annotations.AfterMethod;
            import org.testng.annotations.Test;
            
            public class StaticMethodTest {
            
                private final MockedStatic<Calendar> mockedStatic = mockStatic(Calendar.class);
                private Calendar calendarMock = mock(Calendar.class);
            
                @AfterMethod
                void tearDown() {
                    mockedStatic.close();
                }
                
                @Test
                void testWithCalendar() {
                    mockedStatic.when(Calendar::getInstance).thenReturn(calendarMock);
                    assertEquals(Calendar.getInstance(), calendarMock);
                }
            
            }
            
          """
        ));
    }
}
