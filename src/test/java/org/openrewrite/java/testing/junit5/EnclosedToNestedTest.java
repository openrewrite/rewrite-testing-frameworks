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
package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

class EnclosedToNestedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4"))
          .recipeFromResources("org.openrewrite.java.testing.junit5.JUnit4to5Migration");
    }

    @DocumentExample
    @Test
    void oneInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;

              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test
                      public void test() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test
                      public void test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void multipleInnerClasses() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;

              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test
                      public void test() {
                      }
                  }

                  public static class Inner2Test {
                      @Test
                      public void test() {
                      }

                      public static class InnermostTest {
                          @Test
                          public void test() {
                          }
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test
                      public void test() {
                      }
                  }

                  @Nested
                  public class Inner2Test {
                      @Test
                      public void test() {
                      }

                      @Nested
                      public class InnermostTest {
                          @Test
                          public void test() {
                          }
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void recognizesTestAnnotationWithArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;

              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test(timeout = 10)
                      public void test() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test
                      @Timeout(value = 10, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
                      public void test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void recognizesTestAnnotationWithTimeoutRuleAndArguments() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;
              import org.junit.Rule;
              import org.junit.rules.Timeout;

              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {

                      @Rule
                      public Timeout timeout = new Timeout(30);

                      @Test(timeout = 10)
                      public void test() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;
              import org.junit.jupiter.api.Timeout;

              import java.util.concurrent.TimeUnit;

              public class RootTest {
                  @Nested
                  @Timeout(value = 30, unit = TimeUnit.MILLISECONDS)
                  public class InnerTest {

                      @Test
                      @Timeout(value = 10, unit = TimeUnit.MILLISECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
                      public void test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void marksInnerClassWithStaticMembersBeforeJava16() {
        // `EnclosedToNested` removes the runner and delegates to `AddMissingNested`, which cannot
        // make this class an inner class before Java 16; the marker keeps the migration from
        // silently ending discovery of the tests.
        //language=java
        rewriteRun(
          version(
            java(
              """
                import org.junit.BeforeClass;
                import org.junit.Test;
                import org.junit.experimental.runners.Enclosed;
                import org.junit.runner.RunWith;

                @RunWith(Enclosed.class)
                public class RootTest {
                    public static class InnerTest {
                        @BeforeClass
                        public static void beforeAll() {
                        }

                        @Test
                        public void test() {
                        }
                    }
                }
                """,
              """
                import org.junit.jupiter.api.BeforeAll;
                import org.junit.jupiter.api.Test;

                public class RootTest {
                    /*~~(Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration)~~>*/public static class InnerTest {
                        @BeforeAll
                        public static void beforeAll() {
                        }

                        @Test
                        public void test() {
                        }
                    }
                }
                """
            ), 11)
        );
    }

    @Test
    void doesNotAnnotateNonTestInnerClasses() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              import org.junit.experimental.runners.Enclosed;
              import org.junit.runner.RunWith;

              @RunWith(Enclosed.class)
              public class RootTest {
                  public static class InnerTest {
                      @Test
                      public void test() {
                      }
                  }

                  public static class Foo {
                      public void bar() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              public class RootTest {
                  @Nested
                  public class InnerTest {
                      @Test
                      public void test() {
                      }
                  }

                  public static class Foo {
                      public void bar() {
                      }
                  }
              }
              """
          )
        );
    }
}
