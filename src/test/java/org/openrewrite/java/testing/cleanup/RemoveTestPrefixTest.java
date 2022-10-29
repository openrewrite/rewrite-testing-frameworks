package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveTestPrefixTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(new RemoveTestPrefix());
    }

    @Test
    void removeTestPrefixes() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMethod() {
                  }

                  @Test
                  void test_snake_case() {
                  }

                  @Nested
                  class NestedTestClass {
                      @Test
                      void testAnotherTestMethod() {
                      }
                  }

                  @Nested
                  class AnotherNestedTestClass {
                      @Test
                      void testYetAnotherTestMethod() {
                      }
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Nested;
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void method() {
                  }

                  @Test
                  void snake_case() {
                  }

                  @Nested
                  class NestedTestClass {
                      @Test
                      void anotherTestMethod() {
                      }
                  }

                  @Nested
                  class AnotherNestedTestClass {
                      @Test
                      void yetAnotherTestMethod() {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreTooShortMethodName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void test() {
                  }
              }
              """
          )
        );
    }


    @Test
    void ignoreOverriddenMethod() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              abstract class AbstractTest {
                  public abstract void testMethod();
              }
              
              class BTest extends AbstractTest {
                  @Test
                  @Override
                  public void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreInvalidName() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void test1Method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreKeyword() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testSwitch() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreNull() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testNull() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreUnderscoreOnly() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void test_() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreNotAnnotatedMethods() {
        //language=java
        rewriteRun(
          java(
            """
              class ATest {
                  void testMethod() {
                  }
              }
              """
          )
        );
    }

    @Test
    void ignoreToString() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testToString() {
                  }
              }
              """
          )
        );
    }

    @Test
    void renamedMethodExists() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;

              class ATest {
                  @Test
                  void testMyDoSomethingLogic() {
                  }
                  
                  void myDoSomethingLogic() {}
              }
              """
          )
        );
    }
}
