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
package org.openrewrite.java.testing.easymock;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.javaVersion;

class EasyMockCaptureToMockitoArgumentCaptorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "easymock-5", "mockito-core-5"))
          .recipeFromResources("org.openrewrite.java.testing.easymock.EasyMockToMockito");
    }

    @DocumentExample
    @Test
    void captureBecomesArgumentCaptor() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.Capture;
              import org.easymock.CaptureType;
              import org.easymock.EasyMock;
              import org.junit.Test;

              import java.util.List;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertTrue;

              public class ExampleTest {
                  private final Capture<List<String>> lists = EasyMock.newCapture();

                  @Test
                  public void test() {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      Capture<String> names = EasyMock.newCapture(CaptureType.ALL);
                      Capture<Long> ids = Capture.newInstance();
                      EasyMock.expect(dependency.call(EasyMock.capture(names), EasyMock.captureLong(ids), EasyMock.capture(lists))).andReturn(true);
                      EasyMock.replay(dependency);

                      dependency.call("a", 1L, null);

                      assertTrue(names.hasCaptured());
                      assertEquals("a", names.getValue());
                      assertEquals(1, names.getValues().size());
                      assertEquals(Long.valueOf(1L), ids.getValue());
                  }

                  interface Dependency {
                      boolean call(String name, long id, List<String> list);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.ArgumentCaptor;
              import org.mockito.Mockito;

              import java.util.List;

              import static org.junit.Assert.assertEquals;
              import static org.junit.Assert.assertTrue;

              public class ExampleTest {
                  private final ArgumentCaptor<List<String>> lists = ArgumentCaptor.forClass((Class) List.class);

                  @Test
                  public void test() {
                      Dependency dependency = Mockito.mock(Dependency.class);
                      ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
                      ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
                      Mockito.when(dependency.call(names.capture(), ids.capture(), lists.capture())).thenReturn(true);

                      dependency.call("a", 1L, null);

                      assertTrue(!names.getAllValues().isEmpty());
                      assertEquals("a", names.getValue());
                      assertEquals(1, names.getAllValues().size());
                      assertEquals(Long.valueOf(1L), ids.getValue());
                  }

                  interface Dependency {
                      boolean call(String name, long id, List<String> list);
                  }
              }
              """
          )
        );
    }

    @Test
    void verifiedCallDoesNotCaptureAgain() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.Capture;
              import org.junit.Test;

              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = createMock(Dependency.class);
                      Capture<String> names = newCapture();
                      Capture<Long> ids = newCapture();
                      expect(dependency.call(capture(names), captureLong(ids))).andReturn(true);
                      replay(dependency);

                      dependency.call("a", 1L);

                      verify(dependency);
                  }

                  interface Dependency {
                      boolean call(String name, long id);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.ArgumentCaptor;

              import static org.mockito.Mockito.*;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = mock(Dependency.class);
                      ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
                      ArgumentCaptor<Long> ids = ArgumentCaptor.forClass(Long.class);
                      when(dependency.call(names.capture(), ids.capture())).thenReturn(true);

                      dependency.call("a", 1L);

                      verify(dependency).call(any(), anyLong());
                  }

                  interface Dependency {
                      boolean call(String name, long id);
                  }
              }
              """
          )
        );
    }

    @Test
    void typeOfAnyGivenForJava7() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.Capture;
              import org.junit.Test;

              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = createMock(Dependency.class);
                      Capture<String> names = newCapture();
                      expect(dependency.call(capture(names))).andReturn(true);
                      replay(dependency);

                      dependency.call("a");

                      verify(dependency);
                  }

                  interface Dependency {
                      boolean call(String name);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.ArgumentCaptor;
              import org.mockito.Mockito;

              import static org.mockito.Mockito.*;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = mock(Dependency.class);
                      ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
                      when(dependency.call(names.capture())).thenReturn(true);

                      dependency.call("a");

                      verify(dependency).call(Mockito.<String>any());
                  }

                  interface Dependency {
                      boolean call(String name);
                  }
              }
              """,
            spec -> spec.markers(javaVersion(7))
          )
        );
    }

    @Test
    void negatedHasCaptured() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.Capture;
              import org.easymock.EasyMock;

              class ExampleTest {
                  boolean test() {
                      Capture<String> names = EasyMock.newCapture();
                      return !names.hasCaptured();
                  }
              }
              """,
            """
              import org.mockito.ArgumentCaptor;

              class ExampleTest {
                  boolean test() {
                      ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
                      return names.getAllValues().isEmpty();
                  }
              }
              """
          )
        );
    }
}
