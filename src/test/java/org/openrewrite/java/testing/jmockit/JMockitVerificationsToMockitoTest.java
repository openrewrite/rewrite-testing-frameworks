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

import static org.openrewrite.java.Assertions.java;

class JMockitVerificationsToMockitoTest implements RewriteTest {
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
    void whenNoTimes() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Verifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      myObject.wait(10L, 10);             
                      new Verifications() {{
                          myObject.wait(anyLong, anyInt);
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
                      verify(myObject).wait(anyLong(), anyInt());
                  }
              }
              """
          )
        );
    }

    @DocumentExample
    @Test
    void whenNoTimesNoArgs() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Verifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;

                  void test() {
                      myObject.wait(10L, 10);
                      new Verifications() {{
                          myObject.wait();
                      }};                      
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
    void whenClassArgumentMatcher() {
        //language=java
        rewriteRun(
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
                            
              import mockit.Verifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
                            
              import static org.junit.jupiter.api.Assertions.assertNull;
                            
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;
                            
                  void test() {
                      assertNull(myObject.getSomeField(new ArrayList<>()));
                      assertNull(myObject.getSomeOtherField(new Object()));
                      new Verifications() {{
                          myObject.getSomeField((List<String>) any);
                          myObject.getSomeOtherField((Object) any);
                      }};                      
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
              import static org.mockito.Mockito.any;
              import static org.mockito.Mockito.anyList;
                            
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;
                            
                  void test() {
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
    void whenTimesNoArgs() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.List;
                            
              class MyObject {
                  public String getSomeField() {
                      return "X";
                  }
              }
              """
          ),
          java(
            """
              import java.util.ArrayList;
              import java.util.List;
                            
              import mockit.Verifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;
                            
                  void test() {
                      myObject.getSomeField();
                      new Verifications() {{
                          myObject.getSomeField();
                      }};
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.mockito.Mockito.verify;
                            
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;
                            
                  void test() {
                      myObject.getSomeField();
                      verify(myObject).getSomeField();
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
                            
              import mockit.Verifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
                            
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;
                            
                  void test() {
                      String bazz = "bazz";
                      myObject.getSomeField("foo", "bar", bazz, 10L);
                      new Verifications() {{
                          myObject.getSomeField("foo", anyString, bazz, 10L);
                      }};                      
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.mockito.Mockito.*;
                            
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;
                            
                  void test() {
                      String bazz = "bazz";
                      myObject.getSomeField("foo", "bar", bazz, 10L);
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
              class MyObject {
                            
                  public String getSomeField(String s) {
                      return "X";
                  }
                  public String getString() {
                      return "Y";
                  }
              }
              """
          ),
          java(
            """
              import mockit.Verifications;
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
                      String s = "s";
                      assertEquals("s", myObject.getSomeField("foo"));
                      assertEquals("a", myObject.getString());
                      new Verifications() {{
                          myObject.getSomeField(anyString);
                          result = s;
                            
                          myObject.getString();
                          result = a;
                      }};
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
                  MyObject myObject;

                  void test() {
                      String a = "a";
                      String s = "s";                           
                      assertEquals("s", myObject.getSomeField("foo"));
                      assertEquals("a", myObject.getString());
                      verify(myObject).getSomeField(anyString());            
                      verify(myObject).getString();
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
              import static org.mockito.Mockito.when;
                            
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @InjectMocks
                  MyObject myObject;
                            
                  void test() {
                      when(myObject.getSomeField()).thenReturn("foo");
                      assertEquals("foo", myObject.getSomeField());
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
                      when(myOtherObject.getSomeStringField(anyString(), anyLong())).thenReturn("foo");
                      assertEquals(10, myObject.hashCode());
                      assertNull(myOtherObject.getSomeObjectField());
                      myObject.wait(10L, 10);
                      assertEquals("foo", myOtherObject.getSomeStringField("bar", 10L));
                      verify(myObject).wait(anyLong(), anyInt());
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
              class MyObject {
                  public String getSomeStringField() {
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
              import static org.junit.jupiter.api.Assertions.assertNull;
                            
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;
                            
                  void test() {
                      new Expectations() {{
                          myObject.getSomeStringField();
                          result = "a";
                      }};
                      assertEquals("a", myObject.getSomeStringField());
                      new Expectations() {{
                          myObject.getSomeStringField();
                          result = "b";
                      }};
                      assertEquals("b", myObject.getSomeStringField());
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.junit.jupiter.api.Assertions.assertNull;
              import static org.mockito.Mockito.when;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeStringField()).thenReturn("a");
                      assertEquals("a", myObject.getSomeStringField());
                      when(myObject.getSomeStringField()).thenReturn("b");
                      assertEquals("b", myObject.getSomeStringField());
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
