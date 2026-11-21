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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class EasyMockExpectLastCallToMockitoTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "easymock-5", "mockito-core-5"))
          .recipe(new EasyMockExpectLastCallToMockito());
    }

    @DocumentExample
    @Test
    void andThrowBecomesDoThrow() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test() {
                      Dependency dependency = createMock(Dependency.class);
                      dependency.action("a", 1);
                      expectLastCall().andThrow(new IllegalStateException());
                  }

                  interface Dependency {
                      void action(String s, int i);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.*;
              import static org.mockito.Mockito.doThrow;

              class ExampleTest {
                  void test() {
                      Dependency dependency = createMock(Dependency.class);
                      doThrow(new IllegalStateException()).when(dependency).action("a", 1);
                  }

                  interface Dependency {
                      void action(String s, int i);
                  }
              }
              """
          )
        );
    }

    @Test
    void callCountsOnlyAreRemovedWithTheRecordedCall() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;

              class ExampleTest {
                  void test() {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      dependency.action(EasyMock.anyObject(), EasyMock.anyInt());
                      EasyMock.expectLastCall().anyTimes();
                      dependency.other();
                      EasyMock.expectLastCall();
                      dependency.third();
                      EasyMock.expectLastCall().times(2);
                      dependency.fourth();
                      EasyMock.expectLastCall().andVoid().once();
                      EasyMock.replay(dependency);
                  }

                  interface Dependency {
                      void action(String s, int i);
                      void other();
                      void third();
                      void fourth();
                  }
              }
              """,
            """
              import org.easymock.EasyMock;

              class ExampleTest {
                  void test() {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      EasyMock.replay(dependency);
                  }

                  interface Dependency {
                      void action(String s, int i);
                      void other();
                      void third();
                      void fourth();
                  }
              }
              """
          )
        );
    }

    @Test
    void stubbedAndChainedBehaviors() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test() {
                      Dependency dependency = createMock(Dependency.class);
                      dependency.run();
                      expectLastCall().andStubThrow(new RuntimeException());
                      dependency.name(1);
                      expectLastCall().andReturn("a").times(2).andThrow(new RuntimeException("b"));
                      dependency.name(2);
                      expectLastCall().andStubReturn("c");
                  }

                  interface Dependency {
                      void run();
                      String name(int i);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.*;
              import static org.mockito.Mockito.doReturn;
              import static org.mockito.Mockito.doThrow;

              class ExampleTest {
                  void test() {
                      Dependency dependency = createMock(Dependency.class);
                      doThrow(new RuntimeException()).when(dependency).run();
                      doReturn("a").doThrow(new RuntimeException("b")).when(dependency).name(1);
                      doReturn("c").when(dependency).name(2);
                  }

                  interface Dependency {
                      void run();
                      String name(int i);
                  }
              }
              """
          )
        );
    }

    @Test
    void fieldMockAndThisQualifier() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;

              class ExampleTest {
                  private Dependency dependency = EasyMock.createMock(Dependency.class);

                  void test() throws Exception {
                      this.dependency.close();
                      EasyMock.expectLastCall().andThrow(new java.io.IOException("boom")).once();
                  }

                  interface Dependency extends AutoCloseable {
                      void close() throws java.io.IOException;
                  }
              }
              """,
            """
              import org.easymock.EasyMock;

              import static org.mockito.Mockito.doThrow;

              class ExampleTest {
                  private Dependency dependency = EasyMock.createMock(Dependency.class);

                  void test() throws Exception {
                      doThrow(new java.io.IOException("boom")).when(this.dependency).close();
                  }

                  interface Dependency extends AutoCloseable {
                      void close() throws java.io.IOException;
                  }
              }
              """
          )
        );
    }

    @Test
    void andAnswer() {
        //language=java
        rewriteRun(
          // `doAnswer` takes a Mockito `Answer`, which an `IAnswer` becomes only further on in `EasyMockToMockito`
          spec -> spec.afterTypeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              import org.easymock.IAnswer;

              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test(IAnswer<String> answer) {
                      Dependency dependency = createMock(Dependency.class);
                      dependency.name(3);
                      expectLastCall().andAnswer(answer).anyTimes();
                  }

                  interface Dependency {
                      String name(int i);
                  }
              }
              """,
            """
              import org.easymock.IAnswer;

              import static org.easymock.EasyMock.*;
              import static org.mockito.Mockito.doAnswer;

              class ExampleTest {
                  void test(IAnswer<String> answer) {
                      Dependency dependency = createMock(Dependency.class);
                      doAnswer(answer).when(dependency).name(3);
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
    void explicitStaticImportIsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.createMock;
              import static org.easymock.EasyMock.expectLastCall;

              class ExampleTest {
                  void test() {
                      Runnable dependency = createMock(Runnable.class);
                      dependency.run();
                      expectLastCall().andThrow(new IllegalStateException());
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.createMock;
              import static org.mockito.Mockito.doThrow;

              class ExampleTest {
                  void test() {
                      Runnable dependency = createMock(Runnable.class);
                      doThrow(new IllegalStateException()).when(dependency).run();
                  }
              }
              """
          )
        );
    }

    @Test
    void delegateTo() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test(Dependency real) {
                      Dependency dependency = createMock(Dependency.class);
                      dependency.run();
                      expectLastCall().andDelegateTo(real);
                  }

                  interface Dependency {
                      void run();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.*;
              import static org.mockito.AdditionalAnswers.delegatesTo;
              import static org.mockito.Mockito.doAnswer;

              class ExampleTest {
                  void test(Dependency real) {
                      Dependency dependency = createMock(Dependency.class);
                      doAnswer(delegatesTo(real)).when(dependency).run();
                  }

                  interface Dependency {
                      void run();
                  }
              }
              """
          )
        );
    }

    @Test
    void addToThePrecedingExpect() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test() {
                      Dependency dependency = createMock(Dependency.class);
                      expect(dependency.name(1)).andReturn("a");
                      expectLastCall().andThrow(new IllegalStateException()).once();
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
                      expect(dependency.name(1)).andReturn("a").andThrow(new IllegalStateException()).once();
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
    void leaveAloneWhenThereIsNoRecordedCallBefore() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test() {
                      record();
                      expectLastCall().andThrow(new RuntimeException());
                  }

                  void other() {
                      expectLastCall().anyTimes();
                  }

                  void record() {
                  }
              }
              """
          )
        );
    }

    @Test
    void leaveAloneWhenExpectationSettersAreKept() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.IExpectationSetters;

              import static org.easymock.EasyMock.*;

              class ExampleTest {
                  void test() {
                      Runnable dependency = createMock(Runnable.class);
                      dependency.run();
                      IExpectationSetters<Object> setters = expectLastCall();
                      setters.andThrow(new RuntimeException());
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
              import org.junit.Test;

              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = createMock(Dependency.class);
                      dependency.action(anyObject());
                      expectLastCall().andThrow(new IllegalArgumentException()).anyTimes();
                      dependency.other();
                      expectLastCall().once();
                      replay(dependency);
                  }

                  interface Dependency {
                      void action(Object o);
                      void other();
                  }
              }
              """,
            """
              import org.junit.Test;

              import static org.mockito.Mockito.any;
              import static org.mockito.Mockito.mock;
              import static org.mockito.Mockito.doThrow;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency dependency = mock(Dependency.class);
                      doThrow(new IllegalArgumentException()).when(dependency).action(any());
                  }

                  interface Dependency {
                      void action(Object o);
                      void other();
                  }
              }
              """
          )
        );
    }
}
