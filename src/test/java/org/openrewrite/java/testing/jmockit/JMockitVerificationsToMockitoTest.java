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
    void whenTimesNoArgs() {
        //language=java
        rewriteRun(
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
                  Object myObject;
                            
                  void test() {
                      myObject.wait();
                      myObject.wait();
                      new Verifications() {{
                          myObject.wait();
                          times = 2;
                      }};
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
                            
              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                                                        
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;
                            
                  void test() {
                      myObject.wait();
                      myObject.wait();
                      verify(myObject, times(2)).wait();
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
                            
              import mockit.Mocked;
              import mockit.Verifications;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
                                                        
              @ExtendWith(JMockitExtension.class)
              class MyTest {                                     
                  @Mocked
                  MyObject myObject;
                            
                  void test() {
                      myObject.getSomeField(new ArrayList<>());
                      myObject.getSomeOtherField(new Object());
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
                                                                                    
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;
                            
                  void test() {
                      myObject.getSomeField(new ArrayList<>());
                      myObject.getSomeOtherField(new Object());
                      verify(myObject).getSomeField(anyList());
                      verify(myObject).getSomeOtherField(any(Object.class));
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
              import java.util.ArrayList;
              import java.util.List;
                            
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
                          myObject.wait(anyLong, 10);
                      }};
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;
                            
              import static org.mockito.Mockito.*;
                            
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;
                            
                  void test() {
                      myObject.wait(10L, 10);
                      verify(myObject).wait(anyLong(), eq(10));
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
              import mockit.Verifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
                            
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
                            
                  void test() {
                      String a = "a";
                      String s = "s";
                      myObject.wait(1L);
                      myObject.wait();
                      new Verifications() {{
                          myObject.wait(anyLong);
                          myObject.wait();
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.mockito.Mockito.anyLong;
              import static org.mockito.Mockito.verify;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      String a = "a";
                      String s = "s";
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

    @Test
    void whenSetupStatements2() {
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
                      String a = "a";
                      myObject.wait(1L);
                      new Verifications() {{
                          myObject.wait(anyLong);
                      }};
                  }
              }
              """,
            """
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;
                            
              import static org.mockito.Mockito.anyLong;
              import static org.mockito.Mockito.verify;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      String a = "a";
                      myObject.wait(1L);
                      verify(myObject).wait(anyLong());
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
                      myObject.wait(10L, 10);
                      myObject.wait(10L, 10);
                      new Verifications() {{
                          myObject.wait(anyLong, anyInt);
                          times = 3;
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
                          minTimes = 2;
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
                          maxTimes = 5;
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
                          minTimes = 1;
                          maxTimes = 3;
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
                      verify(myObject, atLeast(1)).wait(anyLong(), anyInt());
                      verify(myObject, atMost(3)).wait(anyLong(), anyInt());
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
              import mockit.Verifications;
              import mockit.Mocked;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;
                                                        
              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  Object myObject;
                            
                  @Mocked
                  Object myOtherObject;
                            
                  void test() {
                      myObject.hashCode();
                      myOtherObject.wait();
                      myObject.wait(10L, 10);
                      myOtherObject.wait(10L);
                      new Verifications() {{
                          myObject.hashCode();
                          myOtherObject.wait();
                          myObject.wait(anyLong, anyInt);
                          myOtherObject.wait(anyLong);
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
                  Object myOtherObject;

                  void test() {
                      myObject.hashCode();
                      myOtherObject.wait();
                      myObject.wait(10L, 10);
                      myOtherObject.wait(10L);
                      verify(myObject).hashCode();
                      verify(myOtherObject).wait();
                      verify(myObject).wait(anyLong(), anyInt());
                      verify(myOtherObject).wait(anyLong());
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMultipleVerificationsAndMultipleStatements() {
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
                      myObject.wait();
                      new Verifications() {{
                      
                          myObject.wait();
                          
                          myObject.wait(anyLong, anyInt);
                      }};
                      myObject.wait(1L);
                      myObject.wait(2L);
                      new Verifications() {{
                          myObject.wait(anyLong);
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
                      myObject.wait();
                      verify(myObject).wait();
                      verify(myObject).wait(anyLong(), anyInt());
                      myObject.wait(1L);
                      myObject.wait(2L);
                      verify(myObject, times(2)).wait(anyLong());
                  }
              }
              """
          )
        );
    }
}
