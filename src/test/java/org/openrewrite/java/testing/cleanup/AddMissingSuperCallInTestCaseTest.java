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
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddMissingSuperCallInTestCaseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddMissingSuperCallInTestCase())
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-4"));
    }

    //language=java
    private static final String BASE = """
      import junit.framework.TestCase;

      public abstract class Base extends TestCase {
          @Override
          protected void setUp() throws Exception {
              super.setUp();
              init();
          }

          @Override
          protected void tearDown() throws Exception {
              cleanup();
              super.tearDown();
          }

          void init() {
          }

          void cleanup() {
          }
      }
      """;

    @Nested
    class Added {

        @DocumentExample
        @Test
        void superSetUpGoesFirst() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          init();
                      }
                  }
                  """,
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          super.setUp();
                          init();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void superTearDownGoesLast() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void tearDown() throws Exception {
                          cleanup();
                      }
                  }
                  """,
                """
                  public class MyTest extends Base {
                      @Override
                      protected void tearDown() throws Exception {
                          cleanup();
                          super.tearDown();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void overrideWithoutAThrowsClauseGainsTheInheritedOne() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      public void setUp() {
                          init();
                      }
                  }
                  """,
                """
                  public class MyTest extends Base {
                      @Override
                      public void setUp() throws Exception {
                          super.setUp();
                          init();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void tearDownEndingInAnIfWithoutElseGetsTheCall() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      Object resource;

                      @Override
                      protected void tearDown() throws Exception {
                          if (resource == null) return;
                      }
                  }
                  """,
                """
                  public class MyTest extends Base {
                      Object resource;

                      @Override
                      protected void tearDown() throws Exception {
                          if (resource == null) return;
                          super.tearDown();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void interfaceQualifiedSuperCallDoesNotCount() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public abstract class Quiet extends TestCase {
                      @Override
                      protected void setUp() {
                          System.out.println("quiet");
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  public interface Fixture {
                      default void setUp() {
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MyTest extends Quiet implements Fixture {
                      @Override
                      public void setUp() {
                          Fixture.super.setUp();
                      }
                  }
                  """,
                """
                  public class MyTest extends Quiet implements Fixture {
                      @Override
                      public void setUp() {
                          super.setUp();
                          Fixture.super.setUp();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void subclassOfAParentLeftAloneStillGetsItsCall() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class Middle extends Base {
                      @Override
                      protected void setUp() {
                          init();
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MyTest extends Middle {
                      @Override
                      protected void setUp() {
                          System.out.println("setup");
                      }
                  }
                  """,
                """
                  public class MyTest extends Middle {
                      @Override
                      protected void setUp() {
                          super.setUp();
                          System.out.println("setup");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void narrowerThrowsClauseIsWidenedToTheInheritedOne() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  import java.io.IOException;

                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws IOException {
                          init();
                      }
                  }
                  """,
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          super.setUp();
                          init();
                      }
                  }
                  """
              )
            );
        }


        @Test
        void superCallOnlyInANestedClassDoesNotCount() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          Base nested = new Base() {
                              @Override
                              protected void setUp() throws Exception {
                                  super.setUp();
                              }
                          };
                      }
                  }
                  """,
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          super.setUp();
                          Base nested = new Base() {
                              @Override
                              protected void setUp() throws Exception {
                                  super.setUp();
                              }
                          };
                      }
                  }
                  """
              )
            );
        }

        @Test
        void bothSetUpAndTearDownMissingSuperCalls() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          init();
                      }

                      @Override
                      protected void tearDown() throws Exception {
                          cleanup();
                      }
                  }
                  """,
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          super.setUp();
                          init();
                      }

                      @Override
                      protected void tearDown() throws Exception {
                          cleanup();
                          super.tearDown();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void multiExceptionThrowsClauseIsAdded() {
            rewriteRun(
              //language=java
              java(
                """
                  import java.io.IOException;
                  import java.sql.SQLException;

                  import junit.framework.TestCase;

                  public abstract class MultiBase extends TestCase {
                      @Override
                      protected void setUp() throws IOException, SQLException {
                          super.setUp();
                          System.out.println("multi");
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MyTest extends MultiBase {
                      @Override
                      protected void setUp() {
                          System.out.println("setup");
                      }
                  }
                  """,
                """
                  import java.io.IOException;
                  import java.sql.SQLException;

                  public class MyTest extends MultiBase {
                      @Override
                      protected void setUp() throws IOException, SQLException {
                          super.setUp();
                          System.out.println("setup");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void grandparentDeclaresSetUp() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public abstract class Middle extends Base {
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MyTest extends Middle {
                      @Override
                      protected void setUp() throws Exception {
                          init();
                      }
                  }
                  """,
                """
                  public class MyTest extends Middle {
                      @Override
                      protected void setUp() throws Exception {
                          super.setUp();
                          init();
                      }
                  }
                  """
              )
            );
        }

    }

    @Nested
    class NotAdded {

        @Test
        void superCallAlreadyPresent() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          super.setUp();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void directTestCaseSubclassHasNothingToCall() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public class MyTest extends TestCase {
                      @Override
                      protected void setUp() throws Exception {
                          System.out.println("setup");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void missingThrowsClauseOnAnExtendedClassIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class Middle extends Base {
                      @Override
                      protected void setUp() {
                          init();
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MyTest extends Middle {
                      @Override
                      protected void setUp() {
                          super.setUp();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void tearDownEndingInAnIfElseThatReturnsIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      boolean skip;

                      @Override
                      protected void tearDown() throws Exception {
                          if (skip) {
                              return;
                          } else {
                              throw new IllegalStateException();
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void tearDownEndingInATryThatReturnsIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void tearDown() throws Exception {
                          try {
                              System.out.println("teardown");
                              return;
                          } finally {
                              System.out.println("done");
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void missingThrowsClauseOnAMethodCalledInTheClassIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      public void setUp() {
                          init();
                      }

                      public void testRestart() {
                          setUp();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void missingThrowsClauseOnAMethodCalledFromAnotherClassIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      public void setUp() {
                          init();
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  class Runner {
                      void run(MyTest test) {
                          test.setUp();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void missingThrowsClauseOnAnInterfaceImplementationIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public interface Fixture {
                      void setUp();
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MyTest extends Base implements Fixture {
                      @Override
                      public void setUp() {
                          init();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void missingThrowsClauseInheritedAsASubclassInterfaceImplementationIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public interface Fixture {
                      void setUp();
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class Middle extends Base {
                      @Override
                      public void setUp() {
                          init();
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class Leaf extends Middle implements Fixture {
                  }
                  """
              )
            );
        }

        @Test
        void inheritedExceptionTheSubclassCannotNameIsLeftAlone() {
            rewriteRun(
              //language=java
              java(
                """
                  package base;

                  import junit.framework.TestCase;

                  public abstract class Base extends TestCase {
                      protected static class SetupException extends Exception {
                      }

                      @Override
                      protected void setUp() throws SetupException {
                          System.out.println("base");
                      }
                  }
                  """
              ),
              //language=java
              java(
                """
                  package t;

                  import base.Base;

                  public class MyTest extends Base {
                      @Override
                      public void setUp() {
                          System.out.println("setup");
                      }
                  }
                  """
              )
            );
        }

        @Test
        void emptyOverrideTurningTheAncestorOffIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                      }

                      @Override
                      protected void tearDown() throws Exception {
                      }
                  }
                  """
              )
            );
        }

        @Test
        void narrowerThrowsClauseOnAnAnonymouslyExtendedClassIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  import java.io.IOException;

                  public class Middle extends Base {
                      @Override
                      protected void setUp() throws IOException {
                          init();
                      }

                      static Middle anonymous() {
                          return new Middle() {
                              @Override
                              protected void setUp() throws IOException {
                                  super.setUp();
                              }
                          };
                      }
                  }
                  """
              )
            );
        }

        @Test
        void qualifiedSuperCallCounts() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void setUp() throws Exception {
                          MyTest.super.setUp();
                      }
                  }
                  """
              )
            );
        }

        @Test
        void tearDownEndingInReturnWouldMakeTheCallUnreachable() {
            rewriteRun(
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void tearDown() throws Exception {
                          System.out.println("teardown");
                          return;
                      }
                  }
                  """
              )
            );
        }

        @Test
        void tearDownEndingInWhileLoopIsLeftAlone() {
            rewriteRun(
              //language=java
              java(BASE),
              //language=java
              java(
                """
                  public class MyTest extends Base {
                      @Override
                      protected void tearDown() throws Exception {
                          while (true) {
                              break;
                          }
                      }
                  }
                  """
              )
            );
        }

        @Test
        void abstractAncestorDeclarationCannotBeCalled() {
            rewriteRun(
              //language=java
              java(
                """
                  import junit.framework.TestCase;

                  public abstract class AbstractBase extends TestCase {
                      @Override
                      protected abstract void setUp() throws Exception;
                  }
                  """
              ),
              //language=java
              java(
                """
                  public class MyTest extends AbstractBase {
                      @Override
                      protected void setUp() throws Exception {
                          System.out.println("setup");
                      }
                  }
                  """
              )
            );
        }

    }
}
