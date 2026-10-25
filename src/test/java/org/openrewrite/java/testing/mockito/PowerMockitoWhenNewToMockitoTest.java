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

class PowerMockitoWhenNewToMockitoTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-4",
              "mockito-core-3.12",
              "powermock-api-mockito2-2",
              "powermock-api-support-2",
              "powermock-core-2",
              "powermock-reflect-2"))
          .recipe(new PowerMockitoWhenNewToMockito());
    }

    @DocumentExample
    @Test
    void whenNewInSetUpStaysActiveForEachTest() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;

              import org.junit.Before;
              import org.junit.Test;

              import static org.mockito.Mockito.mock;
              import static org.powermock.api.mockito.PowerMockito.whenNew;

              public class MyTest {
                  private File file = mock(File.class);

                  @Before
                  public void setUp() throws Exception {
                      whenNew(File.class).withArguments("data.txt").thenReturn(file);
                  }

                  @Test
                  public void test() {
                  }
              }
              """,
            """
              import java.io.File;

              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;

              import static org.mockito.Mockito.mock;

              public class MyTest {
                  private MockedConstruction<File> mockedConstructionFile;
                  private File file = mock(File.class);

                  @Before
                  public void setUp() throws Exception {
                      mockedConstructionFile = Mockito.mockConstructionWithAnswer(File.class, AdditionalAnswers.delegatesTo(file));
                  }

                  @After
                  public void tearDownStaticMocks() {
                      mockedConstructionFile.closeOnDemand();
                  }

                  @Test
                  public void test() {
                  }
              }
              """
          )
        );
    }

    @Test
    void sameTypeStubbedTwiceInOneMethodIsLeftAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;

              import org.junit.Test;

              import static org.mockito.Mockito.mock;
              import static org.powermock.api.mockito.PowerMockito.whenNew;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      whenNew(File.class).withArguments("a.txt").thenReturn(mock(File.class));
                      whenNew(File.class).withArguments("b.txt").thenReturn(mock(File.class));
                  }
              }
              """
          )
        );
    }

    @Test
    void sameTypeStubbedAgainInNestedBlockIsLeftAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;

              import org.junit.Test;

              import static org.mockito.Mockito.mock;
              import static org.powermock.api.mockito.PowerMockito.whenNew;

              public class MyTest {
                  boolean other;

                  @Test
                  public void test() throws Exception {
                      whenNew(File.class).withArguments("a.txt").thenReturn(mock(File.class));
                      if (other) {
                          whenNew(File.class).withArguments("b.txt").thenReturn(mock(File.class));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void restubbingInTestClosesMockFromSetUp() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;

              import org.junit.Before;
              import org.junit.Test;

              import static org.mockito.Mockito.mock;
              import static org.powermock.api.mockito.PowerMockito.whenNew;

              public class MyTest {
                  @Before
                  public void setUp() throws Exception {
                      whenNew(File.class).withAnyArguments().thenReturn(mock(File.class));
                  }

                  @Test
                  public void test() throws Exception {
                      whenNew(File.class).withAnyArguments().thenReturn(mock(File.class));
                  }
              }
              """,
            """
              import java.io.File;

              import org.junit.After;
              import org.junit.Before;
              import org.junit.Test;
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;

              import static org.mockito.Mockito.mock;

              public class MyTest {
                  private MockedConstruction<File> mockedConstructionFile;

                  @Before
                  public void setUp() throws Exception {
                      mockedConstructionFile = Mockito.mockConstructionWithAnswer(File.class, AdditionalAnswers.delegatesTo(mock(File.class)));
                  }

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedConstructionFile != null) {
                          mockedConstructionFile.closeOnDemand();
                      }
                  }

                  @Test
                  public void test() throws Exception {
                      mockedConstructionFile.closeOnDemand();
                      mockedConstructionFile = Mockito.mockConstructionWithAnswer(File.class, AdditionalAnswers.delegatesTo(mock(File.class)));
                  }
              }
              """
          )
        );
    }

    @Test
    void whenNewInLoopClosesPreviousIteration() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;

              import org.junit.Test;

              import static org.mockito.Mockito.mock;
              import static org.powermock.api.mockito.PowerMockito.whenNew;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      for (String name : new String[]{"a.txt", "b.txt"}) {
                          whenNew(File.class).withArguments(name).thenReturn(mock(File.class));
                      }
                  }
              }
              """,
            """
              import java.io.File;

              import org.junit.After;
              import org.junit.Test;
              import org.mockito.AdditionalAnswers;
              import org.mockito.MockedConstruction;
              import org.mockito.Mockito;

              import static org.mockito.Mockito.mock;

              public class MyTest {
                  private MockedConstruction<File> mockedConstructionFile;

                  @After
                  public void tearDownStaticMocks() {
                      if (mockedConstructionFile != null) {
                          mockedConstructionFile.closeOnDemand();
                      }
                  }

                  @Test
                  public void test() throws Exception {
                      for (String name : new String[]{"a.txt", "b.txt"}) {
                          if (mockedConstructionFile != null) {
                              mockedConstructionFile.closeOnDemand();
                          }
                          mockedConstructionFile = Mockito.mockConstructionWithAnswer(File.class, AdditionalAnswers.delegatesTo(mock(File.class)));
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void sameTypeAlsoStubbedToThrowIsLeftAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import java.io.File;
              import java.io.IOException;

              import org.junit.Test;

              import static org.mockito.Mockito.mock;
              import static org.powermock.api.mockito.PowerMockito.whenNew;

              public class MyTest {
                  @Test
                  public void test() throws Exception {
                      whenNew(File.class).withArguments("bad.txt").thenThrow(new IOException());
                      whenNew(File.class).withArguments("good.txt").thenReturn(mock(File.class));
                  }
              }
              """
          )
        );
    }
}
