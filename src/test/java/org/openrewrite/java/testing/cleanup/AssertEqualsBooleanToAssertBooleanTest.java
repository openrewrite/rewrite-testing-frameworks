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

class AssertEqualsBooleanToAssertBooleanTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9"))
          .recipe(new AssertEqualsBooleanToAssertBoolean());
    }

    @DocumentExample
    @SuppressWarnings({"ConstantConditions"})
    @Test
    void assertEqualsFalseToToAssertFalse() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      boolean b = false;
                      assertEquals(false, b);
                      assertEquals(false, a.equals(c));
                      assertEquals(false, a.equals(c), "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      boolean b = false;
                      assertFalse(b);
                      assertFalse(a.equals(c));
                      assertFalse(a.equals(c), "message");
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
                      Assertions.assertEquals(false, a.equals(c), "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      String a = "a";
                      String c = "c";
                      Assertions.assertFalse(a.equals(c), "message");
                  }
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/587")
    void assertTrueWithNonBoolean() {
        rewriteRun(
          spec -> spec.recipe(new AssertEqualsBooleanToAssertBoolean()),
          // language=java
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertEquals;
              class Main {
                void foo() {
                  assertEquals(true, new Object());
                }
              }
              """
          )
        );
    }
}
