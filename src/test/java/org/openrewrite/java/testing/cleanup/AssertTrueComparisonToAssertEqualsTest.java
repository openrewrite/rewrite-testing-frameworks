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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertTrueComparisonToAssertEqualsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
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

    @DocumentExample
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
