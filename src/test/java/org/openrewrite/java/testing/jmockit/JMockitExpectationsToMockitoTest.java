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
package org.openrewrite.java.testing.jmockit;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class JMockitExpectationsToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "jmockit-1.49",
              "mockito-core-3.12",
              "mockito-junit-jupiter-3.12"
            ))
          .recipeFromResource(
            "/META-INF/rewrite/jmockit.yml",
            "org.openrewrite.java.testing.jmockit.JMockitToMockito"
          );
    }

    @DocumentExample
    @Test
    void whenNoResultNoTimes() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.wait(anyLong, anyInt);
                      }};
                      myObject.wait(10L, 10);
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
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void whenNoResultNoTimesNoArgs() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.wait();
                      }};
                      myObject.wait(10L, 10);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.verify;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      myObject.wait(10L, 10);
                      verify(myObject).wait();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNullResult() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.getClass();
                          result = null;
                      }};
                      assertNull(myObject.getClass());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.getClass()).thenReturn(null);
                      assertNull(myObject.getClass());
                      verify(myObject).getClass();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenIntResult() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.hashCode();
                          result = 10;
                      }};
                      assertEquals(10, myObject.hashCode());
                      new Expectations() {{
                          myObject.hashCode();
                          this.result = 100;
                      }};
                      assertEquals(100, myObject.hashCode());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;             
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.hashCode()).thenReturn(10);
                      assertEquals(10, myObject.hashCode());
                      when(myObject.hashCode()).thenReturn(100);
                      assertEquals(100, myObject.hashCode());
                      verify(myObject).hashCode();
                      verify(myObject).hashCode();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenStringResult() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.toString();
                          result = "foo";
                      }};
                      assertEquals("foo", myObject.toString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.toString()).thenReturn("foo");
                      assertEquals("foo", myObject.toString());
                      verify(myObject).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenVariableResult() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  String expected = "expected";

                  void test() {
                      new Expectations() {{
                          myObject.toString();
                          result = expected;
                      }};
                      assertEquals(expected, myObject.toString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  String expected = "expected";

                  void test() {
                      when(myObject.toString()).thenReturn(expected);
                      assertEquals(expected, myObject.toString());
                      verify(myObject).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNewClassResult() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.toString();
                          result = new String("foo");
                      }};
                      assertNotNull(myObject.toString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.toString()).thenReturn(new String("foo"));
                      assertNotNull(myObject.toString());
                      verify(myObject).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenExceptionResult() {
        //language=java
        rewriteRun(
          java(
            """
              package test;
                            
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() throws RuntimeException {
                      new Expectations() {{
                          myObject.toString();
                          result = new RuntimeException();
                      }};
                      myObject.toString();
                  }
              }
              """,
            """
              package test;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() throws RuntimeException {
                      when(myObject.toString()).thenThrow(new RuntimeException());
                      myObject.toString();
                      verify(myObject).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenReturns() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() throws RuntimeException {
                      new Expectations() {{
                          myObject.toString();
                          returns("foo", "bar");
                      }};
                      assertEquals("foo", myObject.toString());
                      assertEquals("bar", myObject.toString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() throws RuntimeException {
                      when(myObject.toString()).thenReturn("foo", "bar");
                      assertEquals("foo", myObject.toString());
                      assertEquals("bar", myObject.toString());
                      verify(myObject).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenClassArgumentMatcher() {
        //language=java
        rewriteRun(
          // below is disabling type verification because framework complains about missing type due to verify(object)
          // due to MyObject being a separate class
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              import java.util.List;

              class MyObject {
                  public String getSomeField(List<String> input) {
                      return "X";
                  }
                  public String getSomeOtherField(Object input) {
                      return "Y";
                  }
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new Expectations() {{
                          myObject.getSomeField((List<String>) any);
                          result = null;
                          myObject.getSomeOtherField((Object) any);
                          result = null;
                      }};
                      assertNull(myObject.getSomeField(new ArrayList<>()));
                      assertNull(myObject.getSomeOtherField(new Object()));
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeField(anyList())).thenReturn(null);
                      when(myObject.getSomeOtherField(any(Object.class))).thenReturn(null);
                      assertNull(myObject.getSomeField(new ArrayList<>()));
                      assertNull(myObject.getSomeOtherField(new Object()));
                      verify(myObject).getSomeField(anyList());
                      verify(myObject).getSomeOtherField(any(Object.class));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNoArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.toString();
                          result = null;
                      }};
                      assertNull(myObject.toString());
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.toString()).thenReturn(null);
                      assertNull(myObject.toString());
                      verify(myObject).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMixedArgumentMatcher() {
        //language=java
        rewriteRun(
          // below is disabling type verification because framework complains about missing type due to verify(object)
          // due to MyObject being a separate class
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              import java.util.List;

              class MyObject {
                  public String getSomeField(String s, String s2, String s3, long l1) {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import java.util.List;

              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      String bazz = "bazz";
                      new Expectations() {{
                          myObject.getSomeField("foo", anyString, bazz, 10L);
                          result = null;
                      }};
                      assertNull(myObject.getSomeField("foo", "bar", bazz, 10L));
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      String bazz = "bazz";
                      when(myObject.getSomeField(eq("foo"), anyString(), eq(bazz), eq(10L))).thenReturn(null);
                      assertNull(myObject.getSomeField("foo", "bar", bazz, 10L));
                      verify(myObject).getSomeField(eq("foo"), anyString(), eq(bazz), eq(10L));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenSetupStatements() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      Class c = MyTest.class;
                      String s = "s";

                      new Expectations() {{
                          myObject.toString();
                          result = s;

                          myObject.getClass();
                          result = c;
                          
                          myObject.wait(anyLong);
                      }};

                      assertEquals("s", myObject.toString());
                      assertEquals(c, myObject.getClass());
                      myObject.wait(10L);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      Class c = MyTest.class;
                      String s = "s";
                      when(myObject.toString()).thenReturn(s);
                      when(myObject.getClass()).thenReturn(c);
                                            
                      assertEquals("s", myObject.toString());
                      assertEquals(c, myObject.getClass());
                      myObject.wait(10L);
                      verify(myObject).toString();
                      verify(myObject).getClass();
                      verify(myObject).wait(anyLong());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenSetupStatements2() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      String a = "a";
                      new Expectations() {{
                          myObject.toString();
                          String s = "s";
                          String b = "b";
                          result = s;
                      }};

                      assertEquals("s", myObject.toString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      String a = "a";
                      String s = "s";
                      String b = "b";
                      when(myObject.toString()).thenReturn(s);

                      assertEquals("s", myObject.toString());
                      verify(myObject).toString();
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
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.wait(anyLong, anyInt);
                          times = 3;
                      }};
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
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
                      myObject.wait(10L, 10);
                      verify(myObject, times(3)).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMinTimes() {
        //language=java
        rewriteRun(
          java(
            """              
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
                  
                  void test() {
                      new Expectations() {{
                          myObject.wait(anyLong, anyInt);
                          minTimes = 2;
                      }};
                      myObject.wait(10L, 10);
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
                      verify(myObject, atLeast(2)).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMaxTimes() {
        //language=java
        rewriteRun(
          java(
            """              
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
                  
                  void test() {
                      new Expectations() {{
                          myObject.wait(anyLong, anyInt);
                          maxTimes = 5;
                      }};
                      myObject.wait(10L, 10);
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
                      verify(myObject, atMost(5)).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMinTimesMaxTimes() {
        //language=java
        rewriteRun(
          java(
            """              
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
                  
                  void test() {
                      new Expectations() {{
                          myObject.wait(anyLong, anyInt);
                          minTimes = 1;
                          maxTimes = 3;
                      }};
                      myObject.wait(10L, 10);
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
                      verify(myObject, atLeast(1)).wait(anyLong(), anyInt());
                      verify(myObject, atMost(3)).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenSpy() {
        //language=java
        rewriteRun(
          // below is disabling type verification because framework complains about missing type due to verify(object)
          // due to MyObject being a separate class
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              class MyObject {
                  public String getSomeField() {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import mockit.Expectations;
              import mockit.Tested;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Tested
                  MyObject myObject;

                  void test() {
                      new Expectations(myObject) {{
                          myObject.getSomeField();
                          result = "foo";
                      }};
                      assertEquals("foo", myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.InjectMocks;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @InjectMocks
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeField()).thenReturn("foo");
                      assertEquals("foo", myObject.getSomeField());
                      verify(myObject).getSomeField();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMultipleStatements() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  @Mocked
                  Object myOtherObject;

                  void test() {
                      new Expectations() {{
                          myObject.hashCode();
                          result = 10;
                          myOtherObject.getClass();
                          result = null;
                          myObject.wait(anyLong, anyInt);
                          myOtherObject.toString();
                          result = "foo";
                      }};
                      assertEquals(10, myObject.hashCode());
                      assertNull(myOtherObject.getClass());
                      myObject.wait(10L, 10);
                      assertEquals("foo", myOtherObject.toString());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.*;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  @Mock
                  Object myOtherObject;

                  void test() {
                      when(myObject.hashCode()).thenReturn(10);
                      when(myOtherObject.getClass()).thenReturn(null);
                      when(myOtherObject.toString()).thenReturn("foo");
                      assertEquals(10, myObject.hashCode());
                      assertNull(myOtherObject.getClass());
                      myObject.wait(10L, 10);
                      assertEquals("foo", myOtherObject.toString());
                      verify(myObject).hashCode();
                      verify(myOtherObject).getClass();
                      verify(myObject).wait(anyLong(), anyInt());
                      verify(myOtherObject).toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMultipleExpectations() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.toString();
                          result = "a";
                      }};
                      assertEquals("a", myObject.toString());
                      new Expectations() {{
                          myObject.getClass();
                          result = MyTest.class;
                      }};
                      assertEquals(MyTest.class, myObject.getClass());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.verify;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.toString()).thenReturn("a");
                      assertEquals("a", myObject.toString());
                      when(myObject.getClass()).thenReturn(MyTest.class);
                      assertEquals(MyTest.class, myObject.getClass());
                      verify(myObject).toString();
                      verify(myObject).getClass();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMultipleExpectationsNoResults() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Expectations;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNull;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      new Expectations() {{
                          myObject.wait(anyLong);
                      }};
                      myObject.wait(1L);
                      new Expectations() {{
                          myObject.wait();
                      }};
                      myObject.wait();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.anyLong;
              import static org.mockito.Mockito.verify;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      myObject.wait(1L);
                      myObject.wait();
                      verify(myObject).wait(anyLong());
                      verify(myObject).wait();
                  }
              }
              """
          )
        );
    }
}
