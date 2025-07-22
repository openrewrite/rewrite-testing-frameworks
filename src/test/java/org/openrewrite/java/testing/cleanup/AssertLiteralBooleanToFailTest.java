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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AssertLiteralBooleanToFailTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5"))
          .recipe(new AssertLiteralBooleanToFailRecipe());
    }

    @DocumentExample
    @SuppressWarnings("SimplifiableAssertion")
    @Test
    void assertWithStaticImports() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertTrue;

              public class Test {
                  void test() {
                      assertFalse(true, "assert false true");
                      assertTrue(false, "assert true false");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.fail;

              public class Test {
                  void test() {
                      fail("assert false true");
                      fail("assert true false");
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("SimplifiableAssertion")
    @Test
    void assertWithAssertionsImport() {
        //language=java
        rewriteRun(
          java(
            """
              import org.junit.jupiter.api.Assertions;

              public class Test {
                  void test() {
                      Assertions.assertFalse(true, "assert false true");
                      Assertions.assertTrue(false, "assert true false");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.fail;

              public class Test {
                  void test() {
                      fail("assert false true");
                      fail("assert true false");
                  }
              }
              """
          )
        );
    }

    @Test
    void noChangeWhenNotLiteral() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertTrue;

              public class Test {
                  void test(boolean a, Object b) {
                      assertTrue(a, "message");
                      assertFalse(b.equals("foo"), "message");
                  }
              }
              """
          )
        );
    }
}
