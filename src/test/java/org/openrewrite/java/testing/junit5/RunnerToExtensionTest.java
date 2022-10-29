package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

class RunnerToExtensionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit", "mockito"))
          .recipe(new RunnerToExtension(
              List.of("org.mockito.runners.MockitoJUnitRunner"),
              "org.mockito.junit.jupiter.MockitoExtension"
            )
          );
    }

    @Test
    void mockito() {
        rewriteRun(
          //language=java
          java(
            """
                  import org.junit.runner.RunWith;
                  import org.mockito.runners.MockitoJUnitRunner;
                  
                  @RunWith(MockitoJUnitRunner.class)
                  public class MyTest {
                  }
              """,
            """
                  import org.junit.jupiter.api.extension.ExtendWith;
                  import org.mockito.junit.jupiter.MockitoExtension;
                  
                  @ExtendWith(MockitoExtension.class)
                  public class MyTest {
                  }
              """
          )
        );
    }
}
