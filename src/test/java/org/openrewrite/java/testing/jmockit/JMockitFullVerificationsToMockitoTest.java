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
package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setDefaultParserSettings;

/**
 * Not doing comprehensive testing as it is covered in JMockitVerificationsToMockitoTest and shares same code path
 */
@SuppressWarnings("SpellCheckingInspection")
class JMockitFullVerificationsToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setDefaultParserSettings(spec);
    }

    @DocumentExample
    @Test
    void whenMultipleMocks() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.FullVerifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
              
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
              
                  @Mocked
                  String str;
              
                  void test() {
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      str.notify();
                      new FullVerifications() {{
                          myObject.wait(anyLong, anyInt);
                          times = 2;
                          str.notify();
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import static org.mockito.Mockito.*;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;
              
                  @Mock
                  String str;
              
                  void test() {
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      str.notify();
                      verify(myObject, times(2)).wait(anyLong(), anyInt());
                      verify(str).notify();
                      verifyNoMoreInteractions(myObject, str);
                  }
              }
              """
          )
        );
    }

    @Test
    void whenOtherStatements() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.FullVerifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
              
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
              
                  void test() {
                      myObject.wait(10L, 10);
                      new FullVerifications() {{
                          myObject.wait(anyLong, anyInt);
                      }};
                      System.out.println("bla");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import static org.mockito.Mockito.*;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;
              
                  void test() {
                      myObject.wait(10L, 10);
                      verify(myObject).wait(anyLong(), anyInt());
                      verifyNoMoreInteractions(myObject);
                      System.out.println("bla");
                  }
              }
              """
          )
        );
    }

    @Test
    void whenTimes() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.FullVerifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
              
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
              
                  void test() {
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      new FullVerifications() {{
                          myObject.wait(anyLong, anyInt);
                          times = 2;
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import static org.mockito.Mockito.*;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;
              
                  void test() {
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      verify(myObject, times(2)).wait(anyLong(), anyInt());
                      verifyNoMoreInteractions(myObject);
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMultipleInvocationsSameMock() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.FullVerifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
              
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
              
                  void test() {
                      myObject.wait(10L, 10);
                      myObject.wait();
                      new FullVerifications() {{
                          myObject.wait(anyLong, anyInt);
                          myObject.wait();
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import static org.mockito.Mockito.*;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;
              
                  void test() {
                      myObject.wait(10L, 10);
                      myObject.wait();
                      verify(myObject).wait(anyLong(), anyInt());
                      verify(myObject).wait();
                      verifyNoMoreInteractions(myObject);
                  }
              }
              """
          )
        );
    }
}
