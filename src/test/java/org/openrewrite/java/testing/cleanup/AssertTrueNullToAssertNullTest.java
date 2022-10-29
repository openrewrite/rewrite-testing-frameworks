package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueNullToAssertNullTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(new AssertTrueNullToAssertNull());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/202")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void simplifyToAssertNull() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              public class Test {
                  void test() {
                      String a = null;
                      assertTrue(a == null);
                      assertTrue(a == null, "message");
                      
                      String b = null;
                      assertTrue(null == b);
                      assertTrue(null == b, "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertNull;
              
              public class Test {
                  void test() {
                      String a = null;
                      assertNull(a);
                      assertNull(a, "message");
                      
                      String b = null;
                      assertNull(b);
                      assertNull(b, "message");
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/202")
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
                      String a = null;
                      Assertions.assertTrue(a == null);
                      Assertions.assertTrue(a == null, "message");
                      
                      String b = null;
                      Assertions.assertTrue(null == b);
                      Assertions.assertTrue(null == b, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = null;
                      Assertions.assertNull(a);
                      Assertions.assertNull(a, "message");
                      
                      String b = null;
                      Assertions.assertNull(b);
                      Assertions.assertNull(b, "message");
                  }
              }
              """
          )
        );
    }
}
