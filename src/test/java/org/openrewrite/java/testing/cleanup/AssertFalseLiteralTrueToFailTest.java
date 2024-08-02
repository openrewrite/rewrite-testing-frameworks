package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertFalseLiteralTrueToFailTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new AssertFalseLiteralTrueToFail());
    }

    @Test
    void assertFalseToFail() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
                            
              public class Test {
                  void test() {
                      assertFalse(true, "message");
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
    void assertFalseToFailNonStatic() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
                            
              public class Test {
                  void test() {
                      Assertions.assertFalse(true, "message");
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
    void assertFalseNonLiteralNoChange() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
                            
              public class Test {
                  void test() {
                      String a1 = "a";
                      String a2 = "a";
                      assertFalse(a1.equals(a2), "message");
                  }
              }
              """
          )
        );
    }

}