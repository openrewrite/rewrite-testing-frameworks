/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueNullToAssertNullTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
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

    @Test
    void comparableComparedToZero() {
        rewriteRun(
          //language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
                
              public class Test {
                  void test() {
                      Integer a = 0;
                      assertTrue(a.compareTo(0) == 0);
                  }
              }
              """
          )
        );
    }
}
