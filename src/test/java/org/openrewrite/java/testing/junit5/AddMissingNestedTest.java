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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings("JUnit3StyleTestMethodInJUnit4Class")
class AddMissingNestedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddMissingNested())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .parser(KotlinParser.builder()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"));
    }

    @DocumentExample
    @Test
    void oneInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              public class RootTest {
                  public class InnerTest {
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
              import org.junit.jupiter.api.Test;

              public class RootTest {
                  public class InnerTest {
                      @Test
                      public void test() {
                      }
                  }

                  public class Inner2Test {
                      @Test
                      public void test() {
                      }

                      public class InnermostTest {
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
    void doesNotAnnotationNonTestInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              public class RootTest {
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

    @Test
    void removesStatic() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

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
    void doesNotRemoveStaticWithStaticMembersBeforeJava16() {
        //language=java
        rewriteRun(
          version(
            java(
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    static class FieldTest {
                        static Object state = new Object();

                        @Test
                        void test() {
                        }
                    }

                    static class InitializerTest {
                        static {
                            System.out.println("initialize");
                        }

                        @Test
                        void test() {
                        }
                    }

                    static class MethodTest {
                        static void helper() {
                        }

                        @Test
                        void test() {
                        }
                    }

                    static class MemberTypeTest {
                        static class Helper {
                        }

                        @Test
                        void test() {
                        }
                    }

                    static class ImplicitlyStaticMemberTypeTest {
                        interface Helper {
                        }

                        @Test
                        void test() {
                        }
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */
                    static class FieldTest {
                        static Object state = new Object();

                        @Test
                        void test() {
                        }
                    }

                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */

                    static class InitializerTest {
                        static {
                            System.out.println("initialize");
                        }

                        @Test
                        void test() {
                        }
                    }

                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */

                    static class MethodTest {
                        static void helper() {
                        }

                        @Test
                        void test() {
                        }
                    }

                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */

                    static class MemberTypeTest {
                        static class Helper {
                        }

                        @Test
                        void test() {
                        }
                    }

                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */

                    static class ImplicitlyStaticMemberTypeTest {
                        interface Helper {
                        }

                        @Test
                        void test() {
                        }
                    }
                }
                """
            ), 15)
        );
    }

    @Test
    void doesNotRemoveStaticWithNonConstantFinalFieldBeforeJava16() {
        //language=java
        rewriteRun(
          version(
            java(
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    static class BoxedTest {
                        static final Integer BOXED = 1;

                        @Test
                        void test() {
                        }
                    }

                    static class ComputedTest {
                        static final int COMPUTED = "abc".length();

                        @Test
                        void test() {
                        }
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */
                    static class BoxedTest {
                        static final Integer BOXED = 1;

                        @Test
                        void test() {
                        }
                    }

                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */

                    static class ComputedTest {
                        static final int COMPUTED = "abc".length();

                        @Test
                        void test() {
                        }
                    }
                }
                """
            ), 11)
        );
    }

    @Test
    void doesNotRepeatTheMarkerOnASecondRun() {
        //language=java
        rewriteRun(
          version(
            java(
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    /* Not converted to `@Nested`: this class declares static members that may not be legal in an inner class before Java 16; tests in this class may not run and require manual migration */
                    static class InnerTest {
                        static Object state = new Object();

                        @Test
                        void test() {
                        }
                    }
                }
                """
            ), 11)
        );
    }

    @Test
    void removesStaticWithStaticMethodWithoutJavaVersion() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class RootTest {
                  static class InnerTest {
                      static void helper() {
                      }

                      @Test
                      void test() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              class RootTest {
                  @Nested
                  class InnerTest {
                      static void helper() {
                      }

                      @Test
                      void test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void removesStaticWithConstantVariablesBeforeJava16() {
        //language=java
        rewriteRun(
          version(
            java(
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    static class InnerTest {
                        static final int MAX = 1 + 1;
                        static final String NAME = "root";

                        @Test
                        void test() {
                        }
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Nested;
                import org.junit.jupiter.api.Test;

                class RootTest {
                    @Nested
                    class InnerTest {
                        static final int MAX = 1 + 1;
                        static final String NAME = "root";

                        @Test
                        void test() {
                        }
                    }
                }
                """
            ), 11)
        );
    }

    @Test
    void removesStaticWithInheritedStaticMembersBeforeJava16() {
        //language=java
        rewriteRun(
          version(
            java(
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    static class Base {
                        static void helper() {
                        }
                    }

                    static class InnerTest extends Base {
                        @Test
                        void test() {
                        }
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Nested;
                import org.junit.jupiter.api.Test;

                class RootTest {
                    static class Base {
                        static void helper() {
                        }
                    }

                    @Nested
                    class InnerTest extends Base {
                        @Test
                        void test() {
                        }
                    }
                }
                """
            ), 11)
        );
    }

    @Test
    void removesStaticWithStaticMemberFromJava16() {
        //language=java
        rewriteRun(
          version(
            java(
              """
                import org.junit.jupiter.api.Test;

                class RootTest {
                    static class InnerTest {
                        static Object state = new Object();

                        @Test
                        void test() {
                        }
                    }
                }
                """,
              """
                import org.junit.jupiter.api.Nested;
                import org.junit.jupiter.api.Test;

                class RootTest {
                    @Nested
                    class InnerTest {
                        static Object state = new Object();

                        @Test
                        void test() {
                        }
                    }
                }
                """
            ), 16)
        );
    }

    @Test
    void doesNotNestAnnotationType() {
        //language=java
        rewriteRun(
          java(
            """
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            import java.lang.annotation.Retention;
            import org.junit.jupiter.api.Test;

            public class SingleTest {
                @CustomTest
                public void test() {
                }

                @Retention(RUNTIME)
                @Test
                @interface CustomTest {
                }
            }
            """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/759")
    @Test
    void abstractInnerClass() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              public class RootTest {
                  public abstract class InnerTest {
                      @Test
                      public void test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Disabled("Should be enabled when Preconditions.not(new KotlinFileChecker<>()) is removed from the recipe")
    @Test
    void doesNotAnnotateKotlinObject() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class RootTest {
                  object InnerObject {
                      @Test
                      fun test() {
                      }
                  }
              }
              """
          )
        );
    }

    @Disabled("Should be enabled when Preconditions.not(new KotlinFileChecker<>()) is removed from the recipe")
    @Test
    void doesNotAnnotateKotlinCompanionObject() {
        //language=kotlin
        rewriteRun(
          kotlin(
            """
              import org.junit.jupiter.api.Test

              class RootTest {
                  companion object {
                      @Test
                      fun test() {
                      }
                  }
              }
              """
          )
        );
    }
}
