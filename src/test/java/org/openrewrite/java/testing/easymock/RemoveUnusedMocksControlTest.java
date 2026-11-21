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

class RemoveUnusedMocksControlTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4", "easymock-5"))
          .recipe(new RemoveUnusedMocksControl());
    }

    @DocumentExample
    @Test
    void removeUnusedFieldAndItsAssignment() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;
              import org.easymock.IMocksControl;
              import org.junit.Before;

              public class ExampleTest {
                  private IMocksControl control;
                  private IMocksControl other = EasyMock.createNiceControl();

                  @Before
                  public void setUp() {
                      control = EasyMock.createStrictControl();
                      this.other = EasyMock.createControl();
                  }
              }
              """,
            """
              import org.junit.Before;

              public class ExampleTest {

                  @Before
                  public void setUp() {
                  }
              }
              """
          )
        );
    }

    @Test
    void removeUnusedLocalVariable() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.IMocksControl;

              import static org.easymock.EasyMock.createControl;

              public class ExampleTest {
                  public void test() {
                      IMocksControl control = createControl();
                      Runnable runnable = () -> {};
                      runnable.run();
                  }
              }
              """,
            """
              public class ExampleTest {
                  public void test() {
                      Runnable runnable = () -> {};
                      runnable.run();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepControlThatIsStillUsed() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;
              import org.easymock.IMocksControl;

              public class ExampleTest {
                  private final IMocksControl control = EasyMock.createControl();

                  public void test() {
                      Runnable runnable = control.createMock(Runnable.class);
                  }

                  void helper(IMocksControl parameter) {
                  }
              }
              """
          )
        );
    }

    @Test
    void localVariablesOfTheSameNameAreToldApart() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.EasyMock;
              import org.easymock.IMocksControl;

              public class ExampleTest {
                  public void unused() {
                      IMocksControl control = EasyMock.createControl();
                  }

                  public void used() {
                      IMocksControl control = EasyMock.createControl();
                      helper(control);
                  }

                  void helper(IMocksControl control) {
                  }
              }
              """,
            """
              import org.easymock.EasyMock;
              import org.easymock.IMocksControl;

              public class ExampleTest {
                  public void unused() {
                  }

                  public void used() {
                      IMocksControl control = EasyMock.createControl();
                      helper(control);
                  }

                  void helper(IMocksControl control) {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepControlCreatedOtherwise() {
        //language=java
        rewriteRun(
          java(
            """
              import org.easymock.IMocksControl;

              public class ExampleTest {
                  private IMocksControl control = create();

                  IMocksControl create() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void controlsAndEasyMockSupportMigratedTogetherWithTheRestOfEasyMock() {
        //language=java
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.testing.easymock.EasyMockToMockito"),
          java(
            """
              import org.easymock.EasyMock;
              import org.easymock.EasyMockSupport;
              import org.easymock.IMocksControl;
              import org.junit.Test;

              public class ExampleTest extends EasyMockSupport {
                  private IMocksControl control;

                  @Test
                  public void test() {
                      control = EasyMock.createStrictControl();
                      Runnable a = control.createMock(Runnable.class);
                      Runnable b = control.createMock("b", Runnable.class);
                      Runnable c = control.mock("c", Runnable.class);
                      Runnable d = createMock(Runnable.class);
                      Runnable e = createNiceMock("e", Runnable.class);
                      Runnable f = strictMock(Runnable.class);
                      control.checkOrder(false);
                      control.makeThreadSafe(true);
                      control.replay();
                      replayAll();
                      a.run();
                      control.verify();
                      verifyAll();
                      control.resetToNice();
                      control.reset();
                      resetAll();
                      EasyMock.resetToDefault(b);
                  }
              }
              """,
            """
              import org.junit.Test;
              import org.mockito.Mockito;

              public class ExampleTest {

                  @Test
                  public void test() {
                      Runnable a = Mockito.mock(Runnable.class);
                      Runnable b = Mockito.mock(Runnable.class, "b");
                      Runnable c = Mockito.mock(Runnable.class, "c");
                      Runnable d = Mockito.mock(Runnable.class);
                      Runnable e = Mockito.mock(Runnable.class, "e");
                      Runnable f = Mockito.mock(Runnable.class);
                      a.run();
                      Mockito.reset(b);
                  }
              }
              """
          )
        );
    }
}
