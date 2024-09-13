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
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.testing.jmockit.JMockitTestUtils.setParserSettings;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class JMockitMockUpToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        setParserSettings(spec, "jmockit-1.22", "junit-4.13.2");
    }

    @DocumentExample
    @Test
    void mockUpStaticMethodTest() {
        //language=java
        rewriteRun(
          java("""
            import mockit.Mock;
            import mockit.MockUp;
            import static org.junit.Assert.assertEquals;
            
            class MockUpTest {
                void test() {
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
            import org.mockito.MockedStatic;
            
            import static org.junit.Assert.assertEquals;
            import static org.mockito.Mockito.*;
            
            class MockUpTest {
                void test() {
                    MockedStatic<MockUpTest.MyClazz> mockStaticMockUpTest_MyClazz = mockStatic(MockUpTest.MyClazz.class);
                    mockStaticMockUpTest_MyClazz.when(() -> MockUpTest.MyClazz.staticMethod()).thenAnswer(invocation -> {
                        return 1024;
                    });
                    mockStaticMockUpTest_MyClazz.when(() -> MockUpTest.MyClazz.staticMethod(anyInt())).thenAnswer(invocation -> {
                        return 128;
                    });
                    assertEquals(1024, MyClazz.staticMethod());
                    assertEquals(128, MyClazz.staticMethod(0));
                    mockStaticMockUpTest_MyClazz.close();
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
    void mockUpInstanceMethodTest() {
        //language=java
        rewriteRun(
          java("""
            import org.junit.Test;
            import mockit.Mock;
            import mockit.MockUp;
            import static org.junit.Assert.assertEquals;
            
            public class MockUpTest {
                @Test
                public void test() {
                    new MockUp<MyClazz>() {
                        @Mock
                        public String getMsg() {
                            return "mockMsg";
                        }
                        @Mock
                        public String getMsg(String echo) {
                            return "mockEchoMsg";
                        }
                    };
                    assertEquals("mockMsg", new MyClazz().getMsg());
                    assertEquals("mockEchoMsg", new MyClazz().getMsg("echo"));
                }
            
                public static class MyClazz {
                    public String getMsg() {
                        return "msg";
                    }
            
                    public String getMsg(String echo) {
                        return echo;
                    }
                }
            }
            """, """
            import org.junit.Test;
            import org.mockito.MockedConstruction;
            import static org.junit.Assert.assertEquals;
            import static org.mockito.Mockito.*;
            
            public class MockUpTest {
                @Test
                public void test() {
                    MockedConstruction<MockUpTest.MyClazz> mockObjMockUpTest_MyClazz = mockConstruction(MockUpTest.MyClazz.class, (mock, context) -> {
                        doAnswer(invocation -> {
                            return "mockEchoMsg";
                        }).when(mock).getMsg(nullable(String.class));
                        doAnswer(invocation -> {
                            return "mockMsg";
                        }).when(mock).getMsg();
                    });
                    assertEquals("mockMsg", new MyClazz().getMsg());
                    assertEquals("mockEchoMsg", new MyClazz().getMsg("echo"));
                    mockObjMockUpTest_MyClazz.close();
                }
            
                public static class MyClazz {
                    public String getMsg() {
                        return "msg";
                    }
            
                    public String getMsg(String echo) {
                        return echo;
                    }
                }
            }
            """));
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
            import static org.mockito.Mockito.*;
            
            public class MockUpTest {
                @Test
                public void test() {
                    final String msg = "newMsg";
                    MockedConstruction<MockUpTest.MyClazz> mockObjMockUpTest_MyClazz = mockConstruction(MockUpTest.MyClazz.class, (mock, context) -> {
                        doAnswer(invocation -> {
                            return msg;
                        }).when(mock).getMsg();
                    });
            
                    // Should ignore the newClass statement
                    new Runnable() {
                        @Override
                        public void run() {
                            System.out.println("run");
                        }
                    };
                    assertEquals("newMsg", new MyClazz().getMsg());
                    mockObjMockUpTest_MyClazz.close();
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
              import static org.mockito.Mockito.*;
              
              import org.junit.Test;
              import org.mockito.MockedConstruction;
              import org.mockito.MockedStatic;
              
              public class MockUpTest {
                  @Test
                  public void test() {
                      MockedConstruction<MockUpTest.MockUpClass> mockObjMockUpTest_MockUpClass = mockConstruction(MockUpTest.MockUpClass.class, (mock, context) -> {
                          doAnswer(invocation -> {
                              MockUpClass.Save.msg = "mockMsg";
                              return null;
                          }).when(mock).changeMsg();
                          when(mock.getText()).thenCallRealMethod();
                          when(mock.getMsg()).thenCallRealMethod();
                      });
                      MockedStatic<MockUpTest.MockUpClass> mockStaticMockUpTest_MockUpClass = mockStatic(MockUpTest.MockUpClass.class);
                      mockStaticMockUpTest_MockUpClass.when(() -> MockUpTest.MockUpClass.changeText(nullable(String.class))).thenAnswer(invocation -> {
                          MockUpClass.Save.text = "mockText";
                          return null;
                      });
              
                      assertEquals("mockMsg", new MockUpClass().getMsg());
                      assertEquals("mockText", new MockUpClass().getText());
                      mockStaticMockUpTest_MockUpClass.close();
                      mockObjMockUpTest_MockUpClass.close();
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
}
