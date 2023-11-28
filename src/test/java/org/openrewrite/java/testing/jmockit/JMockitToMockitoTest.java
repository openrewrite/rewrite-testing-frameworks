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

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class JMockitToMockitoTest implements RewriteTest {

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

    @Test
    void jMockitExpectationsToMockitoVoidResult() {
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
                      doNothing().when(myObject).wait(anyLong(), anyInt());
                      myObject.wait(10L, 10);
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenNullResult() {
        //language=java
        rewriteRun(
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
                          myObject.getSomeField();
                          result = null;
                      }};
                      assertNull(myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeField()).thenReturn(null);
                      assertNull(myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenIntResult() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public int getSomeField() {
                      return 0;
                  }
              }
              """
          ),
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
                  MyObject myObject;
                          
                  void test() {
                      new Expectations() {{
                          myObject.getSomeField();
                          result = 10;
                      }};
                      assertEquals(10, myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeField()).thenReturn(10);
                      assertEquals(10, myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenStringResult() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public String getSomeField(String s) {
                      return "X";
                  }
              }
              """
          ),
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
                  MyObject myObject;
                          
                  void test() {
                      new Expectations() {{
                          myObject.getSomeField(anyString);
                          result = "foo";
                      }};
                      assertEquals("foo", myObject.getSomeField("bar"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.anyString;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeField(anyString())).thenReturn("foo");
                      assertEquals("foo", myObject.getSomeField("bar"));
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenVariableResult() {
        //language=java
        rewriteRun(
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
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
                          
              import static org.junit.jupiter.api.Assertions.assertEquals;
                          
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;
                
                  String expected = "expected";
                
                  void test() {
                      new Expectations() {{
                          myObject.getSomeField();
                          result = expected;
                      }};
                      assertEquals(expected, myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.when;
                            
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;
                
                  String expected = "expected";
                
                  void test() {
                      when(myObject.getSomeField()).thenReturn(expected);
                      assertEquals(expected, myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenNewClassResult() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public Object getSomeField() {
                      return null;
                  }
              }
              """
          ),
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
                  MyObject myObject;
                          
                  void test() {
                      new Expectations() {{
                          myObject.getSomeField();
                          result = new Object();
                      }};
                      assertNotNull(myObject.getSomeField());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeField()).thenReturn(new Object());
                      assertNotNull(myObject.getSomeField());
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenExceptionResult() {
        //language=java
        rewriteRun(
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
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() throws RuntimeException {
                      new Expectations() {{
                          myObject.getSomeField();
                          result = new RuntimeException();
                      }};
                      myObject.getSomeField();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() throws RuntimeException {
                      when(myObject.getSomeField()).thenThrow(new RuntimeException());
                      myObject.getSomeField();
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenClassArgumentMatcher() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
              
              class MyObject {
                  public String getSomeField(List<String> input) {
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
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;
                  
                  void test() {
                      new Expectations() {{
                          myObject.getSomeField((List<String>) any);
                          result = null;
                      }};
                      assertNotNull(myObject.getSomeField(new ArrayList<>()));
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
              
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
              
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              import static org.mockito.Mockito.anyList;
              import static org.mockito.Mockito.when;
              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;
                  
                  void test() {
                      when(myObject.getSomeField(anyList())).thenReturn(null);
                      assertNotNull(myObject.getSomeField(new ArrayList<>()));
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenSetupStatements() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public String getSomeField(String s) {
                      return "X";
                  }
              }
              """
          ),
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
                  MyObject myObject;
                          
                  void test() {
                      String a = "a";
                      
                      new Expectations() {{
                          myObject.getSomeField(anyString);
                          String s = "s";
                          String b = "b";
                          result = s;
                      }};
                      
                      assertEquals("s", myObject.getSomeField("foo"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.anyString;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      String a = "a";
                      
                      String s = "s";
                      String b = "b";
                      when(myObject.getSomeField(anyString())).thenReturn(s);
                      
                      assertEquals("s", myObject.getSomeField("foo"));
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenTimes() {
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
                          times = 2;
                      }};
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
                      doNothing().when(myObject).wait(anyLong(), anyInt());
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      verify(myObject, times(2)).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @Test
    void jMockitExpectationsToMockitoWhenMultipleStatements() {
        //language=java
        rewriteRun(
          java(
            """
              class MyObject {
                  public String getSomeStringField(String input, long otherInput) {
                      return "X";
                  }
                  public int getSomeIntField() {
                      return 0;
                  }
                  public Object getSomeObjectField() {
                      return new Object();
                  }
                  public void doSomething() {}
              }
              """
          ),
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
                  MyObject myOtherObject;
                  
                  void test() {
                      new Expectations() {{
                          myObject.hashCode();
                          result = 10;
                          myOtherObject.getSomeObjectField();
                          result = null;
                          myObject.wait(anyLong, anyInt);
                          myOtherObject.getSomeStringField(anyString, anyLong);
                          result = "foo";
                      }};
                      assertEquals(10, myObject.hashCode());
                      assertNull(myOtherObject.getSomeObjectField());
                      myObject.wait(10L, 10);
                      assertEquals("foo", myOtherObject.getSomeStringField("bar", 10L));
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
                  MyObject myOtherObject;

                  void test() {
                      when(myObject.hashCode()).thenReturn(10);
                      when(myOtherObject.getSomeObjectField()).thenReturn(null);
                      doNothing().when(myObject).wait(anyLong(), anyInt());
                      when(myOtherObject.getSomeStringField(anyString(), anyLong())).thenReturn("foo");
                      assertEquals(10, myObject.hashCode());
                      assertNull(myOtherObject.getSomeObjectField());
                      myObject.wait(10L, 10);
                      assertEquals("foo", myOtherObject.getSomeStringField("bar", 10L));
                  }
              }
              """
          )
        );
    }
}
