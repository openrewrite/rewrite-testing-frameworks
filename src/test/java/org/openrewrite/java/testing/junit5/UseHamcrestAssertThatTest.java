package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class UseHamcrestAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit", "hamcrest-core", "mockito-all"))
          .recipe(Environment.builder()
        .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
        .build()
        .activateRecipes("org.openrewrite.java.testing.junit5.UseHamcrestAssertThat"));
    }

    @Test
    void assertAssertThatToHamcrestMatcherAssert() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.hamcrest.CoreMatchers.is;
              import static org.junit.Assert.assertThat;
              
              class Test {
                  void test() {
                      assertThat(1 + 1, is(2));
                  }
              }
              """,
            """
              import static org.hamcrest.CoreMatchers.is;
              import static org.hamcrest.MatcherAssert.assertThat;
              
              class Test {
                  void test() {
                      assertThat(1 + 1, is(2));
                  }
              }
              """
          )
        );
    }
}
