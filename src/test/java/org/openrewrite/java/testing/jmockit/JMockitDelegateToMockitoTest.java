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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setDefaultParserSettings;

/**
 * At the moment, JMockit Delegates are not migrated to mockito. What I'm seeing is that they are being trashed
 * with the template being printed out. These tests were written to try to replicate this issue, however I was unable to.
 * They may help anyone adding feature for Delegate migration.
 */
class JMockitDelegateToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setDefaultParserSettings(spec);
    }

    @Test
    void whenNoArgsVoidMethod() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              import mockit.Expectations;
              import mockit.Delegate;
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
                          result = new Delegate() {
                              public void wait() {
                                 System.out.println("bla");
                             }
                          };
                      }};
                      myObject.wait();
                  }
              }
              """,
            """
              import mockit.Delegate;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.mockito.Mockito.when;
                                              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.wait()).thenReturn(new Delegate() {
                          public void wait() {
                              System.out.println("bla");
                          }             
                      });
                      myObject.wait();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenHasArgsVoidMethod() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              import mockit.Expectations;
              import mockit.Delegate;
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
                          myObject.wait(anyLong);
                          result = new Delegate() {
                              void wait(long timeoutMs) {
                                  System.out.println("bla");
                                  System.out.println("bla");
                             }
                          };
                      }};
                      myObject.wait();
                  }
              }
              """,
            """
              import mockit.Delegate;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.anyLong;
              import static org.mockito.Mockito.when;
                                              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.wait(anyLong())).thenReturn(new Delegate() {
                          void wait(long timeoutMs) {
                              System.out.println("bla");
                              System.out.println("bla");
                          }           
                      });
                      myObject.wait();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNoArgsNonVoidMethod() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              import mockit.Expectations;
              import mockit.Delegate;
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
                          result = new Delegate() {
                              String toString() {
                                  String a = "bla";
                                  return a + "foo";
                             }
                          };
                      }};
                      myObject.toString();
                  }
              }
              """,
            """
              import mockit.Delegate;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.when;
                                              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.toString()).thenReturn(new Delegate() {
                          String toString() {
                              String a = "bla";
                              return a + "foo";
                          }             
                      });
                      myObject.toString();
                  }
              }
              """
          )
        );
    }

    @Test
    void whenMultipleStatementsWithAnnotation() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              import mockit.Expectations;
              import mockit.Delegate;
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
                          result = 100;
                          myObject.toString();
                          result = new Delegate() {
                              @SuppressWarnings("unused")
                              String toString() {
                                  String a = "bla";
                                  return a + "foo";
                             }
                          };
                      }};
                      myObject.toString();
                  }
              }
              """,
            """
              import mockit.Delegate;
              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              import static org.junit.jupiter.api.Assertions.assertEquals;
              import static org.mockito.Mockito.when;
                                              
              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  Object myObject;

                  void test() {
                      when(myObject.hashCode()).thenReturn(100);
                      when(myObject.toString()).thenReturn(new Delegate() {
                          @SuppressWarnings("unused")
                          String toString() {
                              String a = "bla";
                              return a + "foo";
                          }            
                      });
                      myObject.toString();
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

              import mockit.Delegate;
              import mockit.Mocked;
              import mockit.Expectations;
              import mockit.integration.junit5.JMockitExtension;
              import org.junit.jupiter.api.extension.ExtendWith;

              @ExtendWith(JMockitExtension.class)
              class MyTest {
                  @Mocked
                  MyObject myObject;

                  void test() {
                      new Expectations() {{
                          myObject.getSomeField((List<String>) any);
                          result = new Delegate() {
                              String getSomeOtherField(List<String> input) {
                                  input.add("foo");
                                  return input.toString();
                             }
                          };
                      }};
                      myObject.getSomeField(new ArrayList<>());
                      myObject.getSomeOtherField(new Object());
                  }
              }
              """,
            """
              import java.util.ArrayList;
              import java.util.List;

              import mockit.Delegate;
                             
              import static org.mockito.Mockito.anyList;
              import static org.mockito.Mockito.when;

              import org.junit.jupiter.api.extension.ExtendWith;
              import org.mockito.Mock;
              import org.mockito.junit.jupiter.MockitoExtension;

              @ExtendWith(MockitoExtension.class)
              class MyTest {
                  @Mock
                  MyObject myObject;

                  void test() {
                      when(myObject.getSomeField(anyList())).thenReturn(new Delegate() {
                          String getSomeOtherField(List<String> input) {
                              input.add("foo");
                              return input.toString();
                          }
                      });
                      myObject.getSomeField(new ArrayList<>());
                      myObject.getSomeOtherField(new Object());
                  }
              }
              """
          )
        );
    }

}
