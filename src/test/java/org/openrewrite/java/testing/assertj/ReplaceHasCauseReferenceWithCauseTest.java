package org.openrewrite.java.testing.assertj;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ReplaceHasCauseReferenceWithCauseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceHasCauseReferenceWithCause())
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "assertj-core-3"));
    }

    @Test
    @DocumentExample
    void replaceHasCauseReference() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void test() {
                      Throwable cause = new RuntimeException("cause");
                      Throwable actual = new RuntimeException("actual", cause);
                      assertThat(actual).hasCauseReference(cause);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void test() {
                      Throwable cause = new RuntimeException("cause");
                      Throwable actual = new RuntimeException("actual", cause);
                      assertThat(actual).cause().isEqualTo(cause);
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceHasCauseReferenceWithNewInstance() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void test() {
                      Throwable actual = new RuntimeException("actual", new RuntimeException("specific cause"));
                      assertThat(actual).hasCauseReference(new RuntimeException("specific cause"));
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Test;
              import static org.assertj.core.api.Assertions.assertThat;
              
              class MyTest {
                  @Test
                  void test() {
                      Throwable actual = new RuntimeException("actual", new RuntimeException("specific cause"));
                      assertThat(actual).cause().isEqualTo(new RuntimeException("specific cause"));
                  }
              }
              """
          )
        );
    }
}
