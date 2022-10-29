package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueEqualsToAssertEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(new AssertTrueEqualsToAssertEquals());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/206")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void assertTrueToAssertEquals() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String b = "b";
                      assertTrue(a.equals(b));
                      assertTrue(a.equals(b), "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String b = "b";
                      assertEquals(a, b);
                      assertEquals(a, b, "message");
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
                      String b = "b";
                      Assertions.assertTrue(a.equals(b));
                      Assertions.assertTrue(a.equals(b), "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String b = "b";
                      Assertions.assertEquals(a, b);
                      Assertions.assertEquals(a, b, "message");
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("SimplifiableAssertion")
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/238")
    @Test
    void retainArraysEquals() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.Arrays;
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      int[] a = {1, 2, 3};
                      int[] b = {1, 2, 3};
                      Assertions.assertTrue(Arrays.equals(a, b));
                  }
              }
              """
          )
        );
    }
}
