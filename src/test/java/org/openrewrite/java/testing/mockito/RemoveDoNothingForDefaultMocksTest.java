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
