package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssumeToAssumptionsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit", "hamcrest"));
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/54")
    @Test
    void assumeToAssumptions() {
        rewriteRun(
          spec -> spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit5BestPractices")),
          //language=java
          java(
            """
              import org.junit.Assume;
                              
              class Test {
                  void test() {
                      Assume.assumeTrue("One is one", true);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assumptions;
                              
              class Test {
                  void test() {
                      Assumptions.assumeTrue(true, "One is one");
                  }
              }
              """
          )
        );
    }
}
