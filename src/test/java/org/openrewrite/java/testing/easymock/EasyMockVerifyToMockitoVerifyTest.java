/*
 * Copyright 2024 the original author or authors.
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

class EasyMockVerifyToMockitoVerifyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "easymock-5"))
          .recipe(new EasyMockVerifyToMockitoVerify());
    }

    @DocumentExample
    @Test
    void replaceEasyMockVerifyByMockitoVerify() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action("", 2)).andReturn("result");
                      expect(dependency2.action2());

                      Dependency dependency3 = createNiceMock(Dependency.class);
                      expect(dependency3.action("A", 1)).andReturn("result");
                      expect(dependency3.action2()).andReturn("result");
                      expect(dependency3.action3(3.3)).andReturn("result");

                      verify(dependency);
                      verify(dependency2);
                      verify(dependency3);
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                      String action3(double d);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.mockito.Mockito.verify;
              import static org.easymock.EasyMock.createNiceMock;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action("", 2)).andReturn("result");
                      expect(dependency2.action2());

                      Dependency dependency3 = createNiceMock(Dependency.class);
                      expect(dependency3.action("A", 1)).andReturn("result");
                      expect(dependency3.action2()).andReturn("result");
                      expect(dependency3.action3(3.3)).andReturn("result");

                      verify(dependency).action("", 2);
                      verify(dependency2).action("", 2);
                      verify(dependency2).action2();
                      verify(dependency3).action("A", 1);
                      verify(dependency3).action2();
                      verify(dependency3).action3(3.3);
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                      String action3(double d);
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleReplacement() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");
                      verify(dependency);
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.mockito.Mockito.verify;
              import static org.easymock.EasyMock.createNiceMock;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");
                      verify(dependency).action();
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleReplacementWithArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      verify(dependency);
                  }

                  interface Dependency {
                      String action(String s, int i);
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.mockito.Mockito.verify;
              import static org.easymock.EasyMock.createNiceMock;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      verify(dependency).action("", 2);
                  }

                  interface Dependency {
                      String action(String s, int i);
                  }
              }
              """
          )
        );
    }

    @Test
    void replacementWithMultipleMethods() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      expect(dependency.action2()).andReturn("result");
                      verify(dependency);
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.mockito.Mockito.verify;
              import static org.easymock.EasyMock.createNiceMock;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action("", 2)).andReturn("result");
                      expect(dependency.action2()).andReturn("result");
                      verify(dependency).action("", 2);
                      verify(dependency).action2();
                  }

                  interface Dependency {
                      String action(String s, int i);
                      String action2();
                  }
              }
              """
          )
        );
    }

    @Test
    void verifyWithoutExpectIsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                      verify(dependency);
                  }

                  interface Dependency {}
              }
              """,
            """
              import static org.easymock.EasyMock.createNiceMock;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = createNiceMock(Dependency.class);
                  }

                  interface Dependency {}
              }
              """
          )
        );
    }

    @Test
    void verifyAndDependenciesAllOverThePlace() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  public void testServiceMethod() {
                      verify(null);

                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");

                      Dependency dependency3 = createNiceMock(Dependency.class);
                      verify(dependency3);

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action()).andReturn("result");

                      verify(dependency2);
                      verify(dependency);
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """,
            """
              import static org.easymock.EasyMock.expect;
              import static org.mockito.Mockito.verify;
              import static org.easymock.EasyMock.createNiceMock;

              public class ExampleTest {
                  public void testServiceMethod() {

                      Dependency dependency = createNiceMock(Dependency.class);
                      expect(dependency.action()).andReturn("result");

                      Dependency dependency3 = createNiceMock(Dependency.class);

                      Dependency dependency2 = createNiceMock(Dependency.class);
                      expect(dependency2.action()).andReturn("result");

                      verify(dependency2).action();
                      verify(dependency).action();
                  }

                  interface Dependency {
                      String action();
                  }
              }
              """
          )
        );
    }

    @Test
    void verifyOfSeveralMocks() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency header = EasyMock.createMock(Dependency.class);
                      Dependency request = EasyMock.createMock(Dependency.class);
                      EasyMock.expect(header.action("value")).andReturn("BaseAgent").once();
                      EasyMock.expect(request.action2())
                        .andReturn("header");
                      EasyMock.replay(header, request);
                      EasyMock.verify(header, request);
                  }

                  interface Dependency {
                      String action(String s);
                      String action2();
                  }
              }
              """,
            """
              import org.easymock.EasyMock;

              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency header = EasyMock.createMock(Dependency.class);
                      Dependency request = EasyMock.createMock(Dependency.class);
                      EasyMock.expect(header.action("value")).andReturn("BaseAgent").once();
                      EasyMock.expect(request.action2())
                        .andReturn("header");
                      EasyMock.replay(header, request);
                      verify(header).action("value");
                      verify(request).action2();
                  }

                  interface Dependency {
                      String action(String s);
                      String action2();
                  }
              }
              """
          )
        );
    }

    @Test
    void expectedNumberOfCalls() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;

              public class ExampleTest {
                  public void testServiceMethod(int n) {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      EasyMock.expect(dependency.action(1)).andReturn("a").times(2);
                      EasyMock.expect(dependency.action(2)).andReturn("a").times(2).andReturn("b");
                      EasyMock.expect(dependency.action(3)).andReturn("a").atLeastOnce();
                      EasyMock.expect(dependency.action(4)).andReturn("a").times(2, 5);
                      EasyMock.expect(dependency.action(5)).andReturn("a").times(n);
                      EasyMock.expect(dependency.action(6)).andReturn("a").anyTimes();
                      EasyMock.expect(dependency.action(7)).andStubReturn("a");
                      EasyMock.expect(dependency.action(8)).andReturn("a").andStubReturn("b");
                      EasyMock.verify(dependency);
                  }

                  interface Dependency {
                      String action(int i);
                  }
              }
              """,
            """
              import org.easymock.EasyMock;

              import static org.mockito.Mockito.*;

              public class ExampleTest {
                  public void testServiceMethod(int n) {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      EasyMock.expect(dependency.action(1)).andReturn("a").times(2);
                      EasyMock.expect(dependency.action(2)).andReturn("a").times(2).andReturn("b");
                      EasyMock.expect(dependency.action(3)).andReturn("a").atLeastOnce();
                      EasyMock.expect(dependency.action(4)).andReturn("a").times(2, 5);
                      EasyMock.expect(dependency.action(5)).andReturn("a").times(n);
                      EasyMock.expect(dependency.action(6)).andReturn("a").anyTimes();
                      EasyMock.expect(dependency.action(7)).andStubReturn("a");
                      EasyMock.expect(dependency.action(8)).andReturn("a").andStubReturn("b");
                      verify(dependency, times(2)).action(1);
                      verify(dependency, times(3)).action(2);
                      verify(dependency, atLeastOnce()).action(3);
                      verify(dependency, atLeast(2)).action(4);
                      verify(dependency, times(n)).action(5);
                      verify(dependency, atLeastOnce()).action(8);
                  }

                  interface Dependency {
                      String action(int i);
                  }
              }
              """
          )
        );
    }

    @Test
    void sameCallExpectedInSetUpAndTest() {
        //language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "easymock-5")),
          java(
            """
              import org.junit.Before;
              import org.junit.Test;

              import static org.easymock.EasyMock.*;

              public class ExampleTest {
                  private Dependency market;

                  @Before
                  public void setUp() {
                      market = createMock(Dependency.class);
                      expect(market.getId()).andReturn("id");
                      expect(market.getName()).andReturn("name").anyTimes();
                  }

                  @Test
                  public void test() {
                      expect(market.getId()).andReturn("id");
                      expect(market.getId()).andReturn("id");
                      replay(market);
                      verify(market);
                  }

                  interface Dependency {
                      String getId();
                      String getName();
                  }
              }
              """,
            """
              import org.junit.Before;
              import org.junit.Test;

              import static org.easymock.EasyMock.*;
              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  private Dependency market;

                  @Before
                  public void setUp() {
                      market = createMock(Dependency.class);
                      expect(market.getId()).andReturn("id");
                      expect(market.getName()).andReturn("name").anyTimes();
                  }

                  @Test
                  public void test() {
                      expect(market.getId()).andReturn("id");
                      expect(market.getId()).andReturn("id");
                      replay(market);
                      verify(market, times(3)).getId();
                  }

                  interface Dependency {
                      String getId();
                      String getName();
                  }
              }
              """
          )
        );
    }

    @Test
    void expectLastCallOfVoidMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      dependency.run("a");
                      EasyMock.expectLastCall();
                      dependency.run("b");
                      EasyMock.expectLastCall().andThrow(new RuntimeException()).times(2);
                      dependency.run("c");
                      EasyMock.expectLastCall().anyTimes();
                      EasyMock.replay(dependency);
                      try {
                          dependency.run("a");
                      } finally {
                          EasyMock.verify(dependency);
                      }
                  }

                  interface Dependency {
                      void run(String s);
                  }
              }
              """,
            """
              import org.easymock.EasyMock;

              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  public void testServiceMethod() {
                      Dependency dependency = EasyMock.createMock(Dependency.class);
                      dependency.run("a");
                      EasyMock.expectLastCall();
                      dependency.run("b");
                      EasyMock.expectLastCall().andThrow(new RuntimeException()).times(2);
                      dependency.run("c");
                      EasyMock.expectLastCall().anyTimes();
                      EasyMock.replay(dependency);
                      try {
                          dependency.run("a");
                      } finally {
                          verify(dependency).run("a");
                          verify(dependency, times(2)).run("b");
                      }
                  }

                  interface Dependency {
                      void run(String s);
                  }
              }
              """
          )
        );
    }

    @Test
    void verifyOfNoMocksIsRemoved() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;

              public class ExampleTest {
                  public void testServiceMethod(Runnable runnable) {
                      EasyMock.replay(runnable);
                      runnable.run();
                      EasyMock.verify();
                  }
              }
              """,
            """
              import org.easymock.EasyMock;

              public class ExampleTest {
                  public void testServiceMethod(Runnable runnable) {
                      EasyMock.replay(runnable);
                      runnable.run();
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
              import org.easymock.EasyMock;
              import org.junit.Test;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency first = EasyMock.createMock(Dependency.class);
                      Dependency second = EasyMock.createMock(Dependency.class);
                      EasyMock.expect(first.action(1)).andReturn("a").times(2);
                      EasyMock.expect(second.action(2)).andStubReturn("b");
                      EasyMock.replay(first, second);
                      first.action(1);
                      first.action(1);
                      EasyMock.verify(first, second);
                  }

                  interface Dependency {
                      String action(int i);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.Mockito;

              import static org.mockito.Mockito.times;
              import static org.mockito.Mockito.verify;

              public class ExampleTest {
                  @Test
                  public void test() {
                      Dependency first = Mockito.mock(Dependency.class);
                      Dependency second = Mockito.mock(Dependency.class);
                      Mockito.when(first.action(1)).thenReturn("a");
                      Mockito.when(second.action(2)).thenReturn("b");
                      first.action(1);
                      first.action(1);
                      verify(first, times(2)).action(1);
                  }

                  interface Dependency {
                      String action(int i);
                  }
              }
              """
          )
        );
    }
}
