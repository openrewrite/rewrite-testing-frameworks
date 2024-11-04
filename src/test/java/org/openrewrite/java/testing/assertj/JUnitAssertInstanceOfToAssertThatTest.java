package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JUnitAssertInstanceOfToAssertThatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new JUnitAssertInstanceOfToAssertThat());
    }

    @Test
    void convertsIsInstanceOf() {
        rewriteRun(
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertInstanceOf;

              class Test {
                  void test() {
                      assertInstanceOf(Integer.class, 4);
                  }
              }
              """,
            """
              import static org.assertj.core.api.Assertions.assertThat;

              class Test {
                  void test() {
                      assertThat(4).isInstanceOf(Integer.class);
                  }
              }
              """
          )
        );
    }
}
