package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueLiteralFalseToFailTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new AssertTrueLiteralFalseToFail());
    }

    @Test
    void assertTrueToFail() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
                            
              public class Test {
                  void test() {
                      assertTrue(false, "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.fail;
                            
              public class Test {
                  void test() {
                      fail("message");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertTrueToFailNonStatic() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
                            
              public class Test {
                  void test() {
                      Assertions.assertTrue(false, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
                            
              public class Test {
                  void test() {
                      Assertions.fail("message");
                  }
              }
              """
          )
        );
    }

    @Test
    void assertTrueNonLiteralNoChange() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
                            
              public class Test {
                  void test() {
                      String a = "a";
                      String b = "b";
                      assertTrue(a.equals(b), "message");
                  }
              }
              """
          )
        );
    }

}