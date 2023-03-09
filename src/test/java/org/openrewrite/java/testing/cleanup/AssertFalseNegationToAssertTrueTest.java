/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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

class AssertFalseNegationToAssertTrueTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "junit-jupiter-api-5.9.+"))
          .recipe(new AssertFalseNegationToAssertTrue());
    }

    @Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/205")
    @SuppressWarnings({"SimplifiableAssertion"})
    @Test
    void assertTrueNegationToAssertFalse() {
        //language=java
        rewriteRun(
          java(
            """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              
              public class Test {
                  void test() {
                      boolean a = true;
                      assertFalse(!a);
                      assertFalse(!a, "message");
                  }
              }
              """,
            """
              import static org.junit.jupiter.api.Assertions.assertTrue;
              
              public class Test {
                  void test() {
                      boolean a = true;
                      assertTrue(a);
                      assertTrue(a, "message");
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
                      boolean a = true;
                      Assertions.assertFalse(!a);
                      Assertions.assertFalse(!a, "message");
                  }
              }
              """,
            """
              import org.junit.jupiter.api.Assertions;
              
              public class Test {
                  void test() {
                      boolean a = true;
                      Assertions.assertTrue(a);
                      Assertions.assertTrue(a, "message");
                  }
              }
              """
          )
        );
    }
}
