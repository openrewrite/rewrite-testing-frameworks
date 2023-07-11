package org.openrewrite.java.testing.hamcrest;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class ReplaceCloseToWithIsCloseToTest implements RewriteTest {
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "junit-jupiter-api-5.9",
              "hamcrest-2.2",
              "assertj-core-3.24"))
          .recipe(new ReplaceCloseToWithIsCloseTo());
    }

    @Test
    void replaceIsCloseTo() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.hamcrest.MatcherAssert.assertThat;
              import static org.hamcrest.Matchers.closeTo;
              
              class ATest {
                  @Test
                  void replaceCloseTo() {
                      assertThat(1.0, closeTo(2.0, 1.0));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              import static org.assertj.core.api.Assertions.within;
              
              class ATest {
                  @Test
                  void replaceCloseTo() {
                      assertThat(1.0).isCloseTo(2.0, within(1.0));
                  }
              }
              """
          )
        );
    }
}
