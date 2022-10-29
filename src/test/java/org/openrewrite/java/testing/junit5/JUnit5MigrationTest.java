package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JUnit5MigrationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit", "hamcrest"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.junit5")
            .build()
            .activateRecipes("org.openrewrite.java.testing.junit5.JUnit4to5Migration"));
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/145")
    void assertThatReceiver() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.Assert;
              import org.junit.Test;

              import static java.util.Arrays.asList;
              import static org.hamcrest.Matchers.containsInAnyOrder;

              public class SampleTest {
                  @SuppressWarnings("ALL")
                  @Test
                  public void filterShouldRemoveUnusedConfig() {
                      Assert.assertThat(asList("1", "2", "3"),
                              containsInAnyOrder("3", "2", "1"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;

              import static java.util.Arrays.asList;
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.containsInAnyOrder;

              public class SampleTest {
                  @SuppressWarnings("ALL")
                  @Test
                  void filterShouldRemoveUnusedConfig() {
                      assertThat(asList("1", "2", "3"),
                              containsInAnyOrder("3", "2", "1"));
                  }
              }
              """
          )
        );
    }
}
