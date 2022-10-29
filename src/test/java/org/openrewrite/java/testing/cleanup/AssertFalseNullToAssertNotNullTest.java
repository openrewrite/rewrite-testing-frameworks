package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertFalseNullToAssertNotNullTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .recipe(new AssertFalseNullToAssertNotNull());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/202")
    @SuppressWarnings({"ConstantConditions", "SimplifiableAssertion"})
    @Test
    void simplifyToAssertNull() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              
              public class Test {
                  void test() {
                      String a = null;
                      assertFalse(a == null);
                      assertFalse(a == null, "message");
                      
                      String b = null;
                      assertFalse(null == b);
                      assertFalse(null == b, "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertNotNull;
              
              public class Test {
                  void test() {
                      String a = null;
                      assertNotNull(a);
                      assertNotNull(a, "message");
                      
                      String b = null;
                      assertNotNull(b);
                      assertNotNull(b, "message");
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
                      Assertions.assertFalse(a == null);
                      Assertions.assertFalse(a == null, "message");
                      
                      String b = null;
                      Assertions.assertFalse(null == b);
                      Assertions.assertFalse(null == b, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = null;
                      Assertions.assertNotNull(a);
                      Assertions.assertNotNull(a, "message");
                      
                      String b = null;
                      Assertions.assertNotNull(b);
                      Assertions.assertNotNull(b, "message");
                  }
              }
              """
          )
        );
    }
}
