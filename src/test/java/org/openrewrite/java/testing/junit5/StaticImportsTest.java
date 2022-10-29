package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class StaticImportsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices"));
    }

    @Test
    void useAssertionsStaticImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;

              public class Test {
                  void method() {
                      Assertions.assertTrue(true);
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;

              public class Test {
                  void method() {
                      assertTrue(true);
                  }
              }
              """
          )
        );
    }
}
