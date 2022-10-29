package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertFalseEqualToAssertNotEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(new AssertFalseEqualsToAssertNotEquals());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/206")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void assertFalseToAssertNotEquals() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      assertFalse(a.equals(c));
                      assertFalse(a.equals(c), "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertNotEquals;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      assertNotEquals(a, c);
                      assertNotEquals(a, c, "message");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/206")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void preserveStyleOfStaticImportOrNot() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      Assertions.assertFalse(a.equals(c), "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      Assertions.assertNotEquals(a, c, "message");
                  }
              }
              """
          )
        );
    }
}
