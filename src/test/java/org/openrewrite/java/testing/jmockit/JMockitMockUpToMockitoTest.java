/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.*;

class JMockitMockUpToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setParserSettings(spec, JMOCKIT_DEPENDENCY, JUNIT_4_DEPENDENCY);
    }

    @DocumentExample
    @Test
    void mockUpStaticMethodTest() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Mock;
              import mockit.MockUp;
              import static org.junit.Assert.assertEquals;
              import org.junit.Test;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      new MockUp<MyClazz>() {
              
                          @Mock
                          public int staticMethod() {
                              return 1024;
                          }
              
                          @Mock
                          public int staticMethod(int v) {
                              return 128;
                          }
                      };
                      assertEquals(1024, MyClazz.staticMethod());
                      assertEquals(128, MyClazz.staticMethod(0));
                  }
              
                  public static class MyClazz {
                      public static int staticMethod() {
                          return 0;
                      }
              
                      public static int staticMethod(int v) {
                          return 1;
                      }
                  }
              }
              """, """
              import static org.junit.Assert.assertEquals;
              import static org.mockito.ArgumentMatchers.*;
              import static org.mockito.Mockito.*;
              
              import org.junit.Test;
              import org.mockito.MockedStatic;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      try (MockedStatic mockStaticMyClazz = mockStatic(MyClazz.class)) {
                          mockStaticMyClazz.when(() -> MyClazz.staticMethod()).thenAnswer(invocation -> 1024);
                          mockStaticMyClazz.when(() -> MyClazz.staticMethod(anyInt())).thenAnswer(invocation -> 128);
                          assertEquals(1024, MyClazz.staticMethod());
                          assertEquals(128, MyClazz.staticMethod(0));
                      }
                  }
              
                  public static class MyClazz {
                      public static int staticMethod() {
                          return 0;
                      }
              
                      public static int staticMethod(int v) {
                          return 1;
                      }
                  }
              }
              """));
    }

    @Test
    void mockUpMultipleTest() {
        //language=java
        rewriteRun(
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().identifiers(false).build()),
          java(
            """
              package com.openrewrite;
              public static class Foo {
                  public String getMsg() {
                      return "foo";
                  }
              
                  public String getMsg(String echo) {
                      return "foo" + echo;
                  }
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
              package com.openrewrite;
              public static class Bar {
                  public String getMsg() {
                      return "bar";
                  }
              
                  public String getMsg(String echo) {
                      return "bar" + echo;
                  }
              }
              """,
            SourceSpec::skip
          ),
          java(
            """
              import com.openrewrite.Foo;
              import com.openrewrite.Bar;
              import org.junit.Test;
              import mockit.Mock;
              import mockit.MockUp;
              import static org.junit.Assert.assertEquals;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      new MockUp<Foo>() {
                          @Mock
                          public String getMsg() {
                              return "FOO";
                          }
                          @Mock
                          public String getMsg(String echo) {
                              return "FOO" + echo;
                          }
                      };
                      new MockUp<Bar>() {
                          @Mock
                          public String getMsg() {
                              return "BAR";
                          }
                          @Mock
                          public String getMsg(String echo) {
                              return "BAR" + echo;
                          }
                      };
                      assertEquals("FOO", new Foo().getMsg());
                      assertEquals("FOOecho", new Foo().getMsg("echo"));
                      assertEquals("BAR", new Bar().getMsg());
                      assertEquals("BARecho", new Bar().getMsg("echo"));
                  }
              }
              """, """
              import com.openrewrite.Foo;
              import com.openrewrite.Bar;
              import org.junit.Test;
              import org.mockito.MockedConstruction;
              import static org.junit.Assert.assertEquals;
              import static org.mockito.AdditionalAnswers.delegatesTo;
              import static org.mockito.Answers.CALLS_REAL_METHODS;
              import static org.mockito.ArgumentMatchers.*;
              import static org.mockito.Mockito.*;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      Foo mockFoo = mock(Foo.class, CALLS_REAL_METHODS);
                      doAnswer(invocation -> "FOO").when(mockFoo).getMsg();
                      doAnswer(invocation -> {
                          String echo = invocation.getArgument(0);
                          return "FOO" + echo;
                      }).when(mockFoo).getMsg(nullable(String.class));
                      Bar mockBar = mock(Bar.class, CALLS_REAL_METHODS);
                      doAnswer(invocation -> "BAR").when(mockBar).getMsg();
                      doAnswer(invocation -> {
                          String echo = invocation.getArgument(0);
                          return "BAR" + echo;
                      }).when(mockBar).getMsg(nullable(String.class));
                      try (MockedConstruction mockConsFoo = mockConstructionWithAnswer(Foo.class, delegatesTo(mockFoo));MockedConstruction mockConsBar = mockConstructionWithAnswer(Bar.class, delegatesTo(mockBar))) {
                          assertEquals("FOO", new Foo().getMsg());
                          assertEquals("FOOecho", new Foo().getMsg("echo"));
                          assertEquals("BAR", new Bar().getMsg());
                          assertEquals("BARecho", new Bar().getMsg("echo"));
                      }
                  }
              }
              """)
        );
    }

    @Test
    void mockUpInnerStatementTest() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Mock;
              import mockit.MockUp;
              
              import org.junit.Test;
              import static org.junit.Assert.assertEquals;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      new MockUp<MyClazz>() {
                          final String msg = "newMsg";
              
                          @Mock
                          public String getMsg() {
                              return msg;
                          }
                      };
              
                      // Should ignore the newClass statement
                      new Runnable() {
                          @Override
                          public void run() {
                              System.out.println("run");
                          }
                      };
                      assertEquals("newMsg", new MyClazz().getMsg());
                  }
              
                  public static class MyClazz {
                      public String getMsg() {
                          return "msg";
                      }
                  }
              }
              """, """
              import org.junit.Test;
              import org.mockito.MockedConstruction;
              
              import static org.junit.Assert.assertEquals;
              import static org.mockito.AdditionalAnswers.delegatesTo;
              import static org.mockito.Answers.CALLS_REAL_METHODS;
              import static org.mockito.Mockito.*;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      final String msg = "newMsg";
                      MyClazz mockMyClazz = mock(MyClazz.class, CALLS_REAL_METHODS);
                      doAnswer(invocation -> msg).when(mockMyClazz).getMsg();
                      try (MockedConstruction mockConsMyClazz = mockConstructionWithAnswer(MyClazz.class, delegatesTo(mockMyClazz))) {
              
                          // Should ignore the newClass statement
                          new Runnable() {
                              @Override
                              public void run() {
                                  System.out.println("run");
                              }
                          };
                          assertEquals("newMsg", new MyClazz().getMsg());
                      }
                  }
              
                  public static class MyClazz {
                      public String getMsg() {
                          return "msg";
                      }
                  }
              }
              """));
    }

    @Test
    void mockUpVoidTest() {
        //language=java
        rewriteRun(
          java(
            """
              import mockit.Mock;
              import mockit.MockUp;
              import static org.junit.Assert.assertEquals;
              import org.junit.Test;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      new MockUp<MockUpClass>() {
                          @Mock
                          public void changeMsg() {
                              MockUpClass.Save.msg = "mockMsg";
                          }
              
                          @Mock
                          public void changeText(String text) {
                              MockUpClass.Save.text = "mockText";
                          }
                      };
              
                      assertEquals("mockMsg", new MockUpClass().getMsg());
                      assertEquals("mockText", new MockUpClass().getText());
                  }
              
                  public static class MockUpClass {
                      public static class Save {
                          public static String msg = "msg";
                          public static String text = "text";
                      }
              
                      public final String getMsg() {
                          changeMsg();
                          return Save.msg;
                      }
              
                      public void changeMsg() {
                          Save.msg = "newMsg";
                      }
              
                      public String getText() {
                          changeText("newText");
                          return Save.text;
                      }
              
                      public static void changeText(String text) {
                          Save.text = text;
                      }
                  }
              }
              """,
            """
              import static org.junit.Assert.assertEquals;
              import static org.mockito.AdditionalAnswers.delegatesTo;
              import static org.mockito.Answers.CALLS_REAL_METHODS;
              import static org.mockito.ArgumentMatchers.*;
              import static org.mockito.Mockito.*;
              
              import org.junit.Test;
              import org.mockito.MockedConstruction;
              import org.mockito.MockedStatic;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      MockUpClass mockMockUpClass = mock(MockUpClass.class, CALLS_REAL_METHODS);
                      doAnswer(invocation -> {
                          MockUpClass.Save.msg = "mockMsg";
                          return null;
                      }).when(mockMockUpClass).changeMsg();
                      try (MockedStatic mockStaticMockUpClass = mockStatic(MockUpClass.class);MockedConstruction mockConsMockUpClass = mockConstructionWithAnswer(MockUpClass.class, delegatesTo(mockMockUpClass))) {
                          mockStaticMockUpClass.when(() -> MockUpClass.changeText(nullable(String.class))).thenAnswer(invocation -> {
                              String text = invocation.getArgument(0);
                              MockUpClass.Save.text = "mockText";
                              return null;
                          });
              
                          assertEquals("mockMsg", new MockUpClass().getMsg());
                          assertEquals("mockText", new MockUpClass().getText());
                      }
                  }
              
                  public static class MockUpClass {
                      public static class Save {
                          public static String msg = "msg";
                          public static String text = "text";
                      }
              
                      public final String getMsg() {
                          changeMsg();
                          return Save.msg;
                      }
              
                      public void changeMsg() {
                          Save.msg = "newMsg";
                      }
              
                      public String getText() {
                          changeText("newText");
                          return Save.text;
                      }
              
                      public static void changeText(String text) {
                          Save.text = text;
                      }
                  }
              }
              """));
    }

    @Test
    void mockUpAtSetUpWithoutTearDownTest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Before;
              import org.junit.Test;
              import mockit.Mock;
              import mockit.MockUp;
              import static org.junit.Assert.assertEquals;
              
              public class MockUpTest {
                  @Before
                  public void setUp() {
                      new MockUp<MyClazz>() {
                          @Mock
                          public String getMsg() {
                              return "mockMsg";
                          }
                      };
                  }
              
                  @Test
                  public void test() {
                      assertEquals("mockMsg", new MyClazz().getMsg());
                  }
              
                  public static class MyClazz {
                      public String getMsg() {
                          return "msg";
                      }
                  }
              }
              """,
            """
              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;
              import org.mockito.MockedConstruction;
              import static org.junit.Assert.assertEquals;
              import static org.mockito.AdditionalAnswers.delegatesTo;
              import static org.mockito.Answers.CALLS_REAL_METHODS;
              import static org.mockito.Mockito.*;
              
              public class MockUpTest {
                  private MockedConstruction mockConsMyClazz;
              
                  @Before
                  public void setUp() {
                      MyClazz mockMyClazz = mock(MyClazz.class, CALLS_REAL_METHODS);
                      doAnswer(invocation -> "mockMsg").when(mockMyClazz).getMsg();
                      mockConsMyClazz = mockConstructionWithAnswer(MyClazz.class, delegatesTo(mockMyClazz));
                  }
              
                  @After
                  public void tearDown() {
                      mockConsMyClazz.closeOnDemand();
                  }
              
                  @Test
                  public void test() {
                      assertEquals("mockMsg", new MyClazz().getMsg());
                  }
              
                  public static class MyClazz {
                      public String getMsg() {
                          return "msg";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mockUpAtSetUpWithTearDownTest() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.Before;
              import org.junit.After;
              import org.junit.Test;
              import mockit.Mock;
              import mockit.MockUp;
              import static org.junit.Assert.assertEquals;
              
              public class MockUpTest {
                  @Before
                  public void setUp() {
                      new MockUp<MyClazz>() {
                          @Mock
                          public String getMsg() {
                              return "mockMsg";
                          }
              
                          @Mock
                          public String getStaticMsg() {
                              return "mockStaticMsg";
                          }
                      };
                  }
              
                  @After
                  public void tearDown() {
                  }
              
                  @Test
                  public void test() {
                      assertEquals("mockMsg", new MyClazz().getMsg());
                      assertEquals("mockStaticMsg", MyClazz.getStaticMsg());
                  }
              
                  public static class MyClazz {
                      public String getMsg() {
                          return "msg";
                      }
              
                      public static String getStaticMsg() {
                          return "staticMsg";
                      }
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.junit.After;
              import org.junit.Test;
              import org.mockito.MockedConstruction;
              import org.mockito.MockedStatic;
              import static org.junit.Assert.assertEquals;
              import static org.mockito.AdditionalAnswers.delegatesTo;
              import static org.mockito.Answers.CALLS_REAL_METHODS;
              import static org.mockito.Mockito.*;
              
              public class MockUpTest {
                  private MockedConstruction mockConsMyClazz;
                  private MockedStatic mockStaticMyClazz;
              
                  @Before
                  public void setUp() {
                      MyClazz mockMyClazz = mock(MyClazz.class, CALLS_REAL_METHODS);
                      doAnswer(invocation -> "mockMsg").when(mockMyClazz).getMsg();
                      mockConsMyClazz = mockConstructionWithAnswer(MyClazz.class, delegatesTo(mockMyClazz));
                      mockStaticMyClazz = mockStatic(MyClazz.class);
                      mockStaticMyClazz.when(() -> MyClazz.getStaticMsg()).thenAnswer(invocation -> "mockStaticMsg");
                  }
              
                  @After
                  public void tearDown() {
                      mockConsMyClazz.closeOnDemand();
                      mockStaticMyClazz.closeOnDemand();
                  }
              
                  @Test
                  public void test() {
                      assertEquals("mockMsg", new MyClazz().getMsg());
                      assertEquals("mockStaticMsg", MyClazz.getStaticMsg());
                  }
              
                  public static class MyClazz {
                      public String getMsg() {
                          return "msg";
                      }
              
                      public static String getStaticMsg() {
                          return "staticMsg";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void mockUpWithParamsTest() {
        rewriteRun(
          //language=java
          java(
            """
              import mockit.Mock;
              import mockit.MockUp;
              import org.junit.Test;
              
              import static org.junit.Assert.assertEquals;
              
              public class MockUpTest {
                  @Test
                  public void init() {
                      new MockUp<MyClazz>() {
                          @Mock
                          public String getMsg(String foo, String bar, String unused) {
                              return foo + bar;
                          }
                      };
                      assertEquals("foobar", new MyClazz().getMsg("foo", "bar", "unused"));
                  }
              
                  public static class MyClazz {
                      public String getMsg(String foo, String bar, String unused) {
                          return "msg";
                      }
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.MockedConstruction;
              
              import static org.junit.Assert.assertEquals;
              import static org.mockito.AdditionalAnswers.delegatesTo;
              import static org.mockito.Answers.CALLS_REAL_METHODS;
              import static org.mockito.ArgumentMatchers.*;
              import static org.mockito.Mockito.*;
              
              public class MockUpTest {
                  @Test
                  public void init() {
                      MyClazz mockMyClazz = mock(MyClazz.class, CALLS_REAL_METHODS);
                      doAnswer(invocation -> {
                          String foo = invocation.getArgument(0);
                          String bar = invocation.getArgument(1);
                          return foo + bar;
                      }).when(mockMyClazz).getMsg(nullable(String.class), nullable(String.class), nullable(String.class));
                      try (MockedConstruction mockConsMyClazz = mockConstructionWithAnswer(MyClazz.class, delegatesTo(mockMyClazz))) {
                          assertEquals("foobar", new MyClazz().getMsg("foo", "bar", "unused"));
                      }
                  }
              
                  public static class MyClazz {
                      public String getMsg(String foo, String bar, String unused) {
                          return "msg";
                      }
                  }
              }
              """
          )
        );
    }
}
