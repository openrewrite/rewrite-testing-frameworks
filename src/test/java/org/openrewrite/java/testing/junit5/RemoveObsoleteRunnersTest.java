package org.openrewrite.java.testing.junit5;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

class RemoveObsoleteRunnersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit"))
          .recipe(new RemoveObsoleteRunners(
            List.of(
              "org.junit.runners.JUnit4",
              "org.junit.runners.BlockJUnit4ClassRunner"
            )
          ));
    }

    @Test
    void removesRunWithJunit4() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.junit.runners.JUnit4;
              
              @RunWith(JUnit4.class)
              public class Foo {
              }
              """,
            """
              public class Foo {
              }
              """
          )
        );
    }

    @Test
    void removeRunWithBlockRunner() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.runner.RunWith;
              import org.junit.runners.BlockJUnit4ClassRunner;
              
              @RunWith(BlockJUnit4ClassRunner.class)
              public class Foo {
              }
              """,
            """
              public class Foo {
              }
              """
          )
        );
    }
}
