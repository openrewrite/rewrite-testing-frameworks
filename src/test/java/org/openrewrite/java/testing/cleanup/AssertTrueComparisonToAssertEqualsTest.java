package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueComparisonToAssertEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(new AssertTrueComparisonToAssertEquals());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/204")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void assertTrueComparisonToAssertEqualsTest() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              public class Test {
                  void test() {
                      int a = 1;
                      int b = 1;
                      assertTrue(a == b);
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              public class Test {
                  void test() {
                      int a = 1;
                      int b = 1;
                      assertEquals(a, b);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/204")
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
                      int a = 1;
                      int b = 1;
                      Assertions.assertTrue(a == b);
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      int a = 1;
                      int b = 1;
                      Assertions.assertEquals(a, b);
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void preserveMessage() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      int a = 1;
                      int b = 1;
                      Assertions.assertTrue(a == b, "a does not equal b");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      int a = 1;
                      int b = 1;
                      Assertions.assertEquals(a, b, "a does not equal b");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/273")
    @SuppressWarnings({"SimplifiableAssertion", "StringEquality"})
    @Test
    void doNotChangeToEqualsWhenCheckingOnObjectIdentityWithStrings() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String b = "a";
                      Assertions.assertTrue(a == b);
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/273")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion", "UnnecessaryLocalVariable"})
    @Test
    void doNotChangeToEqualsWhenCheckingOnObjectIdentityWithObjects() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      Object a = new Object();
                      Object b = a;
                      Assertions.assertTrue(a == b);
                  }
              }
              """
          )
        );
    }

}
