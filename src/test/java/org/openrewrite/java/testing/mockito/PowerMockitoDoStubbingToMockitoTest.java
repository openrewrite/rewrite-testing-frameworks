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

class PowerMockitoDoStubbingToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-core-3.12",
              "junit-jupiter-api-5",
              "powermock-core-1",
              "powermock-api-mockito-1"
            ))
          .recipe(new PowerMockitoDoStubbingToMockito())
          .typeValidationOptions(TypeValidation.builder()
            .identifiers(false)
            .build());
    }

    @DocumentExample
    @Test
    void doNothingWithStringMethodName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy, "clear");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void doReturnWithStringMethodName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doReturn("gregorian").when(calendarSpy, "getCalendarType");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doReturn("gregorian").when(calendarSpy).getCalendarType();
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeForStaticMethodStubbing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  @Test
                  void test() throws Exception {
                      PowerMockito.doReturn(Calendar.getInstance()).when(Calendar.class, "getInstance");
                  }
              }
              """
          )
        );
    }

    @Test
    void addsCommentForPrivateMethodStubbing() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy, "updateTime");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      // PowerMock private method stubbing is not supported by Mockito. Refactor the private method to package-private or extract to a collaborator.
                      PowerMockito.doNothing().when(calendarSpy, "updateTime");
                  }
              }
              """
          )
        );
    }

    @Test
    void doStubbingWithStringMethodNameAndArgs() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy, "set", 2026, 0, 1);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.BeforeEach;
              import org.powermock.api.mockito.PowerMockito;
              import java.util.Calendar;

              class MyTest {

                  private Calendar calendarSpy;

                  @BeforeEach
                  void setUp() throws Exception {
                      calendarSpy = PowerMockito.spy(Calendar.getInstance());
                      PowerMockito.doNothing().when(calendarSpy).set(2026, 0, 1);
                  }
              }
              """
          )
        );
    }
}
