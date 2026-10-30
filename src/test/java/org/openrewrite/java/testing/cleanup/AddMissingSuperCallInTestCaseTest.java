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
    }
}
