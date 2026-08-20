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

import static org.openrewrite.java.Assertions.java;

class RemoveDoNothingForDefaultMocksTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "mockito-core", "junit-4"))
          .recipe(new RemoveDoNothingForDefaultMocks());
    }

    @DocumentExample
    @Test
    void removesDoNothingOnMockVoidMethod() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.ArgumentMatchers.anyString;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      doNothing().when(bufferedWriter).write(anyString());
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.ArgumentMatchers.anyString;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDoNothingWithArgMatcher() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.ArgumentMatcher;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.ArgumentMatchers.argThat;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      doNothing().when(bufferedWriter).write(argThat((ArgumentMatcher<String>) argument -> true));
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.ArgumentMatcher;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.ArgumentMatchers.argThat;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingOnSpyField() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Spy;
              import java.util.ArrayList;
              import java.util.List;

              import static org.mockito.Mockito.doNothing;

              class MyTest {
                  @Spy
                  private List<String> spyList = new ArrayList<>();

                  void test() {
                      doNothing().when(spyList).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingOnMockCallingRealMethods() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Answers;
              import org.mockito.Mock;
              import java.util.ArrayList;

              import static org.mockito.Mockito.doNothing;

              class MyTest {
                  @Mock(answer = Answers.CALLS_REAL_METHODS)
                  private ArrayList<String> partialMock;

                  void test() {
                      doNothing().when(partialMock).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingOnMockCallingRealMethodsWithStaticImport() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Mock;
              import java.util.ArrayList;

              import static org.mockito.Answers.CALLS_REAL_METHODS;
              import static org.mockito.Mockito.doNothing;

              class MyTest {
                  @Mock(answer = CALLS_REAL_METHODS)
                  private ArrayList<String> partialMock;

                  void test() {
                      doNothing().when(partialMock).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDoNothingOnMockWithDeepStubs() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Answers;
              import org.mockito.Mock;
              import java.util.ArrayList;

              import static org.mockito.Mockito.doNothing;

              class MyTest {
                  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
                  private ArrayList<String> list;

                  void test() {
                      doNothing().when(list).clear();
                  }
              }
              """,
            """
              import org.mockito.Answers;
              import org.mockito.Mock;
              import java.util.ArrayList;

              class MyTest {
                  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
                  private ArrayList<String> list;

                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingOnMockFieldReassignedToSpy() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Mock;
              import java.util.ArrayList;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.Mockito.spy;

              class MyTest {
                  @Mock
                  private ArrayList<String> list;

                  void setUp() {
                      list = spy(new ArrayList<>());
                  }

                  void test() {
                      doNothing().when(list).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDoNothingOnMockFieldAccessedThroughThis() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Mock;
              import java.util.ArrayList;

              import static org.mockito.Mockito.doNothing;

              class MyTest {
                  @Mock
                  private ArrayList<String> list;

                  void test() {
                      doNothing().when(this.list).clear();
                  }
              }
              """,
            """
              import org.mockito.Mock;
              import java.util.ArrayList;

              class MyTest {
                  @Mock
                  private ArrayList<String> list;

                  void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingOnMockFieldReassignedToSpyThroughThis() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Mock;
              import java.util.ArrayList;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.Mockito.spy;

              class MyTest {
                  @Mock
                  private ArrayList<String> list;

                  void setUp() {
                      this.list = spy(new ArrayList<>());
                  }

                  void test() {
                      doNothing().when(list).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingOnLocalSpyShadowingMockField() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.Mock;
              import java.util.ArrayList;
              import java.util.List;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.Mockito.spy;

              class MyTest {
                  @Mock
                  private List<String> list;

                  void test() {
                      List<String> list = spy(new ArrayList<>());
                      doNothing().when(list).clear();
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsChainedDoNothing() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.Mockito.doThrow;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      doThrow(new IOException()).doNothing().when(bufferedWriter).write("test");
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingChainedBeforeDoThrow() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.Mockito.doThrow;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      doNothing().doThrow(new IOException()).when(bufferedWriter).write("test");
                  }
              }
              """
          )
        );
    }

    @Test
    void removesMultipleDoNothingStatements() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;
              import static org.mockito.ArgumentMatchers.anyString;
              import static org.mockito.ArgumentMatchers.anyInt;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      doNothing().when(bufferedWriter).write(anyString());
                      doNothing().when(bufferedWriter).write(anyInt());
                      doNothing().when(bufferedWriter).flush();
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.ArgumentMatchers.anyString;
              import static org.mockito.ArgumentMatchers.anyInt;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingWithArgumentCaptor() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.ArgumentCaptor;
              import org.mockito.Mock;

              import static org.mockito.Mockito.doNothing;

              class ExampleTest {
                  @Mock
                  Client client;
                  ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

                  void setUp() {
                      doNothing().when(client).send(captor.capture());
                  }

                  interface Client {
                      void send(String message);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingWithNestedArgumentCaptor() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.mockito.ArgumentCaptor;
              import org.mockito.Mock;

              import static org.mockito.ArgumentMatchers.eq;
              import static org.mockito.Mockito.doNothing;

              class ExampleTest {
                  @Mock
                  Client client;
                  ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

                  void setUp() {
                      doNothing().when(client).send(eq("prefix"), captor.capture());
                  }

                  interface Client {
                      void send(String prefix, String message);
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingInSwitchExpressionArm() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      String status = System.getProperty("status", "ACTIVE");
                      switch (status) {
                          case "ACTIVE" -> doNothing().when(bufferedWriter).write("active");
                          default -> {}
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDoNothingInSwitchExpressionArmBlockLeavingEmptyBlock() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      String status = System.getProperty("status", "ACTIVE");
                      switch (status) {
                          case "ACTIVE" -> {
                              doNothing().when(bufferedWriter).write("active");
                          }
                          default -> {}
                      }
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      String status = System.getProperty("status", "ACTIVE");
                      switch (status) {
                          case "ACTIVE" -> {
                          }
                          default -> {}
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDoNothingWhenNotSoleStatementInSwitchExpressionArmBlock() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      String status = System.getProperty("status", "ACTIVE");
                      switch (status) {
                          case "ACTIVE" -> {
                              doNothing().when(bufferedWriter).write("active");
                              System.out.println("other statement");
                          }
                          default -> {}
                      }
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      String status = System.getProperty("status", "ACTIVE");
                      switch (status) {
                          case "ACTIVE" -> {
                              System.out.println("other statement");
                          }
                          default -> {}
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void retainsDoNothingInsideLambdaBody() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;
              import java.util.List;

              import static org.mockito.Mockito.doNothing;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      List<String> texts = List.of("first", "second");
                      texts.forEach(text -> doNothing().when(bufferedWriter).write(text));
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDoNothingInLambdaBlockLeavingEmptyBlock() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      Runnable r = () -> {
                          doNothing().when(bufferedWriter).write("test");
                      };
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      Runnable r = () -> {
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void removesDoNothingWhenNotSoleStatementInLambdaBlock() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doNothing;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      Runnable r = () -> {
                          doNothing().when(bufferedWriter).write("test");
                          System.out.println("other statement");
                      };
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      Runnable r = () -> {
                          System.out.println("other statement");
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWithoutDoNothing() {
        rewriteRun(
          //language=Java
          java(
            """
              import org.junit.Test;
              import org.junit.runner.RunWith;
              import org.mockito.Mock;
              import org.mockito.junit.MockitoJUnitRunner;
              import java.io.BufferedWriter;
              import java.io.IOException;

              import static org.mockito.Mockito.doThrow;

              @RunWith(MockitoJUnitRunner.class)
              class MyTest {
                  @Mock
                  private BufferedWriter bufferedWriter;

                  @Test
                  public void test() throws IOException {
                      doThrow(new IOException()).when(bufferedWriter).write("test");
                  }
              }
              """
          )
        );
    }
}
