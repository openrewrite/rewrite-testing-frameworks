package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class RemoveEmptyTestsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new RemoveEmptyTests());
    }

    @Test
    void isNotTest() {
        //language=java
        rewriteRun(
          java(
            """
              class MyTest {
                  void method() {
                  }
              }
              """
          )
        );
    }

    @Test
    void emptyTestWithComments() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Test;
              class MyTest {
                  @Test
                  public void method() {
                      // comment
                  }
              }
              """
          )
        );
    }

    @Test
    void removeEmptyTest() {
        //language=java
        rewriteRun(

          java(
            """
              import org.junit.Test;
              class MyTest {
                  @Test
                  public void method() {
                  }
              }
              """,
            """
              import org.junit.Test;
              class MyTest {
              }
              """
          )
        );
    }
}
