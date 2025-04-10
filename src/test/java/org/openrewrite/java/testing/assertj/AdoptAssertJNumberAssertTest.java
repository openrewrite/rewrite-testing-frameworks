package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AdoptAssertJNumberAssertTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new AdoptAssertJNumberAssert())
          .parser(JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"));
    }

    @Test
    void isEqualToZeroToIsZero() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getNano()).isEqualTo(0);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getNano()).isZero();
                  }
              }
              """
          )
        );
    }

    @Test
    void isGreaterThanZeroToIsPositive() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isGreaterThan(0);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isPositive();
                  }
              }
              """
          )
        );
    }

    @Test
    void isLessThanZeroToIsNegative() {
        //language=java
        rewriteRun(
          java(
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isLessThan(0);
                  }
              }
              """,
            """
              import java.time.Duration;
              import java.time.temporal.Temporal;

              import static org.assertj.core.api.Assertions.assertThat;

              class Foo {
                  void testMethod(Temporal timestampA, Temporal timestampB) {
                      assertThat(Duration.between(timestampA, timestampB).getSeconds()).isNegative();
                  }
              }
              """
          )
        );
    }
}
