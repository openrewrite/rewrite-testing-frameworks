package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class SimplifyChainedAssertJAssertionsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api-5.9", "assertj-core-3.24"));
    }

    @Test
    void stringIsEmpty() {
        rewriteRun(
          spec -> spec.recipe(new SimplifyChainedAssertJAssertions("isEmpty", "isTrue", "isEmpty")),
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString().isEmpty()).isTrue();
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void testMethod() {
                      assertThat(getString()).isEmpty();
                  }
              }
              """
          )
        );
    }
}
