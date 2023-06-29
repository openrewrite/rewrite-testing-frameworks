package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class RemoveTryCatchBlocksFromUnitTestsTest implements RewriteTest {
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9", "junit-jupiter-params-5.9"))
          .recipe(new RemoveTryCatchBlocksFromUnitTests());
    }

    @Test
    @DocumentExample
    void removeTryCatchBlock() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.Assert.fail;
              import org.junit.jupiter.api.Test;
              
              class Test {
                @Test
                public void testMethod() {
                  try {
                    int divide = 50/0;
                  }catch (ArithmeticException e) {
                    Assert.fail(e.getMessage());
                  }
                }
              }
              """,
            """
              import static org.junit.Assert.fail;
              import org.junit.jupiter.api.Test;
              
              class Test {
                @Test
                public void testMethod() throws ArithmeticException {
                  int divide = 50/0;
                }
              }
              """
          )
        );
    }
}
