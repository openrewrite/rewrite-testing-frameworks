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

class EasyMockIAnswerToMockitoAnswerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "easymock-5", "mockito-core-5"))
          .recipe(new EasyMockIAnswerToMockitoAnswer());
    }

    @DocumentExample
    @Test
    void lambdaGetsTheInvocation() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test() {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.name(anyInt())).andAnswer(() -> "n" + getCurrentArguments()[0]);
                      expect(dependency.name(eq(1))).andStubAnswer(() -> {
                          Integer i = getCurrentArgument(0);
                          return "m" + i;
                      });
                      expect(dependency.name(eq(2))).andAnswer(() -> "constant");
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test() {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.name(anyInt())).andAnswer(invocation -> "n" + invocation.getArguments()[0]);
                      expect(dependency.name(eq(1))).andStubAnswer(invocation -> {
                          Integer i = invocation.getArgument(0);
                          return "m" + i;
                      });
                      expect(dependency.name(eq(2))).andAnswer(invocation -> "constant");
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """
          )
        );
    }

    @Test
    void anonymousClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;
              import org.easymock.IAnswer;

              class ExampleTest {
                  void test() {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      EasyMock.expect(dependency.name(EasyMock.anyInt())).andAnswer(new IAnswer<String>() {
                          @Override
                          public String answer() throws Throwable {
                              Object[] args = EasyMock.getCurrentArguments();
                              return "n" + args[0];
                          }
                      });
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """,
            """
              import org.easymock.EasyMock;
              import org.easymock.IAnswer;
              import org.mockito.invocation.InvocationOnMock;

              class ExampleTest {
                  void test() {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      EasyMock.expect(dependency.name(EasyMock.anyInt())).andAnswer(new IAnswer<String>() {
                          @Override
                          public String answer(InvocationOnMock invocation) throws Throwable {
                              Object[] args = invocation.getArguments();
                              return "n" + args[0];
                          }
                      });
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """
          )
        );
    }

    @Test
    void namedClassAndMethodReturningAnAnswer() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.IAnswer;

              import static org.easymock.EasyMock.getCurrentArguments;

              class Answers {
                  static class FirstArgument implements IAnswer<Object> {
                      public Object answer() {
                          return getCurrentArguments()[0];
                      }
                  }

                  static IAnswer<String> echo(String suffix) {
                      return () -> getCurrentArguments()[0] + suffix;
                  }
              }
              """,
            """
              import org.easymock.IAnswer;
              import org.mockito.invocation.InvocationOnMock;

              class Answers {
                  static class FirstArgument implements IAnswer<Object> {
                      public Object answer(InvocationOnMock invocation) {
                          return invocation.getArguments()[0];
                      }
                  }

                  static IAnswer<String> echo(String suffix) {
                      return invocation -> invocation.getArguments()[0] + suffix;
                  }
              }
              """
          )
        );
    }

    @Test
    void parameterNameDoesNotClash() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test(String invocation) {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.name(anyInt())).andAnswer(() -> invocation + getCurrentArguments()[0]);
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test(String invocation) {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.name(anyInt())).andAnswer(invocationOnMock -> invocation + invocationOnMock.getArguments()[0]);
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodReferenceBecomesLambda() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Enumeration;
              import java.util.Vector;

              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test(Vector<String> names) {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.names()).andAnswer(names::elements).anyTimes();
                      expect(dependency.size()).andStubAnswer(names::size);
                  }

                  interface Dependency {
                      Enumeration<String> names();
                      int size();
                  }
              }
              """,
            """
              import java.util.Enumeration;
              import java.util.Vector;

              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test(Vector<String> names) {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.names()).andAnswer(invocation -> names.elements()).anyTimes();
                      expect(dependency.size()).andStubAnswer(invocation -> names.size());
                  }

                  interface Dependency {
                      Enumeration<String> names();
                      int size();
                  }
              }
              """
          )
        );
    }

    @Test
    void currentArgumentsOutsideAnAnswerAreLeftAlone() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.getCurrentArguments;

              class Helper {
                  static Object first() {
                      return getCurrentArguments()[0];
                  }
              }
              """
          )
        );
    }

    @Test
    void migratedTogetherWithTheRestOfEasyMock() {
        //language=java
        rewriteRun(
          spec -> spec
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "easymock-5"))
            .recipeFromResources("org.openrewrite.java.testing.easymock.EasyMockToMockito"),
          java(
            """
              import org.easymock.IAnswer;
              import org.junit.Test;

              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.name(anyInt())).andAnswer(() -> "n" + getCurrentArguments()[0]);
                      expect(dependency.name(eq(1))).andAnswer(new IAnswer<String>() {
                          @Override
                          public String answer() {
                              return "m" + getCurrentArgument(0);
                          }
                      });
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.invocation.InvocationOnMock;
              import org.mockito.stubbing.Answer;

              import static org.mockito.Mockito.*;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = mock(Dependency.class);
                      when(dependency.name(anyInt())).thenAnswer(invocation -> "n" + invocation.getArguments()[0]);
                      when(dependency.name(1)).thenAnswer(new Answer<String>() {
                          @Override
                          public String answer(InvocationOnMock invocation) {
                              return "m" + invocation.getArgument(0);
                          }
                      });
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """
          )
        );
    }
}
