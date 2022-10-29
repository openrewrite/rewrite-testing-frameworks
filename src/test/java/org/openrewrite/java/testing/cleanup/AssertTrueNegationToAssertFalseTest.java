package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueNegationToAssertFalseTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(new AssertTrueNegationToAssertFalse());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/205")
    @SuppressWarnings({"SimplifiableAssertion"})
    @Test
    void assertTrueNegationToAssertFalse() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              public class Test {
                  void test() {
                      boolean a = false;
                      assertTrue(!a);
                      assertTrue(!a, "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              
              public class Test {
                  void test() {
                      boolean a = false;
                      assertFalse(a);
                      assertFalse(a, "message");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/205")
    @SuppressWarnings({"SimplifiableAssertion"})
    @Test
    void preserveStyleOfStaticImportOrNot() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      boolean a = false;
                      Assertions.assertTrue(!a);
                      Assertions.assertTrue(!a, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      boolean a = false;
                      Assertions.assertFalse(a);
                      Assertions.assertFalse(a, "message");
                  }
              }
              """
          )
        );
    }
}
