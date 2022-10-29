package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings({"SimplifiableAssertion", "ConstantConditions", "ObviousNullCheck", "EqualsWithItself"})
class CleanupAssertionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.CleanupAssertions"));
    }

    @Test
    void assertTrueComparisonNullToAssertNull() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;
                          
              class ExampleTest {
                  @Test
                  void test() {
                      Assertions.assertTrue("" == null);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;

              class ExampleTest {
                  @Test
                  void test() {
                      Assertions.assertNull("");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertFalseNegatedEqualsToAssertEquals() {
        //language=java
        rewriteRun(
          java(
            """
                  import org.junit.jupiter.api.Assertions;
                  import org.junit.jupiter.api.Test;
                
                  class ExampleTest {
                      @Test
                      void test() {
                          Assertions.assertFalse(!"".equals(""));
                      }
                  }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;

              class ExampleTest {
                  @Test
                  void test() {
                      Assertions.assertEquals("", "");
                  }
              }
              """
          ));
    }

    @Test
    void assertFalseNegatedEqualsNullToAssertNull() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;
                          
              class ExampleTest {
                  @Test
                  void test() {
                      Assertions.assertFalse(!"".equals(null));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              import org.junit.jupiter.api.Test;

              class ExampleTest {
                  @Test
                  void test() {
                      Assertions.assertNull("");
                  }
              }
              """
          )
        );
    }
}
